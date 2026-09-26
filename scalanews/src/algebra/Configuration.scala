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

package com.softinio.scalanews.algebra

import pureconfig.*
import com.anthropic.models.messages.Model

import java.net.URI

final case class Blog(name: String, url: URI, rss: URI) derives ConfigReader
final case class Configuration(bloggers: List[Blog]) derives ConfigReader
final case class EventConfig(meetups: List[Event], conferences: List[Event])
    derives ConfigReader

/** An Anthropic API key. Its `toString` is redacted so it can't leak into logs.
  */
final case class ApiKey(value: String) {
  override def toString: String = "ApiKey(<redacted>)"
}

final case class AnthropicConfig(
    apiKey: ApiKey,
    model: Model = Model.CLAUDE_SONNET_5,
    maxTokens: Int = 1024
)

object AnthropicConfig {

  /** Models known to support structured outputs, which summaries rely on. */
  val structuredOutputModels: Set[Model] = Set(
    Model.CLAUDE_SONNET_5,
    Model.CLAUDE_HAIKU_4_5,
    Model.CLAUDE_OPUS_5,
    Model.CLAUDE_OPUS_5_5
  )

  /** Summaries are requested with structured outputs, so the model must support
    * them.
    */
  def validate(config: AnthropicConfig): Either[String, AnthropicConfig] =
    Either.cond(
      structuredOutputModels.contains(config.model),
      config,
      s"Model ${config.model.asString} does not support structured outputs"
    )
}

object Config {
  given ConfigReader[URI] = ConfigReader[String].map(URI.create)
}
