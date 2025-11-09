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

import munit.CatsEffectSuite
import org.http4s.Uri

import com.softinio.scalanews.algebra.Event
import com.softinio.scalanews.algebra.EventType
import com.softinio.scalanews.algebra.Location

class EventsSuite extends CatsEffectSuite {
  test("generateDirectory - test the new events directory is generated") {
    val event = Event(
      name = "SF Scala",
      description = "San Francisco Scala meetup",
      meetupUrl = Uri.fromString("http://www.meetup.com/SF-Scala/").toOption,
      lumaUrl = Uri.fromString("https://lu.ma/scala").toOption,
      socialMediaUrl = None,
      otherUrl = Uri.fromString("https://www.sfscala.org/").toOption,
      locations = List(Location("San Francisco", Some("CA"), "USA"))
    )
    val obtained = for {
      result <- Events.generateDirectory(List(event), EventType.Meetup)
    } yield result.contains(
      "**Meetup:** <http://www.meetup.com/SF-Scala/>"
    )
    assertIO(obtained, true)
  }
}
