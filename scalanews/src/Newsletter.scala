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
import cats.syntax.all.*

import com.softinio.duck4s.DuckDBConnection
import com.softinio.scalanews.algebra.{
  AiMode,
  DateRange,
  GenerateMode,
  NewsItem
}
import com.softinio.scalanews.db.Database
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}

/** The newsletter pipeline: ingest the feeds and generate the next edition. */
object Newsletter {

  /** Fetches the feeds for the date range and stores new articles, skipping
    * ones already stored. Returns the blogs whose feed couldn't be read.
    */
  private def ingestInto(
      conn: DuckDBConnection,
      range: DateRange
  ): IO[List[String]] =
    for {
      fetched <- Feeds.fetchArticles(range)
      inserted <- fetched.articles.traverse(ArticleRepository.insert(conn, _))
      added = inserted.sum
      _ <- Output.info(
        s"Ingested $added new articles (${fetched.articles.size - added} already stored)"
      )
    } yield fetched.failedFeeds

  def ingestBlogsToDB(
      range: DateRange,
      dbPath: String = Database.defaultPath
  ): IO[ExitCode] =
    Database
      .connect(dbPath, Seq(ArticleSchema))
      .use(ingestInto(_, range))
      .flatMap(reportFailedFeeds)
      .as(ExitCode.Success)

  private def reportFailedFeeds(failedFeeds: List[String]): IO[Unit] =
    Output
      .warn(
        s"${failedFeeds.size} feed(s) couldn't be read: ${failedFeeds.mkString(", ")}"
      )
      .whenA(failedFeeds.nonEmpty)

  /** Writes the next newsletter draft and reports what went into it. */
  private def writeNextNewsletter(
      items: List[NewsItem],
      notRelevant: Option[Int]
  ): IO[Unit] =
    NewsletterPage
      .generateNews(items)
      .flatMap(TextFiles.write(NewsletterPage.nextMarkdownFilePath, _)) >>
      Output.info(NewsletterPage.runSummary(items, notRelevant))

  /** Builds the next newsletter for the date range. */
  def generate(range: DateRange, mode: GenerateMode): IO[ExitCode] =
    mode match {
      case GenerateMode.Direct =>
        for {
          fetched <- Feeds.fetchArticles(range)
          items = fetched.articles.map(a =>
            NewsItem(a, Summaries.simpleSummary(a.content))
          )
          _ <- writeNextNewsletter(items, notRelevant = None)
          _ <- reportFailedFeeds(fetched.failedFeeds)
        } yield ExitCode.Success

      case GenerateMode.Database(dbPath, ai) =>
        Database.connect(dbPath, Seq(ArticleSchema)).use { conn =>
          for {
            failedFeeds <- ingestInto(conn, range)
            rows <- ArticleRepository
              .findAll(conn)
              .filter(row => range.contains(row.publishedDate))
              .compile
              .toList
            _ <- ai match {
              case AiMode.Enabled(refresh) =>
                for {
                  relevantRows <- Relevance.filterRelevant(conn, rows, refresh)
                  items <- Summaries.summariseWithClaude(
                    conn,
                    relevantRows,
                    refresh
                  )
                  _ <- writeNextNewsletter(
                    items,
                    notRelevant = Some(rows.size - relevantRows.size)
                  )
                } yield ()
              case AiMode.Disabled =>
                writeNextNewsletter(
                  rows.map(row =>
                    NewsItem(
                      row.toArticle,
                      Summaries.simpleSummary(row.content)
                    )
                  ),
                  notRelevant = None
                )
            }
            _ <- reportFailedFeeds(failedFeeds)
          } yield ExitCode.Success
        }
    }
}
