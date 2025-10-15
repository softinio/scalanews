![GitHub Pages](https://github.com/softinio/scalanews/actions/workflows/ci.yml/badge.svg)

# Scala News

For more information have a look at our [about us](docs/Resources/About.md)

## 🚀 Quick Start

This project uses Mill as its build tool and Nix for development environment management.

### Using Nix (Recommended)

```bash
# Enter the development shell
nix develop

# Build and run
mill compile
mill test
mill run --help

# See all available commands
menu
```

### Build Commands

```bash
# Compile the project
mill compile

# Run tests
mill test              # All tests
mill testUnit          # Fast unit tests only
mill testIntegration   # Integration tests (slow, real RSS)
mill testService       # Service tests

# Create native binary (GraalVM)
mill nativeImage
# Binary created at: target/scalanews

# Build documentation site
mill tlSitePreview     # Preview at http://localhost:4242
mill tlSite            # Build static site

# Format code
mill fmt               # Format all code
mill checkFormat       # Check formatting

# Pre-PR checks
mill prePR
```

### Application Commands

```bash
# Generate newsletter from RSS feeds for date range
./target/scalanews generate 2024-01-01 2024-01-07

# Create new newsletter draft (saves to next/next.md)
./target/scalanews create

# Start the HTTP server
./target/scalanews server

# Publish current draft and archive
./target/scalanews publish 2024-01-07

# Generate blogger directory page
./target/scalanews blogger --directory

# Generate events directory page
./target/scalanews event --directory
```

## How to contribute Links

Add yourself with details of your rss path to our directory (see next section below) of bloggers and your articles will automatically be included in our next edition of Scala News.


## Do you have a Scala related Blog? Want to add it in our [Blog Directory](docs/Resources/Blog_Directory.md)?

Create a PR adding your blog to the `bloggers` array in our [config.json file](config.json)

## Do you organize a scala meetup or conference? Want to add it to our upcoming meetup and events directory?

Create a PR adding your meetup or conference to the `meetups` or `conferences` array in our [events.json file](events.json)

## Build Tool Migration

This project has been migrated from SBT to Mill. For migration details and SBT command equivalents, see [MILL_MIGRATION.md](MILL_MIGRATION.md).

## Created By

[Salar Rahmanian](https://www.softinio.com)
