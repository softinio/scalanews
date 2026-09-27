/*
 * Copyright 2024 Salar Rahmanian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softinio.scalanews
import com.softinio.scalanews.algebra.{
  AiMode,
  Article,
  ArticleSummary,
  Blog,
  DateRange,
  GenerateMode
}
import cats.effect.{IO, Ref, Resource}
import com.softinio.verdict4s.algebra.Probability
import com.softinio.scalanews.db.Database
import com.softinio.scalanews.db.tables.{StoredRelevance, StoredSummary}
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}
import munit.CatsEffectSuite

import java.net.URI
import java.nio.file.Files as JFiles
import java.text.SimpleDateFormat

import com.softinio.scalanews.TestTags.*

class NewsletterSuite extends CatsEffectSuite {
  test(
    "ingestBlogsToDB - returns ExitCode.Success and persists articles to DB"
      .tag(IntegrationTest)
  ) {
    val testConfigPath = getClass.getResource("/test-config.json").getPath
    for {
      _ <- cats.effect.IO.blocking(System.setProperty("SCALA_NEWS_CONFIG", testConfigPath))
      dbDir <- cats.effect.IO.blocking(JFiles.createTempDirectory("scalanews-ingest-test"))
      dbPath = dbDir.resolve("test.db").toString
      exitCode <- Newsletter.ingestBlogsToDB(
        DateRange.parse("2021-01-01", "2021-12-31").toOption.get,
        dbPath
      ).guarantee(cats.effect.IO.blocking(System.clearProperty("SCALA_NEWS_CONFIG")).void)
      count <- Database.connect(dbPath, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.findAll(conn).compile.toList.map(_.length)
      }
    } yield {
      assertEquals(exitCode, cats.effect.ExitCode.Success)
      assert(count > 0, s"Expected articles in DB but found $count")
    }
  }

  // -- The pipeline with fake feeds, jev and Claude ------------------------

  private val week = DateRange.parse("2026-09-01", "2026-09-07").toOption.get

  private def fakeArticle(title: String) = Article(
    title,
    s"All about $title.",
    org.http4s.Uri
      .fromString(s"https://example.com/${title.replace(' ', '-')}")
      .toOption,
    "Author",
    new java.util.Date(week.start.getTime + 24 * 60 * 60 * 1000L)
  )

  private val feedArticles =
    List("Scala tips", "sbt 9 released", "Rust tips").map(fakeArticle)

  private def p(d: Double) = Probability.either(d).toOption.get

  /** Calls made to the fake services, so tests can check what was reused. */
  private final case class Calls(relevance: Int = 0, summaries: Int = 0)

  private def fakeServices(
      calls: Ref[IO, Calls],
      failRelevanceFor: Set[String] = Set.empty
  ): Services =
    Services(
      feeds = _ =>
        IO.pure(Feeds.FetchedArticles(feedArticles, List("Broken Blog"))),
      relevance = Resource.pure(row =>
        calls.update(c => c.copy(relevance = c.relevance + 1)) >> {
          if (failRelevanceFor(row.title))
            IO.raiseError(new RuntimeException("jev timed out"))
          else if (row.title.contains("Rust"))
            IO.pure(StoredRelevance(p(0.02), p(0.02), "fake-jev"))
          else if (row.title.contains("released"))
            IO.pure(StoredRelevance(p(0.98), p(0.95), "fake-jev"))
          else IO.pure(StoredRelevance(p(0.97), p(0.05), "fake-jev"))
        }
      ),
      summariser = Resource.pure(new Summariser {
        val model = "fake-claude"
        def summarise(article: Article) =
          calls.update(c => c.copy(summaries = c.summaries + 1)).as(
            ArticleSummariser.Summarisation.Summarised(
              ArticleSummary.from(s"Summary of ${article.title}.").get
            )
          )
      })
    )

  /** Services that fail the test if the pipeline touches them. */
  private val unusedServices: Services = Services(
    feeds = _ =>
      IO.pure(Feeds.FetchedArticles(feedArticles, Nil)),
    relevance = Resource.eval(IO.raiseError(new AssertionError("jev used"))),
    summariser =
      Resource.eval(IO.raiseError(new AssertionError("Claude used")))
  )

  private val tempFiles: IO[(String, fs2.io.file.Path)] = IO.blocking {
    val dir = JFiles.createTempDirectory("scalanews-pipeline-test")
    (
      dir.resolve("test.db").toString,
      fs2.io.file.Path.fromNioPath(dir.resolve("next.md"))
    )
  }

  private def readPage(path: fs2.io.file.Path): IO[String] =
    IO.blocking(new String(JFiles.readAllBytes(path.toNioPath), "UTF-8"))

  private def ai(dbPath: String, refresh: Boolean = false) =
    GenerateMode.Database(dbPath, AiMode.Enabled(refresh))

  test("generate - drops irrelevant articles and summarises the rest") {
    for {
      (dbPath, page) <- tempFiles
      calls <- Ref.of[IO, Calls](Calls())
      _ <- Newsletter.generate(week, ai(dbPath), fakeServices(calls), page)
      text <- readPage(page)
      made <- calls.get
      rows <- Database.connect(dbPath, Seq(ArticleSchema)).use(conn =>
        ArticleRepository.findAll(conn).compile.toList
      )
    } yield {
      assert(text.contains("Scala tips"))
      assert(text.contains("Summary of Scala tips."))
      assert(!text.contains("sbt 9 released"))
      assert(!text.contains("Rust tips"))
      assertEquals(made, Calls(relevance = 3, summaries = 1))
      assertEquals(rows.count(_.storedRelevance.isDefined), 3)
      assertEquals(rows.count(_.storedSummary.isDefined), 1)
    }
  }

  test("generate - a rerun reuses stored results and writes the same page") {
    for {
      (dbPath, page) <- tempFiles
      calls <- Ref.of[IO, Calls](Calls())
      _ <- Newsletter.generate(week, ai(dbPath), fakeServices(calls), page)
      first <- readPage(page)
      _ <- calls.set(Calls())
      _ <- Newsletter.generate(week, ai(dbPath), fakeServices(calls), page)
      second <- readPage(page)
      made <- calls.get
    } yield {
      assertEquals(made, Calls())
      assertEquals(second, first)
    }
  }

  test("generate - refresh asks the services again") {
    for {
      (dbPath, page) <- tempFiles
      calls <- Ref.of[IO, Calls](Calls())
      _ <- Newsletter.generate(week, ai(dbPath), fakeServices(calls), page)
      _ <- calls.set(Calls())
      _ <- Newsletter.generate(
        week,
        ai(dbPath, refresh = true),
        fakeServices(calls),
        page
      )
      made <- calls.get
    } yield assertEquals(made, Calls(relevance = 3, summaries = 1))
  }

  test("generate - without AI no service is used and all articles are kept") {
    for {
      (dbPath, page) <- tempFiles
      _ <- Newsletter.generate(
        week,
        GenerateMode.Database(dbPath, AiMode.Disabled),
        unusedServices,
        page
      )
      text <- readPage(page)
    } yield feedArticles.foreach(a => assert(text.contains(a.title)))
  }

  test("generate - a failed relevance check keeps the article and retries it") {
    for {
      (dbPath, page) <- tempFiles
      calls <- Ref.of[IO, Calls](Calls())
      services = fakeServices(calls, failRelevanceFor = Set("Rust tips"))
      _ <- Newsletter.generate(week, ai(dbPath), services, page)
      text <- readPage(page)
      _ <- calls.set(Calls())
      _ <- Newsletter.generate(week, ai(dbPath), services, page)
      made <- calls.get
    } yield {
      assert(text.contains("Rust tips"), "kept when its check failed")
      assertEquals(made.relevance, 1, "only the failed check is retried")
    }
  }

  test("generate - without the database the page comes straight from the feeds") {
    for {
      (_, page) <- tempFiles
      _ <- Newsletter.generate(week, GenerateMode.Direct, unusedServices, page)
      text <- readPage(page)
    } yield feedArticles.foreach(a => assert(text.contains(a.title)))
  }
}
