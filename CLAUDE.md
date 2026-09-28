# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Scala News is a CLI tool that generates curated Scala newsletters by aggregating RSS feeds from Scala community bloggers. The application is built with Scala 3, Cats Effect, and uses functional programming patterns throughout.

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
docs-preview                        # The same, on the dev shell's PATH
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

The native image initialises classes at build time, so never read the environment or other
runtime state in a `val` of an `object` (or eagerly when building an `IO`): the value would be
captured when the binary is built. Read it inside the `IO` (e.g. `IO(sys.env.get(...))`) and make
anything that does so a `def` (as `Services.live` is).

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

# generate, ingest and edition share the default DB path (data/scalanews.duckdb)
# and accept -d/--dbpath to override it
./out/scalanews/nativeImagePath.dest/target/scalanews generate 2024-01-01 2024-01-07 -d data/custom.duckdb

# Create new newsletter draft (saves to next/next.md)
./out/scalanews/nativeImagePath.dest/target/scalanews create

# Generate and publish the next edition in one go (database, jev and Claude): archives the
# current edition under docs/Archive/<year>/ (dated from its heading) and makes the new one
# docs/index.md, dated the end date. Leaves next/next.md alone; publishes nothing when the range
# has no articles or doesn't end after the current edition. The new-edition skill
# (.claude/skills/new-edition) runs this.
./out/scalanews/nativeImagePath.dest/target/scalanews edition 2026-09-01 2026-10-31

# Publish the draft (next/next.md) and archive the current edition. -p dates the new edition
# (yyyyMMdd, default today); the archive date is read from the current edition's heading unless
# given (yyyyMMdd)
./out/scalanews/nativeImagePath.dest/target/scalanews publish -p 20240107

# Generate blogger directory page (docs/Resources/Blog_Directory.md) and an OPML file of all the
# feeds for feed readers (docs/Resources/bloggers.opml, linked from the page) from config.json
./out/scalanews/nativeImagePath.dest/target/scalanews blogger --directory

# Validate config.json's bloggers (http(s) URLs, no duplicates) and fetch their feeds (each needs
# an entry with a link and a date); exits with an error on problems. --base compares with another
# config.json so only new or changed bloggers' feeds are fetched (CI does this on PRs). With
# --directory too, the page is only written when the check passes
./out/scalanews/nativeImagePath.dest/target/scalanews blogger --check --base /tmp/main-config.json

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
3. Relevance: articles pass a cheap keyword filter (`isAboutScala`, "scala"/"sbt") before they're stored, in every mode. With AI, jev (Typesafe, via verdict4s, `jev-latest` by default) is then asked three yes/no questions per article in one request: is it about Scala and its ecosystem, is it just a release announcement, and is it mainly sales or recruitment content. jev's probabilities are stored on the article with the model used, and the keep/drop decision is made from them when read (`aboutScalaThreshold` 0.3, `announcementThreshold` 0.7 and `promotionalThreshold` 0.7 in `Relevance`), so retuning a threshold needs no new jev calls. Articles judged irrelevant are logged with their probabilities and get no summary. A failed check keeps the article and isn't stored, so it's retried; a bad key stops the run
4. Article summaries are written by Claude (Sonnet 5 by default) using structured outputs; articles without enough text get no summary, and failed calls fall back to the built-in `simpleSummary` (the start of the post as plain text: its Markdown is parsed with flexmark and only the text kept, since summaries are shown inside HTML cards). Claude only summarises: it never filters articles out. Claude's outcome (a summary, or "not enough content" with its reason) is stored on the article with the model used, and later runs reuse it instead of calling Claude again (no API key needed if every article has one); failed calls aren't stored, so they're retried. `--refresh-ai` asks jev and Claude again; `--no-ai` skips both (plain summaries); `--no-db` skips the database entirely (plain summaries, the original workflow)
5. `generate`, `ingest` and `edition` default to `data/scalanews.duckdb` (`Database.defaultPath`) and accept `-d/--dbpath` to override
6. There are no schema migrations: the table is created with `CREATE TABLE IF NOT EXISTS` (`ArticleSchema`), so after changing the schema delete the database file and re-ingest. Add versioned migrations once the database holds data worth keeping across schema changes

**Key Modules**:
- `Newsletter`: the pipeline behind `generate` and `ingest` (ingest, relevance, summaries, write the page)
- `Feeds`: fetching RSS feeds, the `isAboutScala` keyword filter, and turning entries into articles (links relative to the blog, e.g. `/posts/x/`, are resolved against its URL)
- `Relevance`: jev relevance checks and their stored verdicts
- `Summaries`: plain and Claude summaries, and their stored outcomes
- `NewsletterPage`: rendering the newsletter page (cards, More articles list) and the run summary
- `BlogDirectory`: the blog directory page
- `BloggerCheck`: `blogger --check`, validating config.json's bloggers and their feeds
- `Services` (`FeedSource`, `RelevanceChecker`, `Summariser`): what the pipeline needs from the outside world. `Services.live` wires in the real feeds, jev and Claude; `NewsletterSuite` runs `generate` end to end with fakes and a temporary database and page, no network
- `Stored.runMissing`: the part the relevance and summary steps share (acquire the service only if some article needs it, call it concurrently, record results one at a time)
- `Rome`: RSS feed parsing using Rome Tools
- `FileHandler`: Newsletter publishing and archiving
- `Edition`: the `edition` command (generate with AI and the database, then publish)
- `ConfigLoader`: JSON configuration handling (also loads the Anthropic config)
- `Database` / `ArticleRepository`: DuckDB persistence layer for articles
- `AnthropicClient`: cats-effect wrapper around Anthropic's official Java SDK (structured outputs)
- `StructuredOutput` / `JsonSchema`: derive a structured-output JSON schema and matching decoder from a case class or sealed trait
- `ArticleSummariser`: typed article summarisation (request `ArticleInput`, reply `Summary | InsufficientContent`)
- `Output` / `UserError`: CLI output. Print through `Output.info` (stdout) for progress, `Output.warn`/`Output.error` (stderr) for problems; never `IO.println` directly. Raise `UserError` for expected, user-fixable failures (missing or rejected API keys): `Main` reports it, a bad config file or an invalid date as one `error:` line with exit code 1, while unexpected errors keep their stack trace. A `generate` run ends with a one-line summary; per-article lines ("Not relevant", "No summary") are printed only for results decided on that run

**CI** (`.github/workflows/`):
- `ci.yml`: tests, and on PRs `blogger --check` against the target branch's config.json; builds the site and publishes it on pushes to main
- `bloggers.yml`: when config.json changes on main, regenerates the Bloggers page and opens a PR (`bot/update-bloggers-page`) with it; needs "Allow GitHub Actions to create and approve pull requests" enabled

**Configuration**:
- Blogger RSS feeds: `config.json`
- Newsletter template: `next/template.md`
- `ANTHROPIC_API_KEY` environment variable: required by `generate` and `edition` for Claude summaries (not needed with `--no-ai`/`--no-db`, or when every article already has a stored summary)
- `TYPESAFE_API_KEY` environment variable: required by `generate` and `edition` for jev relevance checks (not needed with `--no-ai`/`--no-db`, or when every article already has a stored verdict). The verdict4s client is built from the environment (`Verdict4sEnv`): `TYPESAFE_DEFAULT_MODEL` optionally picks the jev model (default `jev-latest`) and `TYPESAFE_BASE_URL` the API endpoint

**File Structure**:
- Draft newsletter: `next/next.md`
- Published newsletter: `docs/index.md`
- Archives: `docs/Archive/[year]/` (`publish` and `edition` file the outgoing edition under its year, dated from its heading)
- Site theme: `scripts/SiteTheme.scala` (Helium settings and colours, shared by
  `LaikaBuild` and `LaikaPreview`) plus `docs/css/scalanews.css`
- Page head: `docs/helium/templates/head.template.html` is Helium 1.3.2's head template with its
  description and canonical tags replaced by `@:scalanewsPageMeta` (defined in `SiteTheme`): per-page
  description, canonical URL, feed link, and Open Graph / Twitter card tags (image
  `docs/img/social-card.png`, 1200x630); edition pages are marked as articles with
  `article:published_time`. `SiteTheme` also renders an edition heading's date as `<time datetime>`, followed by "Share this edition" links
  (Bluesky, Mastodon via toot.kytta.dev, LinkedIn, X, Reddit, Hacker News, email; plain links to each
  platform's compose page with the edition's title and permanent URL, no scripts).
  Re-copy Helium's template when upgrading Laika
- Generated at build time (`scripts/Editions.scala` reads each edition's date, title and article
  links from its Markdown; `scripts/SiteFiles.scala` writes the files): `feed.xml` (Atom feed of all
  editions), `sitemap.xml`, `robots.txt`, the current edition also at its permanent archive URL
  (`Edition.permalink`, where `publish`/`edition` will move it; its `og:url`, also on the home page,
  so each new edition gets a fresh link preview), and a share image per edition
  (`img/editions/scala_news_<date>.png`, drawn by `scripts/ShareImages.scala` in Lato from
  `SCALANEWS_FONTS`, set by the Nix dev shell). The site URL (`https://www.scalanews.net/`) is in
  `SiteFiles`
- Sidebar: `docs/helium/templates/mainNav.template.html` overrides Helium's
  navigation, rendering each archive year as a `<details>` group (no
  JavaScript). `scripts/ArchiveNav.scala` supplies the years, the
  newest-first order and the short date labels at build time, so nothing
  about it is kept in `docs/`
- Generated directories: `docs/Resources/`
- DuckDB database (default): `data/scalanews.duckdb` (gitignored)
- Claude Code skills: `.claude/skills/` (`new-edition` runs the `edition` command)

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
- flexmark for converting feed HTML to Markdown, and Markdown to plain text for plain summaries
