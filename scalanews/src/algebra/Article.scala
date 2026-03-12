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
import com.rometools.rome.feed.synd.SyndContent
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter

case class Article(
    title: String,
    content: String,
    url: Option[Uri],
    author: String,
    publishedDate: Date
)

object Article {
  private val htmlToMd = FlexmarkHtmlConverter.builder().build()

  private def syndContentsToMarkdown(contents: List[SyndContent]): String =
    contents
      .map(c => htmlToMd.convert(c.getValue))
      .mkString("\n\n")

  def apply(
      title: String,
      content: List[SyndContent],
      url: String,
      author: String,
      publishedDate: Date
  ): Article = {
    val parsedUrl = Uri
      .fromString(url)
      .toOption
      .filter(u =>
        u.scheme.contains(Uri.Scheme.http) || u.scheme
          .contains(Uri.Scheme.https)
      )
    Article(
      title,
      syndContentsToMarkdown(content),
      parsedUrl,
      author,
      publishedDate
    )
  }
}
