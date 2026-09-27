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

package com.softinio.scalanews.db

import cats.effect.*
import cats.syntax.all.*
import munit.CatsEffectSuite
import org.http4s.Uri

import java.nio.file.Files as JFiles
import java.util.Date

import com.softinio.scalanews.algebra.{Article, ArticleSummary}
import com.softinio.scalanews.db.tables.{
  ArticleRepository,
  ArticleSchema,
  StoredSummary
}

class ArticleRepositorySuite extends CatsEffectSuite {

  private def tempDbPath: IO[String] = IO.blocking {
    val dir = JFiles.createTempDirectory("scalanews-test-db")
    dir.resolve("test.db").toString
  }

  private val testArticle = Article(
    title = "Getting Started with Scala 3",
    content = "Scala 3 introduces many improvements over Scala 2.",
    url = Uri.fromString("https://example.com/scala3-intro").toOption,
    author = "Test Author",
    publishedDate = new Date(1_700_000_000_000L)
  )

  test("insert - returns 1 row affected") {
    for {
      path <- tempDbPath
      rows <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.insert(conn, testArticle)
      }
    } yield assertEquals(rows, 1)
  }

  test("findAll - returns inserted article") {
    for {
      path <- tempDbPath
      articles <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.insert(conn, testArticle) >>
          ArticleRepository.findAll(conn).compile.toList
      }
    } yield {
      assertEquals(articles.length, 1)
      assertEquals(articles.head.title, testArticle.title)
      assertEquals(articles.head.author, testArticle.author)
    }
  }

  test("findAll - returns all inserted articles in desc published_date order") {
    val older = testArticle.copy(
      title = "Older Article",
      url = Uri.fromString("https://example.com/older").toOption,
      publishedDate = new Date(1_600_000_000_000L)
    )
    val newer = testArticle.copy(
      title = "Newer Article",
      url = Uri.fromString("https://example.com/newer").toOption,
      publishedDate = new Date(1_800_000_000_000L)
    )
    for {
      path <- tempDbPath
      articles <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.insert(conn, older) >>
          ArticleRepository.insert(conn, newer) >>
          ArticleRepository.findAll(conn).compile.toList
      }
    } yield {
      assertEquals(articles.length, 2)
      assertEquals(articles.head.title, "Newer Article")
      assertEquals(articles.last.title, "Older Article")
    }
  }

  test("findById - returns Some when article exists") {
    for {
      path <- tempDbPath
      result <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.insert(conn, testArticle) >>
          ArticleRepository.findAll(conn).compile.toList.flatMap { articles =>
            val id = articles.head.id
            ArticleRepository.findById(conn, id)
          }
      }
    } yield {
      assert(result.isDefined)
      assertEquals(result.get.title, testArticle.title)
    }
  }

  test("findById - returns None for unknown UUID") {
    for {
      path <- tempDbPath
      result <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.findById(conn, java.util.UUID.randomUUID())
      }
    } yield assertEquals(result, None)
  }

  test("Database.connect - creates table idempotently (connect twice)") {
    for {
      path <- tempDbPath
      _ <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.insert(conn, testArticle)
      }
      // Second connect should not fail (CREATE TABLE IF NOT EXISTS)
      count <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.findAll(conn).compile.toList.map(_.length)
      }
    } yield assertEquals(count, 1)
  }

  test("insert - skips an article whose URL is already stored") {
    for {
      path <- tempDbPath
      result <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        for {
          first <- ArticleRepository.insert(conn, testArticle)
          second <- ArticleRepository.insert(
            conn,
            testArticle.copy(title = "Same URL, new title")
          )
          all <- ArticleRepository.findAll(conn).compile.toList
        } yield (first, second, all)
      }
    } yield {
      val (first, second, all) = result
      assertEquals((first, second), (1, 0))
      assertEquals(all.map(_.title), List(testArticle.title))
    }
  }

  test("insert - articles without a URL are all stored") {
    val noUrl = testArticle.copy(url = None)
    for {
      path <- tempDbPath
      count <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.insert(conn, noUrl) >>
          ArticleRepository.insert(conn, noUrl.copy(title = "Another")) >>
          ArticleRepository.findAll(conn).compile.toList.map(_.length)
      }
    } yield assertEquals(count, 2)
  }

  private def storeAndReload(
      stored: StoredSummary*
  ): IO[(Option[StoredSummary], Boolean)] =
    for {
      path <- tempDbPath
      row <- Database.connect(path, Seq(ArticleSchema)).use { conn =>
        for {
          _ <- ArticleRepository.insert(conn, testArticle)
          id <- ArticleRepository.findAll(conn).compile.lastOrError.map(_.id)
          _ <- stored.toList.traverse_(ArticleRepository.saveSummary(conn, id, _))
          row <- ArticleRepository.findById(conn, id)
        } yield row
      }
    } yield (
      row.flatMap(_.storedSummary),
      row.exists(_.summarisedAt.isDefined)
    )

  test("new articles have no stored summary") {
    storeAndReload().map(result => assertEquals(result, (None, false)))
  }

  test("saveSummary - stores a summary") {
    val stored = StoredSummary.Summarised(
      ArticleSummary.from("About Scala 3.").get,
      "claude-sonnet-5"
    )
    storeAndReload(stored).map(result =>
      assertEquals(result, (Some(stored), true))
    )
  }

  test("saveSummary - stores a no-summary outcome with its reason") {
    val stored = StoredSummary.NoSummary("Only a link.", "claude-sonnet-5")
    storeAndReload(stored).map(result =>
      assertEquals(result, (Some(stored), true))
    )
  }

  test("saveSummary - replaces an earlier outcome") {
    val first = StoredSummary.NoSummary("Only a link.", "claude-sonnet-5")
    val second = StoredSummary.Summarised(
      ArticleSummary.from("Now with content.").get,
      "claude-sonnet-5"
    )
    storeAndReload(first, second).map(result =>
      assertEquals(result, (Some(second), true))
    )
  }
}
