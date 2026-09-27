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

class BlogDirectorySuite extends CatsEffectSuite {
  test("generateDirectory - test the new blogger directory is generated") {
    val blog = Blog(
      "Salar Rahmanian",
      new URI("https://www.softinio.com"),
      new URI("https://www.softinio.com/index.xml")
    )
    val obtained = for {
      result <- BlogDirectory.generateDirectory(List(blog))
    } yield result.contains(
      "| Salar Rahmanian | <https://www.softinio.com> | [rss feed](https://www.softinio.com/index.xml) |"
    )
    assertIO(obtained, true)
  }

  test("generateDirectory - starts with the heading and has no stray whitespace") {
    val blog = Blog(
      "Salar Rahmanian",
      new URI("https://www.softinio.com"),
      new URI("https://www.softinio.com/index.xml")
    )
    BlogDirectory.generateDirectory(List(blog)).map { page =>
      assert(page.startsWith("# Bloggers\n"), page)
      assert(page.endsWith("for details.\n"), page)
      assert(!page.linesIterator.exists(_.endsWith(" ")), page)
    }
  }

  test("generateOpml - lists every feed as valid OPML, escaping names") {
    val blogs = List(
      Blog(
        "A Developer's Experience",
        new URI("https://blog.rhetoricalmusings.com"),
        new URI("https://blog.rhetoricalmusings.com/index.xml")
      ),
      Blog(
        "Tom & Jerry <Scala>",
        new URI("https://example.com"),
        new URI("https://example.com/feed?a=1&b=2")
      )
    )
    val doc = javax.xml.parsers.DocumentBuilderFactory
      .newInstance()
      .newDocumentBuilder()
      .parse(
        new java.io.ByteArrayInputStream(
          BlogDirectory.generateOpml(blogs).getBytes("UTF-8")
        )
      )
    val outlines = doc.getElementsByTagName("outline")
    val feeds = (0 until outlines.getLength)
      .map(outlines.item(_).asInstanceOf[org.w3c.dom.Element])
      .filter(_.getAttribute("type") == "rss")
      .map(o => (o.getAttribute("text"), o.getAttribute("xmlUrl"), o.getAttribute("htmlUrl")))
      .toList
    assertEquals(doc.getDocumentElement.getAttribute("version"), "2.0")
    assertEquals(
      feeds,
      List(
        (
          "A Developer's Experience",
          "https://blog.rhetoricalmusings.com/index.xml",
          "https://blog.rhetoricalmusings.com"
        ),
        ("Tom & Jerry <Scala>", "https://example.com/feed?a=1&b=2", "https://example.com")
      )
    )
  }

  test("generateDirectory - links to the OPML file") {
    BlogDirectory
      .generateDirectory(Nil)
      .map(page => assert(page.contains("""<a href="bloggers.opml" download>"""), page))
  }
}
