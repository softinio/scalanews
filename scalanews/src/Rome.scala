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

import java.io.InputStream

import cats.effect.*

import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader

object Rome {
  final private def parseFeed(
      feedStream: InputStream
  ): IO[Either[Throwable, SyndFeed]] =
    IO.blocking {
      val input: SyndFeedInput = new SyndFeedInput()
      input.build(new XmlReader(feedStream))
    }.attempt

  def fetchFeed(feedUrl: String): IO[Either[Throwable, SyndFeed]] =
    HttpClient.fetchRss(feedUrl).use(parseFeed)
}
