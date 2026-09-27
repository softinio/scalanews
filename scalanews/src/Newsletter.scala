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

import java.util.Date
import cats.effect.*
import cats.syntax.all.*
import fs2.io.file.*

import com.softinio.duck4s.DuckDBConnection
import com.softinio.scalanews.algebra.{
  AiMode,
  Article,
  ArticleSummary,
  GenerateMode
}
import com.softinio.scalanews.db.Database
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}

/** The newsletter pipeline: ingest the feeds and generate the next edition. */
object Newsletter {

  /** Fetches the feeds for the date range and stores new articles, skipping
    * ones already stored.
    */
  private def ingestInto(
      conn: DuckDBConnection,
      startDate: Date,
      endDate: Date
  ): IO[List[String]] =
    for {
      fetched <- Feeds.fetchArticles(startDate, endDate)
      inserted <- fetched.articles.traverse(ArticleRepository.insert(conn, _))
      added = inserted.sum
      _ <- Output.info(
        s"Ingested $added new articles (${fetched.articles.size - added} already stored)"
      )
    } yield fetched.failedFeeds

  def ingestBlogsToDB(
      startDate: Date,
      endDate: Date,
      dbPath: String = Database.defaultPath
  ): IO[ExitCode] =
    Database
      .connect(dbPath, Seq(ArticleSchema))
      .use(ingestInto(_, startDate, endDate))
      .flatMap(reportFailedFeeds)
      .as(ExitCode.Success)

  private def reportFailedFeeds(failedFeeds: List[String]): IO[Unit] =
    Output
      .warn(
        s"${failedFeeds.size} feed(s) couldn't be read: ${failedFeeds.mkString(", ")}"
      )
      .whenA(failedFeeds.nonEmpty)

  /** Writes the next newsletter draft, replacing any existing one. */
  private def writeNextNewsletter(
      articles: List[(Article, Option[ArticleSummary])]
  ): IO[Unit] =
    for {
      exists <- Files[IO].exists(NewsletterPage.nextMarkdownFilePath)
      _ <-
        if (exists) Files[IO].delete(NewsletterPage.nextMarkdownFilePath)
        else IO.unit
      news <- NewsletterPage.generateNews(articles)
      _ <- fs2.Stream
        .emits(List(news))
        .through(fs2.text.utf8.encode)
        .through(
          Files[IO].writeAll(
            NewsletterPage.nextMarkdownFilePath,
            Flags(Flag.CreateNew)
          )
        )
        .compile
        .drain
    } yield ()

  /** Builds the next newsletter for the date range. */
  def generate(
      startDate: Date,
      endDate: Date,
      mode: GenerateMode
  ): IO[ExitCode] =
    mode match {
      case GenerateMode.Direct =>
        for {
          fetched <- Feeds.fetchArticles(startDate, endDate)
          articles = fetched.articles.map(a =>
            a -> Summaries.simpleSummary(a.content)
          )
          _ <- writeNextNewsletter(articles)
          _ <- Output.info(NewsletterPage.runSummary(articles, None))
          _ <- reportFailedFeeds(fetched.failedFeeds)
        } yield ExitCode.Success

      case GenerateMode.Database(dbPath, ai) =>
        Database.connect(dbPath, Seq(ArticleSchema)).use { conn =>
          for {
            failedFeeds <- ingestInto(conn, startDate, endDate)
            rows <- ArticleRepository
              .findAll(conn)
              .filter(row =>
                row.publishedDate.after(startDate) &&
                  row.publishedDate.before(endDate)
              )
              .compile
              .toList
            result <- ai match {
              case AiMode.Enabled(refresh) =>
                for {
                  relevantRows <- Relevance.filterRelevant(conn, rows, refresh)
                  articles <- Summaries.summariseWithClaude(
                    conn,
                    relevantRows,
                    refresh
                  )
                } yield (articles, Some(rows.size - relevantRows.size))
              case AiMode.Disabled =>
                IO.pure(
                  (
                    rows
                      .map(row =>
                        row.toArticle -> Summaries.simpleSummary(row.content)
                      ),
                    None
                  )
                )
            }
            (articles, notRelevant) = result
            _ <- writeNextNewsletter(articles)
            _ <- Output.info(NewsletterPage.runSummary(articles, notRelevant))
            _ <- reportFailedFeeds(failedFeeds)
          } yield ExitCode.Success
        }
    }
}
