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

import java.net.URI

import munit.CatsEffectSuite

import com.softinio.scalanews.algebra.Blog

class BloggersSuite extends CatsEffectSuite {
  test("generateDirectory - test the new blogger directory is generated") {
    val blog = Blog(
      "Salar Rahmanian",
      new URI("https://www.softinio.com"),
      new URI("https://www.softinio.com/index.xml")
    )
    val obtained = for {
      result <- Bloggers.generateDirectory(List(blog))
    } yield (result.contains(
      "| Salar Rahmanian | <https://www.softinio.com> | [rss feed](https://www.softinio.com/index.xml) |"
    ))
    assertIO(obtained, true)
  }
}
