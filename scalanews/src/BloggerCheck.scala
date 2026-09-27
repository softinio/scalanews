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

import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.rometools.rome.feed.synd.SyndFeed

import java.net.URI
import scala.jdk.CollectionConverters.*

import com.softinio.scalanews.algebra.Blog

/** `blogger --check`: validates the bloggers in config.json, e.g. on a pull
  * request that adds one, before the directory page is regenerated.
  */
object BloggerCheck {

  // Feeds fetched at once.
  private val fetchConcurrency = 4

  /** Problems that need no network: missing names, URLs that aren't absolute
    * http(s) URLs, and duplicate names or feeds.
    */
  private[scalanews] def configProblems(bloggers: List[Blog]): List[String] = {
    def urlProblem(blog: Blog, field: String, uri: URI): Option[String] = {
      val scheme = Option(uri.getScheme).map(_.toLowerCase)
      Option.unless(
        scheme.exists(Set("http", "https")) && Option(uri.getHost).exists(
          _.nonEmpty
        )
      )(s"${blog.name}: $field '$uri' isn't an absolute http(s) URL")
    }
    def duplicates(what: String, key: Blog => String): List[String] =
      bloggers
        .groupBy(key)
        .collect {
          case (value, blogs) if blogs.size > 1 =>
            s"duplicate $what '$value': ${blogs.map(_.name).mkString(", ")}"
        }
        .toList
        .sorted

    bloggers.flatMap(blog =>
      Option.when(blog.name.trim.isEmpty)(
        s"an entry has no name (rss ${blog.rss})"
      ) ++
        urlProblem(blog, "url", blog.url) ++
        urlProblem(blog, "rss", blog.rss)
    ) ++
      duplicates("name", _.name.trim.toLowerCase) ++
      duplicates("rss feed", _.rss.toString)
  }

  /** Entries that are new, or changed, compared with `base`. */
  private[scalanews] def newOrChanged(
      bloggers: List[Blog],
      base: List[Blog]
  ): List[Blog] = {
    val existing = base.toSet
    bloggers.filterNot(existing)
  }

  /** Why the feed can't be used for the newsletter, if it can't: it needs at
    * least one entry with a link and a publication date.
    */
  private[scalanews] def feedProblem(feed: SyndFeed): Option[String] = {
    val entries = feed.getEntries.asScala.toList
    val usable = entries.count(entry =>
      Option(entry.getLink).exists(_.trim.nonEmpty) &&
        Feeds.publishedOpt(entry).isDefined
    )
    if (entries.isEmpty) Some("the feed has no entries")
    else if (usable == 0)
      Some(
        s"none of the feed's ${entries.size} entries has both a link and a publication date"
      )
    else None
  }

  /** Checks every entry's config, and fetches the feeds of the entries that are
    * new or changed compared with `base` (all of them without a base). Exits
    * with an error when anything is wrong.
    */
  def check(
      bloggers: List[Blog],
      base: Option[List[Blog]],
      fetch: String => IO[Either[Throwable, SyndFeed]] = Rome.fetchFeed
  ): IO[ExitCode] = {
    val toFetch = base.fold(bloggers)(newOrChanged(bloggers, _))
    for {
      feedResults <- toFetch.parTraverseN(fetchConcurrency)(blog =>
        fetch(blog.rss.toString).map {
          case Left(error) =>
            Left(
              s"${blog.name}: feed ${blog.rss} couldn't be read: ${error.getMessage}"
            )
          case Right(feed) =>
            feedProblem(feed)
              .map(problem => s"${blog.name}: feed ${blog.rss}: $problem")
              .toLeft(
                s"ok: ${blog.name} (${feed.getEntries.size} entries in ${blog.rss})"
              )
        }
      )
      problems = configProblems(bloggers) ++ feedResults.collect {
        case Left(p) => p
      }
      _ <- feedResults.collect { case Right(ok) => ok }.traverse_(Output.info)
      _ <- Output
        .info("No new or changed bloggers to check")
        .whenA(base.isDefined && toFetch.isEmpty)
      _ <- problems.traverse_(Output.error)
      _ <- Output.info(
        s"Checked ${bloggers.size} bloggers' config and ${toFetch.size} feeds: ${problems.size} problems"
      )
    } yield if (problems.isEmpty) ExitCode.Success else ExitCode.Error
  }
}
