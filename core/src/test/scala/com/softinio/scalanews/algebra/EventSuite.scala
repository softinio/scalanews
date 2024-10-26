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

// package com.softinio.scalanews.algebra
//
// import munit.FunSuite
// import org.http4s.Uri
//
// class EventSuite extends FunSuite {
//   test("Test creating a new event with string URI") {
//     val event = Event(
//       name = "SF Scala",
//       description = "San Francisco Scala meetup",
//       meetupUrl = "http://www.meetup.com/SF-Scala/",
//       lumaUrl = "https://lu.ma/scala",
//       socialMediaUrl = "",
//       otherUrl = "https://www.sfscala.org/",
//       locations = List(Location("San Francisco", Some("CA"), "USA"))
//       )
//     val expectedMeetupUrl = Uri.fromString("http://www.meetup.com/SF-Scala/").toOption
//     assert(event.meetupUrl == expectedMeetupUrl)
//   }
// }
