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

import cats.effect.{ExitCode, IO}
import com.anthropic.core.ObjectMappers
import com.anthropic.models.messages.Message

import com.softinio.scalanews.ArticleSummariser.{
  ArticleInput,
  ArticleText,
  SummaryResponse
}
import com.softinio.scalanews.algebra.{AnthropicConfig, ApiKey}

/** An offline check that the Claude request and reply JSON round-trip through
  * the Anthropic SDK. The SDK serialises with Jackson, which needs native-image
  * metadata; this catches missing metadata in a native binary without a network
  * call or an API key. Run it after building the native image.
  */
object SelfCheck {

  private val sampleReply =
    """|{"id":"msg_self_check","type":"message","role":"assistant",
       |"model":"claude-sonnet-5",
       |"content":[{"type":"text","text":"{\"result\":{\"kind\":\"Summary\",\"text\":\"Self-check summary.\"}}"}],
       |"stop_reason":"end_turn","stop_sequence":null,
       |"usage":{"input_tokens":1,"output_tokens":1}}""".stripMargin

  def run: IO[ExitCode] =
    IO.blocking {
      val mapper = ObjectMappers.jsonMapper()
      val params = AnthropicClient.messageParams(
        AnthropicConfig(ApiKey("self-check")),
        "self-check",
        ArticleInput("Title", "Author", None, ArticleText.Complete("Text")),
        SummaryResponse.structuredOutput
      )
      val request = mapper.writeValueAsString(params._body())
      require(
        request.contains("\"output_config\"") && request.contains("\"anyOf\""),
        s"Request is missing the structured-output schema: $request"
      )
      val message = mapper.readValue(sampleReply, classOf[Message])
      AnthropicClient.decodeReply(
        message,
        SummaryResponse.structuredOutput
      ) match {
        case Right(SummaryResponse.Summary("Self-check summary.")) => ()
        case other => sys.error(s"Unexpected decoded reply: $other")
      }
    }.flatMap(_ => Output.info("Self-check passed").as(ExitCode.Success))
}
