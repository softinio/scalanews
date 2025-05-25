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

package com.softinio.scalanews.service

import cats.effect.*
import munit.CatsEffectSuite

import org.http4s.*
import org.http4s.implicits.*
import io.circe.*
import io.circe.parser.*

object IntegrationTest extends munit.Tag("IntegrationTest")

class BlogServiceSuite extends CatsEffectSuite {

  private val service = BlogService.routes

  test("GET /blog should return 200 with JSON array of articles".tag(IntegrationTest)) {
    val request = Request[IO](Method.GET, uri"/blog")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.Ok) >>
    response.flatMap(_.as[String]).map { body =>
      val json = parse(body)
      assert(json.isRight, s"Response should be valid JSON: $body")
      val jsonValue = json.getOrElse(Json.Null)
      assert(jsonValue.isArray, "Response should be a JSON array")
      assert(jsonValue.asArray.size == 1)
    }
  }

  test("GET /blog with custom startDate and endDate parameters".tag(IntegrationTest)) {
    val request = Request[IO](Method.GET, uri"/blog?startDate=2024-01-01&endDate=2024-01-07")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.Ok) >>
    response.flatMap(_.as[String]).map { body =>
      val json = parse(body)
      assert(json.isRight, s"Response should be valid JSON: $body")
      val jsonValue = json.getOrElse(Json.Null)
      assert(jsonValue.isArray, "Response should be a JSON array")
    }
  }

  test("GET /blog with only startDate parameter".tag(IntegrationTest)) {
    val request = Request[IO](Method.GET, uri"/blog?startDate=2024-01-01")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.Ok) >>
    response.flatMap(_.as[String]).map { body =>
      val json = parse(body)
      assert(json.isRight, s"Response should be valid JSON: $body")
      val jsonValue = json.getOrElse(Json.Null)
      assert(jsonValue.isArray, "Response should be a JSON array")
    }
  }

  test("GET /blog with only endDate parameter".tag(IntegrationTest)) {
    val request = Request[IO](Method.GET, uri"/blog?endDate=2024-01-07")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.Ok) >>
    response.flatMap(_.as[String]).map { body =>
      val json = parse(body)
      assert(json.isRight, s"Response should be valid JSON: $body")
      val jsonValue = json.getOrElse(Json.Null)
      assert(jsonValue.isArray, "Response should be a JSON array")
    }
  }

  test("GET /blog/nonexistent should return 404") {
    val request = Request[IO](Method.GET, uri"/blog/nonexistent")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.NotFound)
  }

  test("POST /blog should return 404 Not Found") {
    val request = Request[IO](Method.POST, uri"/blog")
    val response = service.orNotFound.run(request)

    assertIO(response.map(_.status), Status.NotFound)
  }
}
