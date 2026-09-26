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

import cats.effect.{IO, Resource}
import io.circe.{Encoder, parser}
import io.circe.syntax.*
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message, OutputConfig, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.agent.ResponseSchema
import sttp.client4.{Backend, ResponseException}
import sttp.client4.httpclient.cats.HttpClientCatsBackend

import com.softinio.scalanews.algebra.AnthropicConfig

/** A Claude client for one run, sharing a single HTTP backend across calls. */
final class AnthropicClient private (
    client: ClaudeClient,
    backend: Backend[IO],
    config: AnthropicConfig
) {

  /** Sends `input` as JSON and constrains the reply to `responseSchema` using
    * structured outputs, decoding it into `A`.
    */
  def structured[I: Encoder, A](
      systemPrompt: String,
      input: I,
      responseSchema: ResponseSchema[A]
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

    client
      .createMessage(request)
      .send(backend)
      .flatMap(response => IO.fromEither(response.body))
      .flatMap { message =>
        message.stopReason match {
          case Some("end_turn") =>
            val reply = message.content.collect { case t: ContentBlock.Text =>
              t.text
            }.mkString
            IO.fromEither(parser.decode(reply)(using responseSchema.codec))
          case other =>
            IO.raiseError(
              new RuntimeException(
                s"Claude did not finish its reply (stop_reason: ${other.getOrElse("none")})"
              )
            )
        }
      }
  }
}

object AnthropicClient {

  def resource(config: AnthropicConfig): Resource[IO, AnthropicClient] =
    HttpClientCatsBackend
      .resource[IO]()
      .map(backend =>
        new AnthropicClient(
          ClaudeClient(ClaudeConfig(apiKey = config.apiKey.value)),
          backend,
          config
        )
      )

  private val fatalStatusCodes = Set(401, 403)

  /** Errors every call would hit (bad key, no permission), so a run should stop
    * instead of falling back article by article.
    *
    * sttp-ai's non-streaming calls report a 401 as a deserialization error
    * rather than an AuthenticationException, so this also checks the HTTP
    * status anywhere in the cause chain.
    */
  def isFatal(error: Throwable): Boolean =
    error match {
      case _: ClaudeException.AuthenticationException |
          _: ClaudeException.PermissionException =>
        true
      case e: ResponseException[?] if fatalStatusCodes(e.response.code.code) =>
        true
      case e if e.getCause != null && (e.getCause ne e) => isFatal(e.getCause)
      case _                                            => false
    }
}
