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

import org.http4s.Uri
import pureconfig.*
import pureconfig.generic.derivation.default.*

enum EventType:
  case Meetup, Conference

case class Location(city: String, state: Option[String], country: String)
    derives ConfigReader

case class Event(
    name: String,
    description: String,
    meetupUrl: Option[Uri],
    lumaUrl: Option[Uri],
    socialMediaUrl: Option[Uri],
    otherUrl: Option[Uri],
    locations: List[Location]
) derives ConfigReader

object Event {
  given ConfigReader[Option[Uri]] =
    ConfigReader[Option[String]].map(_.flatMap(Uri.fromString(_).toOption))
}
