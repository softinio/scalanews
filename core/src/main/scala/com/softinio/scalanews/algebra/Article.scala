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

import java.util.Date
import org.http4s.Uri

case class Article(
    title: String,
    url: Option[Uri],
    author: String,
    publishedDate: Date
)

object Article {
  def apply(
      title: String,
      url: String,
      author: String,
      publishedDate: Date
  ): Article = {
    val parsedUrl = Uri.fromString(url).toOption
    Article(title, parsedUrl, author, publishedDate)
  }
}
