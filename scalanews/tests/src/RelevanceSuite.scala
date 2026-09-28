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

class RelevanceSuite extends CatsEffectSuite {
  private def noul(p: Double) =
    com.softinio.verdict4s.algebra.Probability.either(p).toOption.get

  test("relevant - about Scala and not just an announcement") {
    assert(Relevance.relevant(noul(0.98), noul(0.03), noul(0.02)))
  }

  test("relevant - a release announcement is not relevant") {
    assert(!Relevance.relevant(noul(0.98), noul(0.94), noul(0.02)))
  }

  test("relevant - an off-topic article is not relevant") {
    assert(!Relevance.relevant(noul(0.03), noul(0.02), noul(0.02)))
  }

  test("relevant - borderline Scala community news is kept") {
    assert(Relevance.relevant(noul(0.41), noul(0.02), noul(0.02)))
  }

  test("relevant - a post that also mentions a release is kept") {
    assert(Relevance.relevant(noul(0.97), noul(0.31), noul(0.02)))
  }

  test("relevant - thresholds are inclusive for yes") {
    assert(Relevance.relevant(noul(Relevance.aboutScalaThreshold), noul(0.0), noul(0.02)))
    assert(
      !Relevance.relevant(noul(1.0), noul(Relevance.announcementThreshold), noul(0.02))
    )
  }

  test("relevant - sales or recruitment content is not relevant") {
    assert(!Relevance.relevant(noul(0.95), noul(0.05), noul(0.9)))
  }

  test("relevant - a post by a company that shares knowledge is kept") {
    assert(Relevance.relevant(noul(0.95), noul(0.05), noul(0.3)))
  }

  test("relevant - the sales/recruitment threshold is inclusive for yes") {
    assert(
      !Relevance.relevant(
        noul(1.0),
        noul(0.0),
        noul(Relevance.promotionalThreshold)
      )
    )
  }

  test("relevanceQuestions - form a valid request with the title and description") {
    val request = Relevance.relevanceQuestions.request(
      Relevance.RelevanceContext("sbt 2.0.9", "Bug fixes."),
      com.softinio.verdict4s.algebra.Model.JevLatest
    )
    assert(request.isValid)
  }

  test("relevant - decided from stored probabilities") {
    assert(Relevance.relevant(StoredRelevance(noul(0.98), noul(0.03), noul(0.02), "jev")))
    assert(!Relevance.relevant(StoredRelevance(noul(0.98), noul(0.94), noul(0.02), "jev")))
  }

  private def verdictApiError(status: Int) =
    com.softinio.verdict4s.Verdict4sError.Api(status, "details")

  test("isFatalVerdict - a bad key or no permission stops the run") {
    assert(Relevance.isFatalVerdict(verdictApiError(401)))
    assert(Relevance.isFatalVerdict(verdictApiError(403)))
    assert(
      Relevance.isFatalVerdict(
        new RuntimeException("wrapped", verdictApiError(401))
      )
    )
  }

  test("isFatalVerdict - rate limits and other errors keep going") {
    assert(!Relevance.isFatalVerdict(verdictApiError(429)))
    assert(!Relevance.isFatalVerdict(verdictApiError(500)))
    assert(!Relevance.isFatalVerdict(new RuntimeException("timeout")))
  }

  test("missingTypesafeKeyHint - points to --no-ai when the key is missing") {
    val missing = com.softinio.verdict4s.Verdict4sError.Validation(
      "TYPESAFE_API_KEY",
      "environment variable is not set"
    )
    val hinted = Relevance.missingTypesafeKeyHint.lift(missing)
    assert(hinted.exists(_.getMessage.contains("--no-ai")))
    assert(hinted.exists(_.getCause eq missing))
  }

  test("missingTypesafeKeyHint - leaves other errors alone") {
    val other = com.softinio.verdict4s.Verdict4sError.Validation(
      "TYPESAFE_BASE_URL",
      "not a URL"
    )
    assert(!Relevance.missingTypesafeKeyHint.isDefinedAt(other))
    assert(!Relevance.missingTypesafeKeyHint.isDefinedAt(verdictApiError(401)))
  }
}
