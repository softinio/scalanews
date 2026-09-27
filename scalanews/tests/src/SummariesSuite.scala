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
}
