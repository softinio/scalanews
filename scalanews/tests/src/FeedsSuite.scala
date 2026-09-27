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
import com.softinio.scalanews.algebra.{Article, ArticleSummary, Blog}
import com.softinio.scalanews.db.Database
import com.softinio.scalanews.db.tables.{StoredRelevance, StoredSummary}
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}
import munit.CatsEffectSuite

import java.net.URI
import java.nio.file.Files as JFiles
import java.text.SimpleDateFormat

import com.softinio.scalanews.TestTags.*

class FeedsSuite extends CatsEffectSuite {
  test(
    "getArticlesForBlogger - test getting articles for a blog list for a blogger for a given date range"
      .tag(IntegrationTest)
  ) {
    val formatter = new SimpleDateFormat("yyyy-MM-dd")
    val blog = Blog(
      "Salar Rahmanian",
      new URI("https://www.softinio.com"),
      new URI("https://www.softinio.com/index.xml")
    )
    val obtained = for {
      result <- Feeds.getArticlesForBlogger(
        blog,
        formatter.parse("2021-01-01"),
        formatter.parse("2021-12-31")
      )
    } yield {
      result match {
        case Some(articles) => articles.nonEmpty
        case None           => false
      }
    }
    assertIO(obtained, true)
  }

  test(
    "createBlogList - test getting articles for a blog list for all bloggers for a given date range"
      .tag(IntegrationTest)
  ) {
    val formatter = new SimpleDateFormat("yyyy-MM-dd")

    val obtained = for {
      testConfigPath = getClass.getResource("/test-config.json").getPath
      result <- Feeds.createBlogList(
        formatter.parse("2021-01-01"),
        formatter.parse("2021-12-31"),
        testConfigPath
      )
    } yield {
      result.nonEmpty
    }
    assertIO(obtained, true)
  }
}
