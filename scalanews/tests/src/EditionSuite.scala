/*
 * Copyright 2026 Salar Rahmanian
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

import cats.effect.{IO, Resource}
import com.softinio.scalanews.algebra.{Article, ArticleSummary, DateRange}
import com.softinio.scalanews.db.tables.StoredRelevance
import com.softinio.verdict4s.algebra.Probability
import fs2.io.file.{Files, Path}
import munit.CatsEffectSuite

import java.time.LocalDate

class EditionSuite extends CatsEffectSuite {
  private val range = DateRange.parse("2026-09-01", "2026-10-31").toOption.get

  private def p(d: Double) = Probability.either(d).toOption.get

  private def article(title: String) = Article(
    title,
    s"All about $title.",
    org.http4s.Uri.fromString(s"https://example.com/${title.replace(' ', '-')}").toOption,
    "Author",
    new java.util.Date(range.start.getTime + 24 * 60 * 60 * 1000L)
  )

  private def services(articles: List[Article]) = Services(
    feeds = _ => IO.pure(Feeds.FetchedArticles(articles, Nil)),
    relevance =
      Resource.pure(_ => IO.pure(StoredRelevance(p(0.97), p(0.05), p(0.02), "fake-jev"))),
    summariser = Resource.pure(new Summariser {
      val model = "fake-claude"
      def summarise(a: Article) =
        IO.pure(
          ArticleSummariser.Summarisation.Summarised(
            ArticleSummary.from(s"Summary of ${a.title}.").get
          )
        )
    })
  )

  /** A site with a current edition dated `heading`, and a temporary DB. */
  private def site(heading: String)(test: (Path, Path, String) => IO[Unit]) =
    Files[IO].tempDirectory.use { dir =>
      val index = dir / "index.md"
      fs2.Stream
        .emit(s"\n$heading\n\nThe previous edition.\n")
        .through(Files[IO].writeUtf8(index))
        .compile
        .drain >> test(dir, index, (dir / "test.db").toString)
    }

  private def run(dir: Path, index: Path, db: String, articles: List[Article]) =
    Edition.run(range, db, refresh = false, services(articles), index, dir / "Archive")

  test("run - archives the current edition and publishes the new one") {
    site("# Scala News - August 31, 2026") { (dir, index, db) =>
      for {
        _ <- run(dir, index, db, List(article("Scala tips")))
        page <- Files[IO].readUtf8(index).compile.string
        archived <- Files[IO].readUtf8(
          dir / "Archive" / "2026" / "scala_news_2026-08-31.md"
        ).compile.string
      } yield {
        assert(page.contains("# Scala News - October 31, 2026"), page)
        assert(page.contains("Summary of Scala tips."), page)
        assert(archived.contains("The previous edition."), archived)
      }
    }
  }

  test("run - publishes nothing when there are no articles") {
    site("# Scala News - August 31, 2026") { (dir, index, db) =>
      for {
        result <- run(dir, index, db, Nil).attempt
        page <- Files[IO].readUtf8(index).compile.string
        archive <- Files[IO].exists(dir / "Archive")
      } yield {
        assert(result.left.exists(_.isInstanceOf[UserError]), result)
        assert(page.contains("The previous edition."))
        assert(!archive)
      }
    }
  }

  test("run - the new edition must end after the current one") {
    site("# Scala News - October 31, 2026") { (dir, index, db) =>
      for {
        result <- run(dir, index, db, List(article("Scala tips"))).attempt
        page <- Files[IO].readUtf8(index).compile.string
      } yield {
        assert(result.left.exists(_.isInstanceOf[UserError]), result)
        assert(page.contains("The previous edition."))
      }
    }
  }

  test("permalink - built from the CNAME next to the index") {
    Files[IO].tempDirectory.use { dir =>
      for {
        none <- Edition.permalink(dir / "index.md", LocalDate.of(2026, 10, 31))
        _ <- fs2.Stream
          .emit("www.scalanews.net\n")
          .through(Files[IO].writeUtf8(dir / "CNAME"))
          .compile
          .drain
        url <- Edition.permalink(dir / "index.md", LocalDate.of(2026, 10, 31))
      } yield {
        assertEquals(none, None)
        assertEquals(
          url,
          Some("https://www.scalanews.net/Archive/2026/scala_news_2026-10-31.html")
        )
      }
    }
  }
}
