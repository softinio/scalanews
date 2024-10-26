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
import pureconfig.generic.derivation.default.*

import java.net.URI

final case class Blog(name: String, url: URI, rss: URI) derives ConfigReader
final case class Configuration(bloggers: List[Blog]) derives ConfigReader
final case class EventConfig(meetups: List[Event], conferences: List[Event])
    derives ConfigReader

object Config {
  given ConfigReader[URI] = ConfigReader[String].map(URI.create)
}
