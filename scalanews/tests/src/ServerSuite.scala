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

import cats.effect.*
import munit.CatsEffectSuite

import org.http4s.*
import org.http4s.implicits.*

class ServerSuite extends CatsEffectSuite {

  private val service = Server.scalaNewsService

  test("GET /health should return 200 with server status") {
    val request = Request[IO](Method.GET, uri"/health")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.Ok) >>
      assertIO(response.flatMap(_.as[String]), "Server is running")
  }

  test("GET / should return 404 for root path") {
    val request = Request[IO](Method.GET, uri"/")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.NotFound)
  }

  test("GET /nonexistent should return 404") {
    val request = Request[IO](Method.GET, uri"/nonexistent")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.NotFound)
  }

  test("POST /health should return 404 Not Found") {
    val request = Request[IO](Method.POST, uri"/health")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.NotFound)
  }
}
