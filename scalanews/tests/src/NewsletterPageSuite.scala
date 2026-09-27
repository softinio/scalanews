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
import com.softinio.scalanews.algebra.{Article, ArticleSummary, Blog, NewsItem}
import com.softinio.scalanews.db.Database
import com.softinio.scalanews.db.tables.{StoredRelevance, StoredSummary}
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}
import munit.CatsEffectSuite

import java.net.URI
import java.nio.file.Files as JFiles
import java.text.SimpleDateFormat

import com.softinio.scalanews.TestTags.*

class NewsletterPageSuite extends CatsEffectSuite {
  private val cardArticle = Article(
    "Match Types",
    "",
    org.http4s.Uri.fromString("https://example.com/match-types").toOption,
    "Author",
    new java.util.Date(0)
  )

  test("generateNews - renders a card's summary when there is one") {
    NewsletterPage
      .generateNews(List(NewsItem(cardArticle, ArticleSummary.from("About types."))))
      .map(page =>
        assert(page.contains("""<p class="article-summary">About types.</p>"""))
      )
  }

  test("generateNews - omits the summary line when there is none") {
    NewsletterPage
      .generateNews(List(NewsItem(cardArticle, None)))
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
    val articles = (1 to NewsletterPage.highlightCount).toList.map(i =>
      NewsItem(articleTitled(f"Article $i%02d"), ArticleSummary.from("Summary."))
    )
    NewsletterPage
      .generateNews(articles)
      .map { page =>
        assertEquals(cardCount(page), NewsletterPage.highlightCount)
        assert(!page.contains("More articles"))
      }
  }

  test("generateNews - extra articles are listed under More articles") {
    val articles = (1 to NewsletterPage.highlightCount + 3).toList.map(i =>
      NewsItem(articleTitled(f"Article $i%02d"), ArticleSummary.from("Summary."))
    )
    NewsletterPage
      .generateNews(articles)
      .map { page =>
        assertEquals(cardCount(page), NewsletterPage.highlightCount)
        assert(page.contains("### More articles"))
        assertEquals("<li>".r.findAllMatchIn(page).size, 3)
      }
  }

  test("generateNews - articles with a summary get the cards first") {
    val withoutSummary =
      (1 to NewsletterPage.highlightCount).toList.map(i =>
        NewsItem(articleTitled(f"A no summary $i%02d"), None)
      )
    val withSummary =
      NewsItem(articleTitled("Z has summary"), ArticleSummary.from("Summary."))
    NewsletterPage
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
    NewsletterPage
      .generateNews(List(NewsItem(article, ArticleSummary.from("Uses \"quotes\" & <tags>"))))
      .map { page =>
        assert(page.contains("Either[A, B] &amp; &lt;friends&gt;"))
        assert(page.contains("O&#39;Brien"))
        assert(page.contains("Uses &quot;quotes&quot; &amp; &lt;tags&gt;"))
        assert(!page.contains("<friends>"))
      }
  }

  test("runSummary - counts cards, listed articles and summaries") {
    val articles = (1 to NewsletterPage.highlightCount + 2).toList.map(i =>
      NewsItem(
        articleTitled(f"Article $i%02d"),
        if (i <= 5) ArticleSummary.from("Summary.") else None
      )
    )
    assertEquals(
      NewsletterPage.runSummary(articles, Some(3)),
      s"Wrote next/next.md: ${NewsletterPage.highlightCount + 2} articles (${NewsletterPage.highlightCount} as cards, 2 listed), 5 with a summary; 3 not relevant"
    )
  }

  test("runSummary - leaves out relevance without AI") {
    assert(
      !NewsletterPage
        .runSummary(List(NewsItem(cardArticle, None)), None)
        .contains("not relevant")
    )
  }
}
