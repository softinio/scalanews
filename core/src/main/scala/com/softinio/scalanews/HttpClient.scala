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

import cats.effect._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import java.io.InputStream
import org.http4s.Request
import org.http4s.Method
import org.http4s.Uri
import fs2._

object HttpClient {

  def fetchRss(feedUrl: String): Resource[IO, InputStream] = {
    val request = Request[IO](Method.GET, Uri.unsafeFromString(feedUrl))
    client.flatMap { client =>
      client
        .run(request)
        .flatMap(response => io.toInputStreamResource(response.body))
    }
  }

  private lazy val client: Resource[IO, Client[IO]] =
    EmberClientBuilder
      .default[IO]
      .build
}
