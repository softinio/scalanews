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
mill scalanews.compile && mill scalanews.checkFormat && mill scalanews.tests.testCached

# Run a specific test suite
mill scalanews.tests.testOnly "com.softinio.scalanews.BloggersSuite"

# Format code
mill scalanews.reformat             # Format all code
mill scalanews.checkFormat          # Check formatting

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

```bash
# Generate newsletter from RSS feeds for date range
./out/scalanews/nativeImagePath.dest/target/scalanews generate 2024-01-01 2024-01-07

# Create new newsletter draft (saves to next/next.md)
./out/scalanews/nativeImagePath.dest/target/scalanews create

# To start the http4s server
./out/scalanews/nativeImagePath.dest/target/scalanews server

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

Tests use MUnit with Cats Effect integration. Test files are located in `scalanews/tests/src/`.

## Dependencies

Key libraries used:
- Cats Effect for functional effects
- Rome Tools for RSS parsing
- HTTP4s for HTTP clients
- HTTP4s for HTTP server
- PureConfig for configuration
- Decline for CLI parsing
- FS2 for streaming
- Laika for documentation site generation
