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
import fs2.io.file.Path

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
      range: DateRange,
      feeds: FeedSource
  ): IO[List[String]] =
    for {
      fetched <- feeds.fetch(range)
      inserted <- fetched.articles.traverse(ArticleRepository.insert(conn, _))
      added = inserted.sum
      _ <- Output.info(
        s"Ingested $added new articles (${fetched.articles.size - added} already stored)"
      )
    } yield fetched.failedFeeds

  def ingestBlogsToDB(
      range: DateRange,
      dbPath: String = Database.defaultPath,
      feeds: FeedSource = Services.live.feeds
  ): IO[ExitCode] =
    Database
      .connect(dbPath, Seq(ArticleSchema))
      .use(ingestInto(_, range, feeds))
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
      notRelevant: Option[Int],
      pagePath: Path
  ): IO[Unit] =
    NewsletterPage
      .generateNews(items)
      .flatMap(TextFiles.write(pagePath, _)) >>
      Output.info(NewsletterPage.runSummary(items, notRelevant, pagePath))

  /** Builds the next newsletter for the date range. `services` and `pagePath`
    * default to the real feeds, jev, Claude and `next/next.md`; tests pass
    * fakes and a temporary file.
    */
  def generate(
      range: DateRange,
      mode: GenerateMode,
      services: Services = Services.live,
      pagePath: Path = NewsletterPage.nextMarkdownFilePath
  ): IO[ExitCode] =
    mode match {
      case GenerateMode.Direct =>
        for {
          fetched <- services.feeds.fetch(range)
          items = fetched.articles.map(a =>
            NewsItem(a, Summaries.simpleSummary(a.content))
          )
          _ <- writeNextNewsletter(items, notRelevant = None, pagePath)
          _ <- reportFailedFeeds(fetched.failedFeeds)
        } yield ExitCode.Success

      case GenerateMode.Database(dbPath, ai) =>
        Database.connect(dbPath, Seq(ArticleSchema)).use { conn =>
          for {
            failedFeeds <- ingestInto(conn, range, services.feeds)
            rows <- ArticleRepository
              .findAll(conn)
              .filter(row => range.contains(row.publishedDate))
              .compile
              .toList
            _ <- ai match {
              case AiMode.Enabled(refresh) =>
                for {
                  relevantRows <- Relevance.filterRelevant(
                    conn,
                    rows,
                    refresh,
                    services.relevance
                  )
                  items <- Summaries.summariseWithClaude(
                    conn,
                    relevantRows,
                    refresh,
                    services.summariser
                  )
                  _ <- writeNextNewsletter(
                    items,
                    notRelevant = Some(rows.size - relevantRows.size),
                    pagePath
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
                  notRelevant = None,
                  pagePath
                )
            }
            _ <- reportFailedFeeds(failedFeeds)
          } yield ExitCode.Success
        }
    }
}
