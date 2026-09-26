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

import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite
import com.anthropic.core.JsonValue
import com.anthropic.core.http.Headers
import com.anthropic.errors.{
  PermissionDeniedException,
  RateLimitException,
  UnauthorizedException
}
import com.anthropic.models.messages.Model

import com.softinio.scalanews.ArticleSummariser.*
import com.softinio.scalanews.algebra.{AnthropicConfig, ApiKey, ArticleSummary}

class ArticleSummariserSuite extends FunSuite {

  test("ArticleText.from - blank content has no text") {
    assertEquals(ArticleText.from("  \n\t "), None)
  }

  test("ArticleText.from - short content is complete") {
    assertEquals(
      ArticleText.from("  Scala 3 match types  "),
      Some(ArticleText.Complete("Scala 3 match types"))
    )
  }

  test("ArticleText.from - long content is truncated with its length") {
    val content = "a" * (ArticleText.MaxChars + 500)
    assertEquals(
      ArticleText.from(content),
      Some(ArticleText.Truncated("a" * ArticleText.MaxChars, content.length))
    )
  }

  test("ArticleInput - encodes the text kind for Claude") {
    val input = ArticleInput(
      "Title",
      "Author",
      None,
      ArticleText.Truncated("text", 9000)
    )
    val json = input.asJson
    assertEquals(
      json.hcursor.downField("text").get[String]("kind"),
      Right("truncated")
    )
    assertEquals(
      json.hcursor.downField("text").get[Int]("originalLength"),
      Right(9000)
    )
  }

  private val codec = SummaryResponse.structuredOutput.decoder

  test("SummaryResponse - decodes a summary") {
    assertEquals(
      decode("""{"result":{"kind":"Summary","text":"About match types."}}""")(
        using codec
      ),
      Right(SummaryResponse.Summary("About match types."))
    )
  }

  test("SummaryResponse - decodes insufficient content") {
    assertEquals(
      decode(
        """{"result":{"kind":"InsufficientContent","reason":"Only a link."}}"""
      )(using codec),
      Right(SummaryResponse.InsufficientContent("Only a link."))
    )
  }

  test("SummaryResponse - rejects an unknown kind") {
    assert(
      decode("""{"result":{"kind":"Relevant","text":"x"}}""")(using codec).isLeft
    )
  }

  test("ArticleSummary.from - blank text is not a summary") {
    assertEquals(ArticleSummary.from("   "), None)
  }

  test("ArticleSummary.from - collapses whitespace") {
    assertEquals(
      ArticleSummary.from("  Scala\n\n 3   rocks ").map(_.value),
      Some("Scala 3 rocks")
    )
  }

  test("ArticleSummary.from - trims long text at a word boundary") {
    val summary = ArticleSummary.from(("word " * 100).trim).map(_.value)
    assert(summary.exists(_.length <= ArticleSummary.MaxLength))
    assert(summary.exists(_.endsWith("word")))
  }

  test("interpret - a summary is summarised") {
    assertEquals(
      interpret(SummaryResponse.Summary("About sbt.")),
      Summarisation.Summarised(ArticleSummary.from("About sbt.").get)
    )
  }

  test("interpret - an empty summary means no summary") {
    assert(
      interpret(SummaryResponse.Summary("  ")) match {
        case Summarisation.NoSummary(_) => true
        case _                          => false
      }
    )
  }

  test("interpret - insufficient content means no summary") {
    assertEquals(
      interpret(SummaryResponse.InsufficientContent("Only a link.")),
      Summarisation.NoSummary(
        NoSummaryReason.InsufficientContent("Only a link.")
      )
    )
  }

  test("ApiKey - toString is redacted") {
    val config = AnthropicConfig(ApiKey("sk-ant-secret"))
    assert(!config.toString.contains("sk-ant-secret"))
  }

  test("AnthropicConfig - defaults to Sonnet 5") {
    assertEquals(
      AnthropicConfig(ApiKey("key")).model,
      Model.CLAUDE_SONNET_5
    )
  }

  test("AnthropicConfig.validate - accepts a structured-output model") {
    val config = AnthropicConfig(ApiKey("key"))
    assertEquals(AnthropicConfig.validate(config), Right(config))
  }

  test("AnthropicConfig.validate - rejects a model without structured outputs") {
    val config = AnthropicConfig(ApiKey("key"), model = Model.of("claude-3-haiku-20240307"))
    assert(AnthropicConfig.validate(config).isLeft)
  }

  private val noHeaders = Headers.builder().build()
  private val noBody = JsonValue.from(null)

  test("AnthropicClient.isFatal - authentication errors stop the run") {
    assert(
      AnthropicClient.isFatal(
        UnauthorizedException.builder().headers(noHeaders).body(noBody).build()
      )
    )
  }

  test("AnthropicClient.isFatal - permission errors stop the run") {
    assert(
      AnthropicClient.isFatal(
        PermissionDeniedException
          .builder()
          .headers(noHeaders)
          .body(noBody)
          .build()
      )
    )
  }

  test("AnthropicClient.isFatal - wrapped fatal errors stop the run") {
    val wrapped = new java.util.concurrent.CompletionException(
      UnauthorizedException.builder().headers(noHeaders).body(noBody).build()
    )
    assert(AnthropicClient.isFatal(wrapped))
  }

  test("AnthropicClient.isFatal - rate limits fall back per article") {
    assert(
      !AnthropicClient.isFatal(
        RateLimitException.builder().headers(noHeaders).body(noBody).build()
      )
    )
  }

  test("AnthropicClient.isFatal - other errors fall back per article") {
    assert(!AnthropicClient.isFatal(new RuntimeException("timeout")))
  }
}
