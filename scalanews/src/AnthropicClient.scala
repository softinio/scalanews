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

import cats.effect.IO
import cats.syntax.functorFilter.*
import fs2.Stream
import io.circe.{Encoder, parser}
import io.circe.syntax.*
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message, OutputConfig, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.agent.ResponseSchema
import sttp.ai.claude.responses.MessageStreamResponse
import sttp.ai.claude.responses.MessageStreamResponse.ContentDelta.TextDelta
import sttp.ai.claude.streaming.fs2.ClaudeFs2Streaming.*
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

import com.softinio.scalanews.algebra.AnthropicConfig

object AnthropicClient {

  /** Sends `input` as JSON and constrains the reply to `responseSchema` using
    * structured outputs, decoding it into `A`.
    */
  def structured[I: Encoder, A](
      systemPrompt: String,
      input: I,
      responseSchema: ResponseSchema[A],
      config: AnthropicConfig
  ): IO[A] = {
    val request = MessageRequest
      .simple(
        model = config.model.value,
        messages =
          List(Message.user(List(ContentBlock.text(input.asJson.noSpaces)))),
        maxTokens = config.maxTokens,
        outputConfig = Some(
          OutputConfig(
            format = Some(OutputFormat.JsonSchema(responseSchema.schema)),
            effort = None
          )
        )
      )
      .copy(system = Some(systemPrompt))

    send(request, config).flatMap(reply =>
      IO.fromEither(parser.decode(reply)(using responseSchema.codec))
    )
  }

  private def send(
      request: MessageRequest,
      config: AnthropicConfig
  ): IO[String] = {
    val client = ClaudeClient(ClaudeConfig(apiKey = config.apiKey.value))

    HttpClientFs2Backend.resource[IO]().use { backend =>
      val streamRequest = client.createStreamedMessage[IO](request)
      streamRequest
        .send(backend)
        .map(_.body)
        .flatMap {
          case Right(stream) =>
            stream
              .mapFilter {
                case MessageStreamResponse.ContentBlockDelta(
                      _,
                      TextDelta(text)
                    ) =>
                  Some(text)
                case _ => None
              }
              .compile
              .string
          case Left(error) =>
            IO.raiseError(
              new RuntimeException(s"Anthropic stream error: $error")
            )
        }
    }
  }

  def streamMessage(
      prompt: String,
      config: AnthropicConfig
  ): Stream[IO, String] = {
    val claudeConfig = ClaudeConfig(apiKey = config.apiKey.value)
    val client = ClaudeClient(claudeConfig)
    val request = MessageRequest.simple(
      model = config.model.value,
      messages = List(Message.user(List(ContentBlock.text(prompt)))),
      maxTokens = config.maxTokens
    )

    Stream
      .resource(HttpClientFs2Backend.resource[IO]())
      .flatMap { backend =>
        val streamRequest = client.createStreamedMessage[IO](request)
        Stream
          .eval(streamRequest.send(backend).map(_.body))
          .flatMap {
            case Right(stream) =>
              stream.mapFilter {
                case MessageStreamResponse.ContentBlockDelta(
                      _,
                      TextDelta(text)
                    ) =>
                  Some(text)
                case _ => None
              }
            case Left(error) =>
              Stream.raiseError[IO](
                new RuntimeException(s"Anthropic stream error: $error")
              )
          }
      }
  }
}
