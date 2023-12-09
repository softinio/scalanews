/*
 * Copyright 2023 Salar Rahmanian
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
import munit.CatsEffectSuite

import cats.effect.unsafe.IORuntime

class HttpClientSuite extends CatsEffectSuite {

  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
  test("Fetch Rss") {
    val result = HttpClient.fetchRss("https://www.softinio.com/index.xml")
    val obtained = result.use { res =>
      val resultStr = new String(res.readAllBytes)
      IO(resultStr.contains("lightening-talks-at-pybay-2018"))
    }
    assertIO(obtained, true)
  }
}
