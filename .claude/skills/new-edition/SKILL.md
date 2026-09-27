---
name: new-edition
description: Generate and publish the next Scala News edition for a date range with the scalanews CLI (database, jev relevance checks and Claude summaries), archiving the current edition. Use when asked to create, generate or publish a new edition or newsletter for a period.
---

# New Scala News edition

The `scalanews edition START END` command does the whole cycle. Don't
re-implement any of it by hand; run the command and report what it did. It:

1. ingests the feeds for the range into the DuckDB database
   (`data/scalanews.duckdb`);
2. drops articles jev judges irrelevant and summarises the rest with Claude,
   reusing results already stored;
3. archives the current edition (`docs/index.md`) to
   `docs/Archive/<year>/scala_news_<date>.md`, dated from its heading;
4. makes the new edition the home page, headed `# Scala News - <END date>`.

It leaves the draft in `next/next.md` alone, and publishes nothing (changing no
files) when the range has no articles or doesn't end after the current
edition.

## Steps

1. **Get the dates.** START and END are `yyyy-MM-dd`, both inclusive. If the
   user gave a period in words ("September and October"), turn it into dates
   and confirm them. The current edition's date is in the first heading of
   `docs/index.md`; the new range normally starts the day after it, and END
   must be after it.

2. **Check the keys.** Both must be set (check without printing them):
   `ANTHROPIC_API_KEY` (Claude) and `TYPESAFE_API_KEY` (jev). If one is
   missing, stop and ask the user to set it.

3. **Build the CLI.** From the repo root:

   ```bash
   nix develop --command mill scalanews.nativeImage
   ```

   Mill only rebuilds when the sources changed, so this is quick when the
   binary is current. If another Mill process holds the lock (e.g. the user's
   `docs-preview`), ask the user to stop it rather than killing it.

4. **Run it:**

   ```bash
   ./out/scalanews/nativeImagePath.dest/target/scalanews edition START END
   ```

   Add `--refresh-ai` only if the user asks to re-check stored results.
   Summaries and relevance checks cost API calls; a normal run only pays for
   articles not already in the database.

5. **Report** from the output: how many articles were ingested, which were
   judged not relevant (with their probabilities), which got no summary and
   why, which feeds failed, and where the previous edition was archived. Call
   out anything that looks wrong, e.g. a significant post (a major release)
   dropped as "just an announcement", or a page that isn't a blog post (an
   "about me" page) kept. Fixes are editorial: edit the edition's Markdown by
   hand, and only if the user asks.

6. **Check the site builds:**

   ```bash
   nix develop --command scala-cli run --server=false scripts/LaikaBuild.scala
   ```

7. **Stop there.** Don't commit or push: the user reviews every change first.
   Summarise what changed (the new `docs/index.md`, the archived file) and
   wait.

## If it fails

- `error: ...` lines are user-fixable (a missing key, an invalid date, no
  articles, a range not after the current edition): report the message and
  what to change.
- A stack trace is unexpected: show it and stop; don't retry with other
  commands or edit files to work around it.
