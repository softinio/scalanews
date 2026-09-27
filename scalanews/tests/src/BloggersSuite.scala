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

import com.softinio.scalanews.algebra.{Article, ArticleSummary, Blog}
import com.softinio.scalanews.db.Database
import com.softinio.scalanews.db.tables.{StoredRelevance, StoredSummary}
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}
import munit.CatsEffectSuite

import java.net.URI
import java.nio.file.Files as JFiles
import java.text.SimpleDateFormat

import com.softinio.scalanews.TestTags.*

class BloggersSuite extends CatsEffectSuite {
  test("generateDirectory - test the new blogger directory is generated") {
    val blog = Blog(
      "Salar Rahmanian",
      new URI("https://www.softinio.com"),
      new URI("https://www.softinio.com/index.xml")
    )
    val obtained = for {
      result <- Bloggers.generateDirectory(List(blog))
    } yield result.contains(
      "| Salar Rahmanian | <https://www.softinio.com> | [rss feed](https://www.softinio.com/index.xml) |"
    )
    assertIO(obtained, true)
  }

  test(
    "getArticlesForBlogger - test getting articles for a blog list for a blogger for a given date range"
      .tag(IntegrationTest)
  ) {
    val formatter = new SimpleDateFormat("yyyy-MM-dd")
    val blog = Blog(
      "Salar Rahmanian",
      new URI("https://www.softinio.com"),
      new URI("https://www.softinio.com/index.xml")
    )
    val obtained = for {
      result <- Bloggers.getArticlesForBlogger(
        blog,
        formatter.parse("2021-01-01"),
        formatter.parse("2021-12-31")
      )
    } yield {
      result match {
        case Some(articles) => articles.nonEmpty
        case None           => false
      }
    }
    assertIO(obtained, true)
  }

  test(
    "createBlogList - test getting articles for a blog list for all bloggers for a given date range"
      .tag(IntegrationTest)
  ) {
    val formatter = new SimpleDateFormat("yyyy-MM-dd")

    val obtained = for {
      testConfigPath = getClass.getResource("/test-config.json").getPath
      result <- Bloggers.createBlogList(
        formatter.parse("2021-01-01"),
        formatter.parse("2021-12-31"),
        testConfigPath
      )
    } yield {
      result.nonEmpty
    }
    assertIO(obtained, true)
  }

  test(
    "ingestBlogsToDB - returns ExitCode.Success and persists articles to DB"
      .tag(IntegrationTest)
  ) {
    val formatter = new SimpleDateFormat("yyyy-MM-dd")
    val testConfigPath = getClass.getResource("/test-config.json").getPath
    for {
      _ <- cats.effect.IO.blocking(System.setProperty("SCALA_NEWS_CONFIG", testConfigPath))
      dbDir <- cats.effect.IO.blocking(JFiles.createTempDirectory("scalanews-ingest-test"))
      dbPath = dbDir.resolve("test.db").toString
      exitCode <- Bloggers.ingestBlogsToDB(
        formatter.parse("2021-01-01"),
        formatter.parse("2021-12-31"),
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

  private val cardArticle = Article(
    "Match Types",
    "",
    org.http4s.Uri.fromString("https://example.com/match-types").toOption,
    "Author",
    new java.util.Date(0)
  )

  test("generateNews - renders a card's summary when there is one") {
    Bloggers
      .generateNews(List(cardArticle -> ArticleSummary.from("About types.")))
      .map(page =>
        assert(page.contains("""<p class="article-summary">About types.</p>"""))
      )
  }

  test("generateNews - omits the summary line when there is none") {
    Bloggers
      .generateNews(List(cardArticle -> None))
      .map { page =>
        assert(page.contains("Match Types"))
        assert(!page.contains("article-summary"))
      }
  }

  private def articleTitled(title: String) =
    cardArticle.copy(title = title)

  private def cardCount(page: String) =
    "class=\"article-card\"".r.findAllMatchIn(page).size

  test("generateNews - small editions have only cards") {
    val articles = (1 to Bloggers.highlightCount).toList.map(i =>
      articleTitled(f"Article $i%02d") -> ArticleSummary.from("Summary.")
    )
    Bloggers
      .generateNews(articles)
      .map { page =>
        assertEquals(cardCount(page), Bloggers.highlightCount)
        assert(!page.contains("More articles"))
      }
  }

  test("generateNews - extra articles are listed under More articles") {
    val articles = (1 to Bloggers.highlightCount + 3).toList.map(i =>
      articleTitled(f"Article $i%02d") -> ArticleSummary.from("Summary.")
    )
    Bloggers
      .generateNews(articles)
      .map { page =>
        assertEquals(cardCount(page), Bloggers.highlightCount)
        assert(page.contains("### More articles"))
        assertEquals("<li>".r.findAllMatchIn(page).size, 3)
      }
  }

  test("generateNews - articles with a summary get the cards first") {
    val withoutSummary =
      (1 to Bloggers.highlightCount).toList.map(i =>
        articleTitled(f"A no summary $i%02d") -> None
      )
    val withSummary = articleTitled("Z has summary") -> ArticleSummary.from(
      "Summary."
    )
    Bloggers
      .generateNews(withSummary :: withoutSummary)
      .map { page =>
        val cardsPart = page.split("More articles").head
        assert(cardsPart.contains("Z has summary"))
        assert(page.split("More articles").last.contains("A no summary"))
      }
  }

  test("generateNews - escapes HTML in titles, authors and summaries") {
    val article = cardArticle.copy(
      title = "Either[A, B] & <friends>",
      author = "O'Brien"
    )
    Bloggers
      .generateNews(List(article -> ArticleSummary.from("Uses \"quotes\" & <tags>")))
      .map { page =>
        assert(page.contains("Either[A, B] &amp; &lt;friends&gt;"))
        assert(page.contains("O&#39;Brien"))
        assert(page.contains("Uses &quot;quotes&quot; &amp; &lt;tags&gt;"))
        assert(!page.contains("<friends>"))
      }
  }

  test("storedFor - stores Claude's decisions only") {
    import ArticleSummariser.{NoSummaryReason, Summarisation}
    val summary = ArticleSummary.from("About types.").get
    assertEquals(
      Bloggers.storedFor(Summarisation.Summarised(summary), "m"),
      Some(StoredSummary.Summarised(summary, "m"))
    )
    assertEquals(
      Bloggers.storedFor(
        Summarisation.NoSummary(NoSummaryReason.InsufficientContent("link")),
        "m"
      ),
      Some(StoredSummary.NoSummary("link", "m"))
    )
    assertEquals(
      Bloggers.storedFor(Summarisation.NoSummary(NoSummaryReason.NoText), "m"),
      None
    )
    assertEquals(
      Bloggers.storedFor(Summarisation.Failed(new RuntimeException("x")), "m"),
      None
    )
  }

  test("summaryOf - a stored no-summary outcome shows no summary") {
    val summary = ArticleSummary.from("About types.").get
    assertEquals(
      Bloggers.summaryOf(StoredSummary.Summarised(summary, "m")),
      Some(summary)
    )
    assertEquals(
      Bloggers.summaryOf(StoredSummary.NoSummary("link", "m")),
      None
    )
  }

  private def noul(p: Double) =
    com.softinio.verdict4s.algebra.Probability.either(p).toOption.get

  test("relevant - about Scala and not just an announcement") {
    assert(Bloggers.relevant(noul(0.98), noul(0.03)))
  }

  test("relevant - a release announcement is not relevant") {
    assert(!Bloggers.relevant(noul(0.98), noul(0.94)))
  }

  test("relevant - an off-topic article is not relevant") {
    assert(!Bloggers.relevant(noul(0.03), noul(0.02)))
  }

  test("relevant - borderline Scala community news is kept") {
    assert(Bloggers.relevant(noul(0.41), noul(0.02)))
  }

  test("relevant - a post that also mentions a release is kept") {
    assert(Bloggers.relevant(noul(0.97), noul(0.31)))
  }

  test("relevant - thresholds are inclusive for yes") {
    assert(Bloggers.relevant(noul(Bloggers.aboutScalaThreshold), noul(0.0)))
    assert(
      !Bloggers.relevant(noul(1.0), noul(Bloggers.announcementThreshold))
    )
  }

  test("relevanceQuestions - form a valid request with the title and description") {
    val request = Bloggers.relevanceQuestions.request(
      Bloggers.RelevanceContext("sbt 2.0.9", "Bug fixes."),
      com.softinio.verdict4s.algebra.Model.JevLatest
    )
    assert(request.isValid)
  }

  test("relevant - decided from stored probabilities") {
    assert(Bloggers.relevant(StoredRelevance(noul(0.98), noul(0.03), "jev")))
    assert(!Bloggers.relevant(StoredRelevance(noul(0.98), noul(0.94), "jev")))
  }

  private def verdictApiError(status: Int) =
    com.softinio.verdict4s.Verdict4sError.Api(status, "details")

  test("isFatalVerdict - a bad key or no permission stops the run") {
    assert(Bloggers.isFatalVerdict(verdictApiError(401)))
    assert(Bloggers.isFatalVerdict(verdictApiError(403)))
    assert(
      Bloggers.isFatalVerdict(
        new RuntimeException("wrapped", verdictApiError(401))
      )
    )
  }

  test("isFatalVerdict - rate limits and other errors keep going") {
    assert(!Bloggers.isFatalVerdict(verdictApiError(429)))
    assert(!Bloggers.isFatalVerdict(verdictApiError(500)))
    assert(!Bloggers.isFatalVerdict(new RuntimeException("timeout")))
  }

  test("missingTypesafeKeyHint - points to --no-ai when the key is missing") {
    val missing = com.softinio.verdict4s.Verdict4sError.Validation(
      "TYPESAFE_API_KEY",
      "environment variable is not set"
    )
    val hinted = Bloggers.missingTypesafeKeyHint.lift(missing)
    assert(hinted.exists(_.getMessage.contains("--no-ai")))
    assert(hinted.exists(_.getCause eq missing))
  }

  test("missingTypesafeKeyHint - leaves other errors alone") {
    val other = com.softinio.verdict4s.Verdict4sError.Validation(
      "TYPESAFE_BASE_URL",
      "not a URL"
    )
    assert(!Bloggers.missingTypesafeKeyHint.isDefinedAt(other))
    assert(!Bloggers.missingTypesafeKeyHint.isDefinedAt(verdictApiError(401)))
  }
}
