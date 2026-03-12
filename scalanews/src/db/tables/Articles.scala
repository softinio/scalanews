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

package com.softinio.scalanews.db.tables

import com.softinio.duck4s.DuckDBConnection
import com.softinio.duck4s.algebra.DuckDBResultSet
import com.softinio.duck4s.effect.DuckDBIO
import com.softinio.scalanews.algebra.Article
import com.softinio.scalanews.db.{Repository, RowsAffected, TableSchema}
import cats.effect.IO
import fs2.Stream
import java.sql.Timestamp

case class ArticleRow(
    id: java.util.UUID,
    title: String,
    content: String,
    url: Option[String],
    author: String,
    publishedDate: java.util.Date,
    createdAt: java.sql.Timestamp
)

object ArticleSchema extends TableSchema:
  val createDdl =
    """|CREATE TABLE IF NOT EXISTS articles (
       |  id             UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
       |  title          VARCHAR   NOT NULL,
       |  content        VARCHAR   NOT NULL,
       |  url            VARCHAR,
       |  author         VARCHAR   NOT NULL,
       |  published_date TIMESTAMP NOT NULL,
       |  created_at     TIMESTAMP NOT NULL DEFAULT now()
       |)""".stripMargin

object ArticleRepository extends Repository[Article, ArticleRow]:

  private val insertSql =
    "INSERT INTO articles (title, content, url, author, published_date) VALUES (?, ?, ?, ?, ?)"

  private val selectAllSql =
    "SELECT id, title, content, url, author, published_date, created_at FROM articles ORDER BY published_date DESC"

  def insert(conn: DuckDBConnection, article: Article): IO[RowsAffected] =
    for
      stmt <- IO.fromEither(
        conn
          .prepareStatement(insertSql)
          .left
          .map(e => new RuntimeException(e.toString))
      )
      _ <- IO.blocking(stmt.setString(1, article.title)).void
      _ <- IO.blocking(stmt.setString(2, article.content)).void
      _ <- article.url match
        case Some(uri) => IO.blocking(stmt.setString(3, uri.renderString)).void
        case None => IO.blocking(stmt.setNull(3, java.sql.Types.VARCHAR)).void
      _ <- IO.blocking(stmt.setString(4, article.author)).void
      _ <- IO
        .blocking(
          stmt.setTimestamp(5, new Timestamp(article.publishedDate.getTime))
        )
        .void
      rows <- IO
        .blocking(stmt.executeUpdate())
        .flatMap(r =>
          IO.fromEither(r.left.map(e => new RuntimeException(e.toString)))
        )
      _ <- IO(stmt.close())
    yield rows

  def findAll(conn: DuckDBConnection): Stream[IO, ArticleRow] =
    DuckDBIO.stream(conn, selectAllSql)(rowMapper)

  def findById(
      conn: DuckDBConnection,
      id: java.util.UUID
  ): IO[Option[ArticleRow]] =
    IO.blocking {
      conn.prepareStatement(
        "SELECT id, title, content, url, author, published_date, created_at FROM articles WHERE id = ?"
      ) match
        case Right(stmt) =>
          stmt.setObject(1, id)
          val result = stmt.executeQuery() match
            case Right(rs) =>
              val row = if rs.next() then Some(rowMapper(rs)) else None
              rs.close()
              row
            case Left(err) => throw new RuntimeException(err.toString)
          stmt.close()
          result
        case Left(err) => throw new RuntimeException(err.toString)
    }

  private def rowMapper(rs: DuckDBResultSet): ArticleRow =
    ArticleRow(
      id = rs.getObject("id").asInstanceOf[java.util.UUID],
      title = rs.getString("title"),
      content = rs.getString("content"),
      url = Option(rs.getString("url")),
      author = rs.getString("author"),
      publishedDate = rs.getTimestamp("published_date"),
      createdAt = rs.getTimestamp("created_at")
    )

end ArticleRepository
