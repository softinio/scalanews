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
import com.softinio.scalanews.algebra.{Article, ArticleSummary, Blog, DateRange}
import com.softinio.scalanews.db.Database
import com.softinio.scalanews.db.tables.{StoredRelevance, StoredSummary}
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}
import munit.CatsEffectSuite

import java.net.URI
import java.nio.file.Files as JFiles
import java.text.SimpleDateFormat

import com.softinio.scalanews.TestTags.*

class NewsletterSuite extends CatsEffectSuite {
  test(
    "ingestBlogsToDB - returns ExitCode.Success and persists articles to DB"
      .tag(IntegrationTest)
  ) {
    val testConfigPath = getClass.getResource("/test-config.json").getPath
    for {
      _ <- cats.effect.IO.blocking(System.setProperty("SCALA_NEWS_CONFIG", testConfigPath))
      dbDir <- cats.effect.IO.blocking(JFiles.createTempDirectory("scalanews-ingest-test"))
      dbPath = dbDir.resolve("test.db").toString
      exitCode <- Newsletter.ingestBlogsToDB(
        DateRange.parse("2021-01-01", "2021-12-31").toOption.get,
        dbPath
      ).guarantee(cats.effect.IO.blocking(System.clearProperty("SCALA_NEWS_CONFIG")).void)
      count <- Database.connect(dbPath, Seq(ArticleSchema)).use { conn =>
        ArticleRepository.findAll(conn).compile.toList.map(_.length)
      }
    } yield {
      assertEquals(exitCode, cats.effect.ExitCode.Success)
      assert(count > 0, s"Expected articles in DB but found $count")
    }
  }
}
