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
import fs2.Stream
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.streaming.fs2.*
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

import com.softinio.scalanews.algebra.AnthropicConfig

object AnthropicClient {

  def sendMessage(prompt: String, config: AnthropicConfig): IO[String] = {
    val claudeConfig = ClaudeConfig(apiKey = config.apiKey)
    val client = ClaudeClient(claudeConfig)
    val request = MessageRequest.simple(
      model = config.model,
      messages = List(Message.user(List(ContentBlock.text(prompt)))),
      maxTokens = config.maxTokens
    )

    HttpClientFs2Backend.resource[IO]().use { backend =>
      val streamRequest =
        client.createMessageAsBinaryStream(backend.capabilities.streams, request)
      streamRequest
        .send(backend)
        .map(_.map(_.parseSSE.parseClaudeStreamResponse))
        .flatMap {
          case Right(stream) =>
            stream
              .mapFilter(_.delta.text)
              .compile
              .string
          case Left(error) =>
            IO.raiseError(new RuntimeException(s"Anthropic stream error: $error"))
        }
    }
  }

  def streamMessage(
      prompt: String,
      config: AnthropicConfig
  ): Stream[IO, String] = {
    val claudeConfig = ClaudeConfig(apiKey = config.apiKey)
    val client = ClaudeClient(claudeConfig)
    val request = MessageRequest.simple(
      model = config.model,
      messages = List(Message.user(List(ContentBlock.text(prompt)))),
      maxTokens = config.maxTokens
    )

    Stream
      .resource(HttpClientFs2Backend.resource[IO]())
      .flatMap { backend =>
        val streamRequest =
          client.createMessageAsBinaryStream(backend.capabilities.streams, request)
        Stream
          .eval(streamRequest.send(backend).map(_.map(_.parseSSE.parseClaudeStreamResponse)))
          .flatMap {
            case Right(stream) => stream.mapFilter(_.delta.text)
            case Left(error) =>
              Stream.raiseError[IO](
                new RuntimeException(s"Anthropic stream error: $error")
              )
          }
      }
  }
}
