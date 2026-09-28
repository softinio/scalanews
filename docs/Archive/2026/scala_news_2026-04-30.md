# Scala News - April 30, 2026

A curated list of Scala related news from the community.

## Articles

<div class="article-cards">
<div class="article-card">
  <h3><a href="http://cloudmark.github.io/Cats-Actors-Native-And-JS/">Cats-Actors 2.1.0 Goes Cross-Platform</a></h3>
  <span class="article-author">Mark Galea</span>
  <p class="article-summary">Introduces Cats-Actors 2.1.0&#39;s cross-platform support (JVM, JS, Native) via sbt crossProject setup, and demonstrates a token-ring actor benchmark comparing JVM vs Scala Native performance.</p>
</div>
<div class="article-card">
  <h3><a href="https://www.scala-lang.org/blog/2026/03/31/sbt-security-advisory.html">Fixing a Command Injection Vulnerability in sbt</a></h3>
  <span class="article-author">Scala Lang</span>
  <p class="article-summary">Details CVE-2026-32948, a Windows-only command injection flaw in sbt&#39;s source-dependency VCS resolution via cmd.exe; fixed in sbt 1.12.8 and 2.0.0-RC10.</p>
</div>
<div class="article-card">
  <h3><a href="https://www.scala-lang.org/blog/2026/03/11/scoverage.html">Hardening Scoverage Support in Scala 3</a></h3>
  <span class="article-author">Scala Lang</span>
  <p class="article-summary">Describes Scala Center&#39;s Sovereign Tech Fund-backed effort to harden Scoverage for Scala 3, enabling coverage on the compiler test suite, uncovering 97 failing tests, and requiring coverage checks in</p>
</div>
<div class="article-card">
  <h3><a href="https://blog.pierre-ricadat.com/introducing-purelogic/">Introducing PureLogic: direct-style, pure domain logic for Scala</a></h3>
  <span class="article-author">Pierre Ricadat</span>
  <p class="article-summary">Introduces PureLogic, a zero-dependency Scala 3 library offering direct-style Reader, Writer, State, and Abort effects via context functions, claiming 7-40x better performance than monadic</p>
</div>
<div class="article-card">
  <h3><a href="https://www.scala-lang.org/blog/2026/04/14/last-mile-towards-sbt2.html">Last mile towards sbt 2</a></h3>
  <span class="article-author">Scala Lang</span>
  <p class="article-summary">Status update on sbt 2 development under Sovereign Tech Fund/Scala Center: new features (Scala 3 builds, incremental test, Bazel-compatible caching, sbtn), RC2 progress, and plugin ecosystem</p>
</div>
<div class="article-card">
  <h3><a href="https://www.scala-lang.org/blog/2026/03/02/sbt2-compat.html">Migrating sbt plugins to sbt 2 with sbt2-compat plugin</a></h3>
  <span class="article-author">Scala Lang</span>
  <p class="article-summary">Introduces the sbt2-compat plugin, which helps sbt plugin authors cross-publish for sbt 1 and sbt 2 using the PluginCompat pattern to abstract breaking API changes like file type representations.</p>
</div>
<div class="article-card">
  <h3><a href="https://rockthejvm.com/articles/never-call-apis-inside-database-transactions">Never Call APIs Inside Database Transactions</a></h3>
  <span class="article-author">Rock The JVM Blog</span>
  <p class="article-summary">Explains why external API calls inside DB transactions cause inconsistent state, and presents fixes using Transactional Outbox, Result Tables, and Saga Compensation patterns with a full Scala</p>
</div>
<div class="article-card">
  <h3><a href="https://www.scala-lang.org/blog/2026/03/23/porting-the-optimizer.html">Porting the Scala 2 optimizer to Scala 3</a></h3>
  <span class="article-author">Scala Lang</span>
  <p class="article-summary">Explains porting Scala 2&#39;s optimizer to Scala 3, boosting performance 10-30% by transforming high-level functional code (e.g., map) into efficient low-level loops, available since 3.8.3-RC3.</p>
</div>
<div class="article-card">
  <h3><a href="https://blog.pierre-ricadat.com/protobuf-goes-scala-first/">Protobuf Goes Scala-First</a></h3>
  <span class="article-author">Pierre Ricadat</span>
  <p class="article-summary">Author recounts efforts to improve developer productivity when using Protobuf within a large Scala codebase, sharing lessons from that journey.</p>
</div>
<div class="article-card">
  <h3><a href="https://www.scala-lang.org/news/3.8.3/">Scala 3.8.3 is now available!</a></h3>
  <span class="article-author">Scala Lang</span>
  <p class="article-summary">Announces Scala 3.8.3, highlighting local coverage exclusion blocks (`$COVERAGE-OFF/ON$`) and an experimental capability-safe &quot;safe mode&quot; restricting unsafe casts, reflection, and APIs for</p>
</div>
<div class="article-card">
  <h3><a href="https://www.scala-lang.org/blog/2026/04/30/scala-days-2026-cfp.html">Scala Days 2026: Call for proposals open</a></h3>
  <span class="article-author">Scala Lang</span>
  <p class="article-summary">Scala Days 2026 CFP is open until May 31, 2026. Seeking Talks (30 min), new Interactive labs (2 hrs, hands-on), and post-conference Workshops (Oct 14-15, separate ticket).</p>
</div>
<div class="article-card">
  <h3><a href="https://softwaremill.com/scalar-2026-celebrating-functional-programming-fiesta/">Scalar 2026: Celebrating Functional Programming Fiesta</a></h3>
  <span class="article-author">SoftwareMill</span>
  <p class="article-summary">Recap of Scalar 2026 in Warsaw: Odersky&#39;s keynote on trusting AI agents via types, Scala 3.9 LTS roadmap, Open Community Build insights, GPU computing with Cyfra, Scala Native/C interop, Protobuf</p>
</div>
<div class="article-card">
  <h3><a href="https://alexn.org/blog/2026/03/05/tapir-server-with-cats-effect-and-pekko-http-snippet/?pk_campaign=rss">Tapir Server with Cats-Effect and Pekko HTTP (snippet)</a></h3>
  <span class="article-author">Alexandru Nedelcu</span>
  <p class="article-summary">A code snippet showing how to build a Tapir server backed by Pekko HTTP, with business logic written in Cats-Effect IO bridged via Dispatcher.unsafeToFuture.</p>
</div>
<div class="article-card">
  <h3><a href="https://typelevel.org/blog/gsoc-2026.html">Typelevel Summer of Code 2026</a></h3>
  <span class="article-author">Arman Bilge</span>
  <p class="article-summary">Typelevel announces participation as a GSoC 2026 Mentoring Organization, with onboarding deadline March 16. Includes project ideas, application info, and a recap of 2025 contributors&#39; work on FS2, ML</p>
</div>
<div class="article-card">
  <h3><a href="https://eed3si9n.com/shutting-down-the-goldmine/">shutting down the goldmine</a></h3>
  <span class="article-author">Eugene Yokota</span>
  <p class="article-summary">Follow-up to a prior post on sbt/sbt being listed on crypto bounty platform Gittensor, which drove many AI-assisted contributions; author announces shutting the whole arrangement down.</p>
</div>
<div class="article-card">
  <h3><a href="https://eed3si9n.com/tree-sitter-scala-0.25.0">tree-sitter-scala 0.24.1 and 0.25.0</a></h3>
  <span class="article-author">Eugene Yokota</span>
  <p class="article-summary">Announces tree-sitter-scala 0.24.1 and 0.25.0 releases, a fast, incremental Scala parser generated via Tree-sitter CLI, with Rust bindings published to crates.io.</p>
</div>
<div class="article-card">
  <h3><a href="https://eed3si9n.com/tree-sitter-scala-0.26.0">tree-sitter-scala 0.25.1 and 0.26.0</a></h3>
  <span class="article-author">Eugene Yokota</span>
  <p class="article-summary">Announces tree-sitter-scala releases 0.25.1 and 0.26.0, updating the Scala grammar to match tree-sitter-cli versions 0.25.x/0.26.x, with Rust bindings published to crates.io.</p>
</div>
</div>
