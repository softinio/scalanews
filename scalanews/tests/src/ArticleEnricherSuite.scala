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

package com.softinio.scalanews

import munit.FunSuite

class ArticleEnricherSuite extends FunSuite {

  test("parseResponse - relevant article returns Some with summary") {
    val response = """{"relevant": true, "summary": "A post about Scala 3 opaque types."}"""
    assertEquals(
      ArticleEnricher.parseResponse(response),
      Some("A post about Scala 3 opaque types.")
    )
  }

  test("parseResponse - non-relevant article returns None") {
    val response = """{"relevant": false, "summary": ""}"""
    assertEquals(ArticleEnricher.parseResponse(response), None)
  }

  test("parseResponse - malformed JSON returns None") {
    val response = "not json at all"
    assertEquals(ArticleEnricher.parseResponse(response), None)
  }

  test("parseResponse - missing fields returns None") {
    val response = """{"something": "else"}"""
    assertEquals(ArticleEnricher.parseResponse(response), None)
  }

  test("parseResponse - relevant false ignores non-empty summary") {
    val response = """{"relevant": false, "summary": "should be ignored"}"""
    assertEquals(ArticleEnricher.parseResponse(response), None)
  }

  test("parseResponse - JSON wrapped in a markdown code fence") {
    val response =
      "```json\n{\"relevant\": true, \"summary\": \"Scala 3 match types.\"}\n```"
    assertEquals(
      ArticleEnricher.parseResponse(response),
      Some("Scala 3 match types.")
    )
  }

  test("parseResponse - JSON surrounded by extra text") {
    val response =
      """Here you go: {"relevant": true, "summary": "About sbt."} Hope that helps."""
    assertEquals(ArticleEnricher.parseResponse(response), Some("About sbt."))
  }
}
