/*
 * Copyright 2026 Salar Rahmanian
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

import cats.effect.{IO, Resource}

import com.softinio.scalanews.algebra.{Article, DateRange}
import com.softinio.scalanews.db.tables.{ArticleRow, StoredRelevance}

/** Fetches articles from the bloggers' feeds. */
trait FeedSource {
  def fetch(range: DateRange): IO[Feeds.FetchedArticles]
}

/** Gets jev's relevance verdict for a stored article. */
trait RelevanceChecker {
  def check(row: ArticleRow): IO[StoredRelevance]
}

/** Gets Claude's summary for an article. */
trait Summariser {

  /** The model the summaries come from, stored with each one. */
  def model: String
  def summarise(article: Article): IO[ArticleSummariser.Summarisation]
}

/** What the newsletter pipeline needs from the outside world. The AI services
  * are resources, acquired only when some article needs them, so a run with
  * everything stored needs no API keys. Tests pass fakes instead of `live`.
  */
final case class Services(
    feeds: FeedSource,
    relevance: Resource[IO, RelevanceChecker],
    summariser: Resource[IO, Summariser]
)

object Services {

  /** The real feeds, jev and Claude. A `def`, not a `val`: the native image
    * initialises classes at build time, and nothing about the live services
    * (such as the environment they read) may be captured then.
    */
  def live: Services = Services(
    feeds = range => Feeds.fetchArticles(range),
    relevance = Relevance.verdictClient.map(client =>
      row => Relevance.assessRelevance(row, client)
    ),
    summariser = Resource
      .eval(ConfigLoader.loadAnthropicConfig())
      .flatMap(config =>
        AnthropicClient
          .resource(config)
          .map(client =>
            new Summariser {
              val model: String = config.model.asString
              def summarise(
                  article: Article
              ): IO[ArticleSummariser.Summarisation] =
                ArticleSummariser.summarise(article, client)
            }
          )
      )
  )
}
