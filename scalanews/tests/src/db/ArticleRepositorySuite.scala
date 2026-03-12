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
import munit.CatsEffectSuite
import org.http4s.Uri

import java.nio.file.Files as JFiles
import java.util.Date

import com.softinio.scalanews.algebra.Article
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}

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
      publishedDate = new Date(1_600_000_000_000L)
    )
    val newer = testArticle.copy(
      title = "Newer Article",
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
}
