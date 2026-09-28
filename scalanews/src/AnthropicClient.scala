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
import com.anthropic.client.AnthropicClient as SdkClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.{PermissionDeniedException, UnauthorizedException}
import com.anthropic.models.messages.{
  JsonOutputFormat,
  Message,
  MessageCreateParams,
  OutputConfig,
  StopReason
}
import com.fasterxml.jackson.databind.ObjectMapper
import io.circe.{Encoder, parser}
import io.circe.syntax.*

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import com.softinio.scalanews.algebra.AnthropicConfig

/** A Claude client for one run, wrapping Anthropic's official Java SDK. */
final class AnthropicClient private (
    client: SdkClient,
    config: AnthropicConfig
) {

  /** Sends `input` as JSON and constrains the reply to `output`'s schema using
    * structured outputs, decoding it into `A`.
    */
  def structured[I: Encoder, A](
      systemPrompt: String,
      input: I,
      output: StructuredOutput[A]
  ): IO[A] = {
    val params =
      AnthropicClient.messageParams(config, systemPrompt, input, output)
    IO.fromCompletableFuture(IO(client.async().messages().create(params)))
      .flatMap(message =>
        IO.fromEither(AnthropicClient.decodeReply(message, output))
      )
  }
}

object AnthropicClient {

  def resource(config: AnthropicConfig): Resource[IO, AnthropicClient] =
    Resource
      .make(
        IO(
          AnthropicOkHttpClient
            .builder()
            .apiKey(config.apiKey.value)
            .build()
        )
      )(client => IO.blocking(client.close()))
      .map(new AnthropicClient(_, config))

  private val mapper = new ObjectMapper()

  /** The request for a structured-output call. Shared with [[SelfCheck]] so the
    * offline check exercises exactly what real calls send.
    */
  private[scalanews] def messageParams[I: Encoder](
      config: AnthropicConfig,
      systemPrompt: String,
      input: I,
      output: StructuredOutput[?]
  ): MessageCreateParams =
    MessageCreateParams
      .builder()
      .model(config.model)
      .maxTokens(config.maxTokens.toLong)
      .system(systemPrompt)
      .addUserMessage(input.asJson.noSpaces)
      .outputConfig(
        OutputConfig
          .builder()
          .format(
            JsonOutputFormat.builder().schema(toSdkSchema(output)).build()
          )
          .build()
      )
      .build()

  /** Decodes a finished reply into `A`; a reply cut short (e.g. at
    * `max_tokens`) is an error.
    */
  private[scalanews] def decodeReply[A](
      message: Message,
      output: StructuredOutput[A]
  ): Either[Throwable, A] =
    message.stopReason().toScala match {
      case Some(StopReason.END_TURN) =>
        val reply = message
          .content()
          .asScala
          .flatMap(_.text().toScala)
          .map(_.text())
          .mkString
        parser.decode(reply)(using output.decoder)
      case other =>
        Left(
          new RuntimeException(
            s"Claude did not finish its reply (stop_reason: ${other.fold("none")(_.toString)})"
          )
        )
    }

  /** Converts our derived JSON schema into the SDK's schema type. */
  private[scalanews] def toSdkSchema(
      output: StructuredOutput[?]
  ): JsonOutputFormat.Schema = {
    val fields = output.schema.asObject
      .fold(Map.empty[String, JsonValue])(
        _.toMap.map((key, value) =>
          key -> JsonValue.fromJsonNode(mapper.readTree(value.noSpaces))
        )
      )
    JsonOutputFormat.Schema
      .builder()
      .putAllAdditionalProperties(fields.asJava)
      .build()
  }

  /** Errors every call would hit (bad key, no permission), so a run should stop
    * instead of falling back article by article.
    */
  def isFatal(error: Throwable): Boolean =
    error match {
      case _: UnauthorizedException | _: PermissionDeniedException => true
      case e if e.getCause != null && (e.getCause ne e) => isFatal(e.getCause)
      case _                                            => false
    }
}
