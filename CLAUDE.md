# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Scala News is a CLI tool that generates curated Scala newsletters by aggregating RSS feeds from Scala community bloggers and managing events. The application is built with Scala 3, Cats Effect, and uses functional programming patterns throughout.

## Build and Development Commands

```bash
# Compile the project
sbt compile

# Run tests
sbt test

# Run Before creating a pull request
sbt prePR

# Run a specific test suite
sbt "testOnly com.softinio.scalanews.BloggersSuite"

# Create native binary (GraalVM)
sbt nativeImage
# Binary created at: target/scalanews

# Build documentation site
sbt docs/tlSitePreview

# Format code
sbt scalafmtAll
```

## Application Commands

The CLI supports these main commands:

```bash
# Generate newsletter from RSS feeds for date range
./target/scalanews generate 2024-01-01 2024-01-07

# Create new newsletter draft (saves to next/next.md)
./target/scalanews create

# Publish current draft and archive
./target/scalanews publish 2024-01-07

# Generate blogger directory page
./target/scalanews blogger --directory

# Generate events directory page
./target/scalanews event --directory
```

## Architecture

**Core Workflow**:
1. RSS feeds are fetched from bloggers in `config.json`
2. Articles are filtered for Scala-related content
3. Newsletter is generated in markdown format
4. Files are managed through archive/publish cycle

**Key Modules**:
- `Bloggers`: RSS processing and newsletter generation
- `Rome`: RSS feed parsing using Rome Tools
- `FileHandler`: Newsletter publishing and archiving
- `Events`: Community event directory management
- `ConfigLoader`: JSON configuration handling

**Configuration**:
- Blogger RSS feeds: `config.json`
- Events/meetups: `events.json`
- Newsletter template: `next/template.md`

**File Structure**:
- Draft newsletter: `next/next.md`
- Published newsletter: `docs/index.md`
- Archives: `docs/Archive/[year]/`
- Generated directories: `docs/Resources/`

## Testing

Tests use MUnit with Cats Effect integration. Test files mirror the main source structure in `core/src/test/scala/`.

## Dependencies

Key libraries used:
- Cats Effect for functional effects
- Rome Tools for RSS parsing
- HTTP4s for HTTP clients
- PureConfig for configuration
- Decline for CLI parsing
- FS2 for streaming
