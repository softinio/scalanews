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

class SummariesSuite extends CatsEffectSuite {
  test("storedFor - stores Claude's decisions only") {
    import ArticleSummariser.{NoSummaryReason, Summarisation}
    val summary = ArticleSummary.from("About types.").get
    assertEquals(
      Summaries.storedFor(Summarisation.Summarised(summary), "m"),
      Some(StoredSummary.Summarised(summary, "m"))
    )
    assertEquals(
      Summaries.storedFor(
        Summarisation.NoSummary(NoSummaryReason.InsufficientContent("link")),
        "m"
      ),
      Some(StoredSummary.NoSummary("link", "m"))
    )
    assertEquals(
      Summaries.storedFor(Summarisation.NoSummary(NoSummaryReason.NoText), "m"),
      None
    )
    assertEquals(
      Summaries.storedFor(Summarisation.Failed(new RuntimeException("x")), "m"),
      None
    )
  }

  test("summaryOf - a stored no-summary outcome shows no summary") {
    val summary = ArticleSummary.from("About types.").get
    assertEquals(
      Summaries.summaryOf(StoredSummary.Summarised(summary, "m")),
      Some(summary)
    )
    assertEquals(
      Summaries.summaryOf(StoredSummary.NoSummary("link", "m")),
      None
    )
  }

  private def plain(markdown: String) = Summaries.plainText(markdown).trim

  test("plainText - links keep their text") {
    assertEquals(
      plain("I gave a talk. * [slides](https://example.com/slides) here"),
      "I gave a talk. * slides here"
    )
    assertEquals(
      plain("See [sudori part 4](https://eed3si9n.com/sudori-part4), [part"),
      "See sudori part 4, [part"
    )
  }

  test("plainText - drops images, including linked ones") {
    assertEquals(
      plain(
        "[![](https://alexn.org/a.jpg)](https://alexn.org/post \"Open\") *The* text"
      ),
      "The text"
    )
  }

  test("plainText - drops headings, including setext ones with anchors") {
    assertEquals(
      plain(
        """Introduction {#heading-introduction}
          |====================================
          |
          |> This blog is a part of the [Data Plumber Series](https://example.com).""".stripMargin
      ),
      "This blog is a part of the Data Plumber Series."
    )
    assertEquals(plain("# Title\n\nBody text."), "Body text.")
  }

  test("plainText - drops emphasis and code markers but keeps the words") {
    assertEquals(
      plain("**TL;DR** : use `Option[A]` or `A | Null`, not ***this***"),
      "TL;DR : use Option[A] or A | Null, not this"
    )
  }

  test("plainText - drops emphasis markers CommonMark leaves as text") {
    assertEquals(
      plain(
        "This blog is a part of the [***Data Plumber Series***](https://example.com)***.*** In Part 1"
      ),
      "This blog is a part of the Data Plumber Series. In Part 1"
    )
  }

  test("plainText - drops code blocks and HTML") {
    assertEquals(
      plain("Before.\n\n```scala\nval x = 1\n```\n\n<div>raw</div>\n\nAfter."),
      "Before. After."
    )
  }

  test("simpleSummary - is plain text") {
    assertEquals(
      Summaries.simpleSummary("## Heading\n\nA [link](https://x.y) and **bold**."),
      ArticleSummary.from("A link and bold.")
    )
  }
}
