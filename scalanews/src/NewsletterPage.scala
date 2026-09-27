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

import cats.effect.IO
import fs2.io.file.Path

import com.softinio.scalanews.algebra.{Article, NewsItem}

/** The newsletter page: article cards, the More articles list, and the run
  * summary.
  */
object NewsletterPage {
  private[scalanews] val nextMarkdownFilePath =
    Path("next/next.md")

  // Articles shown as cards; the rest are listed compactly under "More articles".
  private[scalanews] val highlightCount = 18

  private def escapeHtml(text: String): String =
    text.flatMap {
      case '&'  => "&amp;"
      case '<'  => "&lt;"
      case '>'  => "&gt;"
      case '"'  => "&quot;"
      case '\'' => "&#39;"
      case c    => c.toString
    }

  private[scalanews] def generateNews(
      items: List[NewsItem]
  ): IO[String] = {
    IO.blocking {
      val header =
        """|# Scala News
           |
           |A curated list of Scala related news from the community.
           |
           |## Articles""".stripMargin

      def link(article: Article): String = {
        val url = escapeHtml(article.url.map(_.toString).getOrElse("#"))
        s"""<a href="$url">${escapeHtml(article.title)}</a>"""
      }

      def card(item: NewsItem): String = {
        val NewsItem(article, summary) = item
        val summaryLine = summary.fold("")(s =>
          s"""\n  <p class="article-summary">${escapeHtml(s.value)}</p>"""
        )
        s"""|<div class="article-card">
            |  <h3>${link(article)}</h3>
            |  <span class="article-author">${escapeHtml(
             article.author
           )}</span>$summaryLine
            |</div>""".stripMargin
      }

      def listItem(article: Article): String =
        s"""  <li>${link(article)} <span class="article-author">${escapeHtml(
            article.author
          )}</span></li>"""

      // Articles with a summary get the cards first; order within each part is by title.
      val (summarised, unsummarised) =
        items.sortBy(_.article.title).partition(_.summary.isDefined)
      val (highlights, rest) =
        (summarised ++ unsummarised).splitAt(highlightCount)

      val cards =
        s"""|<div class="article-cards">
            |${highlights.map(card).mkString("\n")}
            |</div>""".stripMargin

      val moreArticles =
        if (rest.isEmpty) ""
        else
          s"""|
              |
              |### More articles
              |
              |<ul class="more-articles">
              |${rest
               .map(_.article)
               .sortBy(_.title)
               .map(listItem)
               .mkString("\n")}
              |</ul>""".stripMargin

      s"""|$header
          |
          |$cards$moreArticles
          |""".stripMargin
    }
  }

  /** The closing line of a `generate` run. */
  private[scalanews] def runSummary(
      items: List[NewsItem],
      notRelevant: Option[Int]
  ): String = {
    val cards = items.size.min(highlightCount)
    (List(
      s"Wrote $nextMarkdownFilePath: ${items.size} articles ($cards as cards, ${items.size - cards} listed), ${items.count(_.summary.isDefined)} with a summary"
    ) ++ notRelevant.map(n => s"$n not relevant")).mkString("; ")
  }
}
