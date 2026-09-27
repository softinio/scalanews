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

  private[scalanews] def simpleSummary(
      content: String
  ): Option[ArticleSummary] =
    ArticleSummary.from(
      content.linesIterator
        .map(_.trim)
        .filterNot(l => l.startsWith("#") || l.isEmpty)
        .mkString(" ")
    )

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
    * storing new ones. Claude is only contacted (and the API key only needed)
    * when some article has no stored outcome.
    */
  private[scalanews] def summariseWithClaude(
      conn: DuckDBConnection,
      rows: List[ArticleRow],
      refresh: Boolean
  ): IO[List[NewsItem]] = {
    val reusable: ArticleRow => Option[StoredSummary] =
      row => if (refresh) None else row.storedSummary
    val toSummarise = rows.filter(reusable(_).isEmpty)

    val fresh: IO[Map[java.util.UUID, Option[ArticleSummary]]] =
      if (toSummarise.isEmpty) IO.pure(Map.empty)
      else
        ConfigLoader.loadAnthropicConfig().flatMap { config =>
          AnthropicClient
            .resource(config)
            .use(client =>
              // Claude calls run concurrently; database writes below run
              // one at a time on the shared connection.
              toSummarise.parTraverseN(summaryConcurrency)(row =>
                ArticleSummariser
                  .summarise(row.toArticle, client)
                  .map(row -> _)
              )
            )
            .flatMap(_.traverse { (row, outcome) =>
              storedFor(outcome, config.model.asString)
                .traverse_(ArticleRepository.saveSummary(conn, row.id, _)) >>
                summaryFor(row.toArticle, outcome).map(row.id -> _)
            })
            .map(_.toMap)
        }

    for {
      summaries <- fresh
      _ <- Output.info(
        s"Summarised ${toSummarise.size} articles with Claude, reused ${rows.size - toSummarise.size} stored"
      )
    } yield rows.map(row =>
      NewsItem(row.toArticle, reusable(row).fold(summaries(row.id))(summaryOf))
    )
  }
}
