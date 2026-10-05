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

import pureconfig.*
import pureconfig.module.catseffect.syntax.*
import cats.effect.IO
import com.softinio.scalanews.algebra.{AnthropicConfig, ApiKey}
import com.softinio.scalanews.algebra.Configuration
import com.softinio.scalanews.algebra.Config.given

object ConfigLoader {

  /** Loads the config named by `SCALA_NEWS_CONFIG` (a system property or
    * environment variable), or `config.json` without one. The override is read
    * when the IO runs, so the native image can't capture it at build time.
    */
  def load(): IO[Configuration] =
    IO(
      sys.props
        .get("SCALA_NEWS_CONFIG")
        .orElse(sys.env.get("SCALA_NEWS_CONFIG"))
        .getOrElse("config.json")
    ).flatMap(load)

  /** Loads the config at `filePath`. `SCALA_NEWS_CONFIG` doesn't apply: a path
    * given explicitly (such as `blogger --check --base`) is always the one
    * read.
    */
  def load(filePath: String): IO[Configuration] =
    ConfigSource.file(filePath).loadF[IO, Configuration]()

  def loadAnthropicConfig(): IO[AnthropicConfig] =
    // Read the environment when the IO runs, not when it's built, so a value
    // captured early (e.g. at native-image build time) can't go stale.
    IO(sys.env.get("ANTHROPIC_API_KEY").filter(_.nonEmpty))
      .flatMap(
        IO.fromOption(_)(
          new UserError(
            "ANTHROPIC_API_KEY is not set: set it for Claude summaries, or use --no-ai for plain summaries"
          )
        )
      )
      .flatMap { key =>
        IO.fromEither(
          AnthropicConfig
            .validate(AnthropicConfig(ApiKey(key)))
            .left
            .map(new UserError(_))
        )
      }
}
