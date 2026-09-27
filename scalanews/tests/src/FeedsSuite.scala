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
import com.rometools.rome.feed.synd.{
  SyndCategoryImpl,
  SyndContentImpl,
  SyndEntry,
  SyndEntryImpl
}
import scala.jdk.CollectionConverters.*
import com.softinio.scalanews.db.Database
import com.softinio.scalanews.db.tables.{StoredRelevance, StoredSummary}
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}
import munit.CatsEffectSuite

import java.net.URI
import java.nio.file.Files as JFiles
import java.text.SimpleDateFormat

import com.softinio.scalanews.TestTags.*

class FeedsSuite extends CatsEffectSuite {
  private val range2021 = DateRange.parse("2021-01-01", "2021-12-31").toOption.get

  private val blog = Blog(
    "Salar Rahmanian",
    new URI("https://www.softinio.com"),
    new URI("https://www.softinio.com/index.xml")
  )

  test(
    "fetchBlog - articles for a blogger in a given date range"
      .tag(IntegrationTest)
  ) {
    Feeds
      .fetchBlog(blog, range2021)
      .map(result => assert(result.exists(_.nonEmpty)))
  }

  test(
    "fetchArticles - articles for all bloggers in a given date range"
      .tag(IntegrationTest)
  ) {
    val testConfigPath = getClass.getResource("/test-config.json").getPath
    Feeds
      .fetchArticles(range2021, testConfigPath)
      .map { fetched =>
        assert(fetched.articles.nonEmpty)
        assertEquals(fetched.failedFeeds, Nil)
      }
  }

  private val inRange = new java.util.Date(
    DateRange.parse("2021-06-01", "2021-06-02").toOption.get.start.getTime
  )

  private def entry(
      title: String = "Scala 3 tips",
      link: String = "https://example.com/post",
      published: java.util.Date = inRange,
      description: String = "",
      categories: List[String] = Nil,
      author: String = ""
  ): SyndEntry = {
    val e = new SyndEntryImpl()
    e.setTitle(title)
    e.setLink(link)
    e.setPublishedDate(published)
    val d = new SyndContentImpl()
    d.setValue(description)
    e.setDescription(d)
    e.setCategories(categories.map { name =>
      val c = new SyndCategoryImpl()
      c.setName(name)
      c: com.rometools.rome.feed.synd.SyndCategory
    }.asJava)
    e.setAuthor(author)
    e
  }

  test("toArticle - an in-range entry mentioning Scala becomes an article") {
    val article = Feeds.toArticle(blog, entry(), range2021)
    assertEquals(article.map(_.title), Some("Scala 3 tips"))
    assertEquals(article.map(_.author), Some("Salar Rahmanian"))
  }

  test("toArticle - Scala may appear in the title, description or categories") {
    assert(Feeds.toArticle(blog, entry(title = "Build tips", description = "Using sbt"), range2021).isDefined)
    assert(Feeds.toArticle(blog, entry(title = "Build tips", categories = List("Scala")), range2021).isDefined)
    assert(Feeds.toArticle(blog, entry(title = "Rust tips"), range2021).isEmpty)
  }

  test("toArticle - entries outside the range or missing fields are skipped") {
    val outside = DateRange.parse("2022-01-01", "2022-01-02").toOption.get.start
    assert(Feeds.toArticle(blog, entry(published = outside), range2021).isEmpty)
    assert(Feeds.toArticle(blog, entry(link = null), range2021).isEmpty)
    assert(Feeds.toArticle(blog, entry(title = null), range2021).isEmpty)
    assert(Feeds.toArticle(blog, entry(published = null), range2021).isEmpty)
  }

  test("toArticle - skipped blogs are excluded and the entry's author is used") {
    assert(
      Feeds
        .toArticle(blog, entry(link = "https://petr-zapletal.medium.com/x"), range2021)
        .isEmpty
    )
    assertEquals(
      Feeds.toArticle(blog, entry(author = "Jane"), range2021).map(_.author),
      Some("Jane")
    )
    assertEquals(
      Feeds.toArticle(blog, entry(author = "unknown"), range2021).map(_.author),
      Some("Salar Rahmanian")
    )
  }
}
