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

# Run before creating a pull request
mill scalanews.compile && mill scalanews.checkFormat && mill checkDependencyOrder && mill scalanews.tests.testCached

# Run a specific test suite
mill scalanews.tests.testOnly "com.softinio.scalanews.BloggersSuite"

# Format code
mill scalanews.reformat             # Format all code
mill scalanews.checkFormat          # Check formatting
mill checkDependencyOrder           # Check versions and dependencies in build.mill are alphabetical

# Additional Mill commands
mill clean                          # Clean build artifacts
mill show scalanews.mvnDeps         # Show dependencies
mill mill.bsp.BSP/install           # Generate Bloop config for IDE
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
record `self-check` and a real `dbgenerate --ai` run with the tracing agent and merge the
`com.anthropic`, `com.fasterxml.jackson` and `kotlin` entries.

```bash
# Generate newsletter from RSS feeds for date range
./out/scalanews/nativeImagePath.dest/target/scalanews generate 2024-01-01 2024-01-07

# Ingest articles from RSS feeds into the DuckDB database for a date range
./out/scalanews/nativeImagePath.dest/target/scalanews ingest 2024-01-01 2024-01-07

# Generate newsletter from articles stored in the DuckDB database
# Add -a/--ai to summarise articles with Claude (requires ANTHROPIC_API_KEY)
./out/scalanews/nativeImagePath.dest/target/scalanews dbgenerate 2024-01-01 2024-01-07
./out/scalanews/nativeImagePath.dest/target/scalanews dbgenerate 2024-01-01 2024-01-07 --ai

# Both ingest and dbgenerate share the same default DB path (data/scalanews.duckdb)
# and accept -d/--dbpath to override it
./out/scalanews/nativeImagePath.dest/target/scalanews ingest 2024-01-01 2024-01-07 -d data/custom.duckdb

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

**Database-backed Workflow** (optional, via `ingest` + `dbgenerate`):
1. `ingest` fetches RSS feeds and persists articles into a DuckDB database
2. `dbgenerate` reads articles from the database for a date range and generates the newsletter
3. With `--ai`, article summaries are written by Claude (Sonnet 5 by default) using structured outputs; articles without enough text get no summary, and failed calls fall back to the built-in `simpleSummary`. `--ai` only summarises: it never filters articles out
4. Both commands default to `data/scalanews.duckdb` (`Database.defaultPath`) and accept `-d/--dbpath` to override

**Key Modules**:
- `Bloggers`: RSS processing and newsletter generation (including DB-backed variants)
- `Rome`: RSS feed parsing using Rome Tools
- `FileHandler`: Newsletter publishing and archiving
- `Events`: Community event directory management
- `ConfigLoader`: JSON configuration handling (also loads the Anthropic config)
- `Database` / `ArticleRepository`: DuckDB persistence layer for articles
- `AnthropicClient`: cats-effect wrapper around Anthropic's official Java SDK (structured outputs)
- `StructuredOutput` / `JsonSchema`: derive a structured-output JSON schema and matching decoder from a case class or sealed trait
- `ArticleSummariser`: typed article summarisation (request `ArticleInput`, reply `Summary | InsufficientContent`)

**Configuration**:
- Blogger RSS feeds: `config.json`
- Events/meetups: `events.json`
- Newsletter template: `next/template.md`
- `ANTHROPIC_API_KEY` environment variable: required for `dbgenerate --ai`

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
- DuckDB (via duck4s) for article storage
