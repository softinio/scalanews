# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Scala News is a CLI tool that generates curated Scala newsletters by aggregating RSS feeds from Scala community bloggers and managing events. The application is built with Scala 3, Cats Effect, and uses functional programming patterns throughout.

## Build Tool

This project uses **Mill** as its build tool (migrated from SBT). Mill provides faster builds, simpler configuration, and better caching. The build is managed through Nix for reproducible development environments.

## Development Environment

```bash
# Enter the Nix development shell (recommended)
nix develop

# Or with direnv
direnv allow
```

## Build and Development Commands

### Mill Commands (Current Build Tool)

```bash
# Compile the project
mill scalanews.compile

# Run tests
mill scalanews.tests.testCached     # All tests (cached)
mill scalanews.tests.testLocal      # Tests without forking
mill scalanews.tests.testOnly       # Run specific test class

# Run before creating a pull request (pre-pr is on the dev shell's PATH)
pre-pr
# which runs:
mill scalanews.compile + scalanews.checkFormat + checkDependencyOrder + scalanews.tests.testForked

# Run a specific test suite
mill scalanews.tests.testOnly "com.softinio.scalanews.FeedsSuite"

# Format code
mill scalanews.reformat             # Format all code
mill scalanews.checkFormat          # Check formatting
mill checkDependencyOrder           # Check versions and dependencies in build.mill are alphabetical

# Additional Mill commands
mill clean                          # Clean build artifacts
mill show scalanews.mvnDeps         # Show dependencies
bsp-install                         # Set up Mill's BSP connection for your IDE (runs mill mill.bsp.BSP/install)
mill mill.idea                      # Generate IntelliJ config

# Development
mill scalanews.console              # Start Scala REPL
mill -w scalanews.compile           # Watch for changes and recompile
mill scalanews.run <args>           # Run the application

# Native Image
mill scalanews.nativeImage          # Build GraalVM native image executable

# Documentation Site
mill docs.build                     # Build documentation site
mill docs.preview                   # Build and serve documentation at http://localhost:4242
```

**Alternative**: The documentation can also be built/previewed directly with scala-cli:

```bash
# Build documentation directly
scala-cli run scripts/LaikaBuild.scala

# Build and preview documentation directly
scala-cli run scripts/LaikaPreview.scala  # Serves at http://localhost:4242
```

The documentation site is built using [Laika](https://typelevel.org/Laika/) with the Helium theme:
- Source files: `docs/`
- Build output: `site/target/docs/site/`
- Preview output: `site/target/docs/preview/` (used by preview server)

## Application Commands

The CLI supports these main commands:

**Note**: After building the native image with `mill scalanews.nativeImage`, the executable is located at:
`./out/scalanews/nativeImagePath.dest/target/scalanews`

Native-image metadata that libraries don't ship themselves (Rome, DuckDB JNI, and the
Anthropic Java SDK's Jackson/Kotlin serialisation) lives in
`scalanews/native-image/reachability-metadata.json`. If a native run fails where the JVM run
works, record what's missing with the tracing agent
(`java -agentlib:native-image-agent=config-output-dir=<dir> -cp <runClasspath> com.softinio.scalanews.Main <cmd>`)
and merge the relevant entries.

After upgrading the Anthropic Java SDK, build the native image and run
`scalanews self-check`: it round-trips a Claude request and reply through the SDK offline (no
API key), which fails if the SDK needs new metadata. If it fails,
record `self-check` and a real `generate` run (with Claude summaries) with the tracing agent and merge the
`com.anthropic`, `com.fasterxml.jackson` and `kotlin` entries.

```bash
# Generate the next newsletter (saves to next/next.md). By default this ingests the feeds
# into the DuckDB database, drops articles jev judges irrelevant and summarises the rest with
# Claude (requires TYPESAFE_API_KEY and ANTHROPIC_API_KEY unless every article already has
# stored results)
./out/scalanews/nativeImagePath.dest/target/scalanews generate 2024-01-01 2024-01-07   # both dates inclusive
# -r/--refresh-ai: ask jev and Claude again for articles that already have stored results
./out/scalanews/nativeImagePath.dest/target/scalanews generate 2024-01-01 2024-01-07 --refresh-ai
# --no-ai: keep the database, but no jev or Claude (keyword filter, plain summaries)
./out/scalanews/nativeImagePath.dest/target/scalanews generate 2024-01-01 2024-01-07 --no-ai
# --no-db: read the feeds directly, without the database (plain summaries)
./out/scalanews/nativeImagePath.dest/target/scalanews generate 2024-01-01 2024-01-07 --no-db

# Ingest articles into the database without generating a newsletter (e.g. a backfill)
./out/scalanews/nativeImagePath.dest/target/scalanews ingest 2024-01-01 2024-01-07

# generate and ingest share the default DB path (data/scalanews.duckdb)
# and accept -d/--dbpath to override it
./out/scalanews/nativeImagePath.dest/target/scalanews generate 2024-01-01 2024-01-07 -d data/custom.duckdb

# Create new newsletter draft (saves to next/next.md)
./out/scalanews/nativeImagePath.dest/target/scalanews create

# Publish current draft and archive
./out/scalanews/nativeImagePath.dest/target/scalanews publish 2024-01-07

# Generate blogger directory page
./out/scalanews/nativeImagePath.dest/target/scalanews blogger --directory

# Generate events directory page
./out/scalanews/nativeImagePath.dest/target/scalanews event --directory

# Alternative: Run with mill directly (JVM, slower startup)
mill scalanews.run generate 2024-01-01 2024-01-07
```

## Architecture

**Core Workflow**:
1. RSS feeds are fetched from bloggers in `config.json`
2. Articles are filtered for Scala-related content
3. Newsletter is generated in markdown format
4. Files are managed through archive/publish cycle

**Database-backed Workflow** (the default for `generate`):
1. `generate` fetches the RSS feeds and stores the articles in a DuckDB database; an article whose URL is already stored is skipped, so overlapping date ranges are safe (`ingest` does just this step)
2. It then reads the articles for the date range back from the database and writes the newsletter
3. Relevance: articles pass a cheap keyword filter (`isAboutScala`, "scala"/"sbt") before they're stored, in every mode. With AI, jev (Typesafe, via verdict4s, `jev-latest` by default) is then asked two yes/no questions per article in one request: is it about Scala and its ecosystem, and is it just a release announcement. jev's probabilities are stored on the article with the model used, and the keep/drop decision is made from them when read (`aboutScalaThreshold` 0.3, `announcementThreshold` 0.7 in `Relevance`), so retuning a threshold needs no new jev calls. Articles judged irrelevant are logged with their probabilities and get no summary. A failed check keeps the article and isn't stored, so it's retried; a bad key stops the run
4. Article summaries are written by Claude (Sonnet 5 by default) using structured outputs; articles without enough text get no summary, and failed calls fall back to the built-in `simpleSummary`. Claude only summarises: it never filters articles out. Claude's outcome (a summary, or "not enough content" with its reason) is stored on the article with the model used, and later runs reuse it instead of calling Claude again (no API key needed if every article has one); failed calls aren't stored, so they're retried. `--refresh-ai` asks jev and Claude again; `--no-ai` skips both (plain summaries); `--no-db` skips the database entirely (plain summaries, the original workflow)
5. `generate` and `ingest` default to `data/scalanews.duckdb` (`Database.defaultPath`) and accept `-d/--dbpath` to override
6. There are no schema migrations: the table is created with `CREATE TABLE IF NOT EXISTS` (`ArticleSchema`), so after changing the schema delete the database file and re-ingest. Add versioned migrations once the database holds data worth keeping across schema changes

**Key Modules**:
- `Newsletter`: the pipeline behind `generate` and `ingest` (ingest, relevance, summaries, write the page)
- `Feeds`: fetching RSS feeds, the `isAboutScala` keyword filter, and turning entries into articles
- `Relevance`: jev relevance checks and their stored verdicts
- `Summaries`: plain and Claude summaries, and their stored outcomes
- `NewsletterPage`: rendering the newsletter page (cards, More articles list) and the run summary
- `BlogDirectory`: the blog directory page
- `Rome`: RSS feed parsing using Rome Tools
- `FileHandler`: Newsletter publishing and archiving
- `Events`: Community event directory management
- `ConfigLoader`: JSON configuration handling (also loads the Anthropic config)
- `Database` / `ArticleRepository`: DuckDB persistence layer for articles
- `AnthropicClient`: cats-effect wrapper around Anthropic's official Java SDK (structured outputs)
- `StructuredOutput` / `JsonSchema`: derive a structured-output JSON schema and matching decoder from a case class or sealed trait
- `ArticleSummariser`: typed article summarisation (request `ArticleInput`, reply `Summary | InsufficientContent`)
- `Output` / `UserError`: CLI output. Print through `Output.info` (stdout) for progress, `Output.warn`/`Output.error` (stderr) for problems; never `IO.println` directly. Raise `UserError` for expected, user-fixable failures (missing or rejected API keys): `Main` reports it, a bad config file or an invalid date as one `error:` line with exit code 1, while unexpected errors keep their stack trace. A `generate` run ends with a one-line summary; per-article lines ("Not relevant", "No summary") are printed only for results decided on that run

**Configuration**:
- Blogger RSS feeds: `config.json`
- Events/meetups: `events.json`
- Newsletter template: `next/template.md`
- `ANTHROPIC_API_KEY` environment variable: required by `generate` for Claude summaries (not needed with `--no-ai`/`--no-db`, or when every article already has a stored summary)
- `TYPESAFE_API_KEY` environment variable: required by `generate` for jev relevance checks (not needed with `--no-ai`/`--no-db`, or when every article already has a stored verdict). The verdict4s client is built from the environment (`Verdict4sEnv`): `TYPESAFE_DEFAULT_MODEL` optionally picks the jev model (default `jev-latest`) and `TYPESAFE_BASE_URL` the API endpoint

**File Structure**:
- Draft newsletter: `next/next.md`
- Published newsletter: `docs/index.md`
- Archives: `docs/Archive/[year]/`
- Generated directories: `docs/Resources/`
- DuckDB database (default): `data/scalanews.duckdb`

## Testing

Tests use MUnit with Cats Effect integration. Test files are located in `scalanews/tests/src/`.

## Dependencies

Key libraries used:
- Cats Effect for functional effects
- Rome Tools for RSS parsing
- HTTP4s for HTTP clients
- PureConfig for configuration
- Decline for CLI parsing
- FS2 for streaming
- Laika for documentation site generation
- Anthropic Java SDK (`anthropic-java`) for Claude summaries
- verdict4s for jev (Typesafe) relevance checks
- DuckDB (via duck4s) for article storage
