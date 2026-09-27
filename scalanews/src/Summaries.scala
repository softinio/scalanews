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

import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.vladsch.flexmark.ast.{
  AutoLink,
  Code,
  FencedCodeBlock,
  HardLineBreak,
  Heading,
  HtmlBlock,
  HtmlCommentBlock,
  HtmlInline,
  HtmlInlineComment,
  Image,
  ImageRef,
  IndentedCodeBlock,
  Reference,
  SoftLineBreak,
  Text,
  ThematicBreak
}
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.{Block, Node}

import scala.jdk.CollectionConverters.*

import com.softinio.duck4s.DuckDBConnection
import com.softinio.scalanews.algebra.{Article, ArticleSummary, NewsItem}
import com.softinio.scalanews.db.tables.{
  ArticleRepository,
  ArticleRow,
  StoredSummary
}

/** Article summaries: plain ones, and Claude's, stored so reruns reuse them. */
object Summaries {
  // Claude calls made at once when summarising; failed calls (e.g. rate
  // limits) fall back to the plain summary.
  private val summaryConcurrency = 4

  /** The start of the article's text, as plain text: summaries are shown inside
    * HTML cards, where Markdown isn't rendered.
    */
  private[scalanews] def simpleSummary(
      content: String
  ): Option[ArticleSummary] =
    ArticleSummary.from(plainText(content))

  /** The text of a Markdown document without its markup: links keep their text,
    * while headings, images, code blocks and HTML are left out.
    */
  private[scalanews] def plainText(markdown: String): String = {
    val text = new StringBuilder
    def visit(node: Node): Unit = node match {
      case _: Heading | _: Image | _: ImageRef | _: FencedCodeBlock |
          _: IndentedCodeBlock | _: HtmlBlock | _: HtmlCommentBlock |
          _: HtmlInline | _: HtmlInlineComment | _: Reference |
          _: ThematicBreak =>
        ()
      case t: Text                             => text ++= t.getChars.unescape()
      case c: Code                             => text ++= c.getText.unescape()
      case a: AutoLink                         => text ++= a.getText.toString
      case _: SoftLineBreak | _: HardLineBreak => text += ' '
      case _                                   =>
        node.getChildren.asScala.foreach(visit)
        if (node.isInstanceOf[Block]) text += ' '
    }
    // Built per call: the native image initialises objects at build time.
    visit(Parser.builder().build().parse(markdown))
    // Emphasis markers CommonMark doesn't accept as emphasis (e.g. around
    // punctuation) are left as text; a run of them is never meant literally.
    text.toString.replaceAll("""\*{2,}""", "")
  }

  /** Picks the summary to show for an AI summarisation outcome, logging why an
    * article didn't get a Claude summary.
    */
  private[scalanews] def summaryFor(
      article: Article,
      outcome: ArticleSummariser.Summarisation
  ): IO[Option[ArticleSummary]] = {
    import ArticleSummariser.{NoSummaryReason, Summarisation}
    outcome match {
      case Summarisation.Summarised(summary) => IO.pure(Some(summary))
      case Summarisation.NoSummary(NoSummaryReason.NoText) =>
        Output
          .info(s"No summary for '${article.title}': article has no text")
          .as(None)
      case Summarisation.NoSummary(
            NoSummaryReason.InsufficientContent(reason)
          ) =>
        Output.info(s"No summary for '${article.title}': $reason").as(None)
      case Summarisation.Failed(error) =>
        Output
          .warn(
            s"Summarisation failed for '${article.title}', using plain summary: ${error.getMessage}"
          )
          .as(simpleSummary(article.content))
    }
  }

  /** The stored form of a summarisation outcome Claude decided; `None` for
    * outcomes that shouldn't be stored (no text is decided locally, and
    * failures should be retried).
    */
  private[scalanews] def storedFor(
      outcome: ArticleSummariser.Summarisation,
      model: String
  ): Option[StoredSummary] = {
    import ArticleSummariser.{NoSummaryReason, Summarisation}
    outcome match {
      case Summarisation.Summarised(summary) =>
        Some(StoredSummary.Summarised(summary, model))
      case Summarisation.NoSummary(NoSummaryReason.InsufficientContent(r)) =>
        Some(StoredSummary.NoSummary(r, model))
      case Summarisation.NoSummary(NoSummaryReason.NoText) |
          Summarisation.Failed(_) =>
        None
    }
  }

  /** The summary to show for a stored outcome. */
  private[scalanews] def summaryOf(
      stored: StoredSummary
  ): Option[ArticleSummary] =
    stored match {
      case StoredSummary.Summarised(summary, _) => Some(summary)
      case StoredSummary.NoSummary(_, _)        => None
    }

  /** Summaries for `rows`, reusing stored outcomes unless `refresh`, and
    * storing new ones. The summariser is only acquired (and the API key only
    * needed) when some article has no stored outcome.
    */
  private[scalanews] def summariseWithClaude(
      conn: DuckDBConnection,
      rows: List[ArticleRow],
      refresh: Boolean,
      summariser: Resource[IO, Summariser]
  ): IO[List[NewsItem]] = {
    val reusable: ArticleRow => Option[StoredSummary] =
      row => if (refresh) None else row.storedSummary
    val toSummarise = rows.filter(reusable(_).isEmpty)

    for {
      summaries <- Stored.runMissing(
        toSummarise,
        summariser,
        summaryConcurrency
      )((s, row) => s.summarise(row.toArticle).tupleRight(s.model)) {
        case (row, (outcome, model)) =>
          storedFor(outcome, model)
            .traverse_(ArticleRepository.saveSummary(conn, row.id, _)) >>
            summaryFor(row.toArticle, outcome)
      }
      _ <- Output.info(
        s"Summarised ${toSummarise.size} articles with Claude, reused ${rows.size - toSummarise.size} stored"
      )
    } yield rows.map(row =>
      NewsItem(row.toArticle, reusable(row).fold(summaries(row.id))(summaryOf))
    )
  }
}
