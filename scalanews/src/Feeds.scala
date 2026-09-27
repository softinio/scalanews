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

import java.util.Date
import cats.effect.*
import cats.syntax.all.*
import com.rometools.rome.feed.synd.{SyndContent, SyndEntry}

import scala.jdk.CollectionConverters.*
import com.softinio.scalanews.algebra.{Article, Blog, DateRange}

/** Fetching the bloggers' RSS feeds and turning Scala-related entries into
  * articles.
  */
object Feeds {
  private val blogsToSkipByUrl = List(
    "petr-zapletal.medium.com",
    "sudarshankasar.medium.com"
  )

  /** An entry counts as about Scala when one of these appears in its title,
    * description or categories.
    */
  private val scalaKeywords = List("scala", "sbt")

  // Rome returns null for missing fields; read them as options instead.
  extension (entry: SyndEntry) {
    private def titleOpt: Option[String] = Option(entry.getTitle)
    private def linkOpt: Option[String] = Option(entry.getLink)
    private def publishedOpt: Option[Date] = Option(entry.getPublishedDate)
    private def descriptionText: Option[String] =
      Option(entry.getDescription).flatMap(d => Option(d.getValue))
    private def categoryNames: List[String] =
      Option(entry.getCategories).toList
        .flatMap(_.asScala)
        .flatMap(c => Option(c.getName))
    private def authorOpt: Option[String] =
      Option(entry.getAuthor)
        .filter(_.nonEmpty)
        .filter(_.toLowerCase != "unknown")
  }

  private def isAboutScala(entry: SyndEntry): Boolean =
    (entry.categoryNames ++ entry.titleOpt ++ entry.descriptionText).exists(
      text => scalaKeywords.exists(text.toLowerCase.contains)
    )

  // Many feeds (plain RSS) only carry a <description> summary, not full
  // <content>; fall back to it so the article isn't stored empty.
  private def entryContent(entry: SyndEntry): List[SyndContent] =
    entry.getContents.asScala.toList match {
      case Nil      => Option(entry.getDescription).toList
      case contents => contents
    }

  /** The entry's link as a full URL: some feeds give links relative to the
    * blog, e.g. "/posts/scala-kotlin/".
    */
  private[scalanews] def absoluteLink(blog: Blog, link: String): String = {
    // java.net.URI drops the "/" between a host with no path and a relative
    // link, so resolve against the site root in that case.
    val base =
      if (blog.url.getPath.isEmpty) blog.url.resolve("/") else blog.url
    scala.util.Try(base.resolve(link.trim).toString).getOrElse(link)
  }

  /** The entry as an article, if it has a title, link and publication date in
    * range, isn't from a skipped blog, and is about Scala.
    */
  private[scalanews] def toArticle(
      blog: Blog,
      entry: SyndEntry,
      range: DateRange
  ): Option[Article] =
    for {
      title <- entry.titleOpt
      link <- entry.linkOpt.map(absoluteLink(blog, _))
      published <- entry.publishedOpt
      if range.contains(published) &&
        !blogsToSkipByUrl.exists(link.contains) &&
        isAboutScala(entry)
    } yield Article(
      title,
      entryContent(entry),
      link,
      entry.authorOpt.getOrElse(blog.name),
      published
    )

  /** Articles fetched from the feeds, and the blogs whose feed couldn't be
    * read.
    */
  private[scalanews] final case class FetchedArticles(
      articles: List[Article],
      failedFeeds: List[String]
  )

  /** A blog's articles in the date range, newest first, or why its feed
    * couldn't be read (also reported as a warning).
    */
  private[scalanews] def fetchBlog(
      blog: Blog,
      range: DateRange
  ): IO[Either[Throwable, List[Article]]] =
    Rome.fetchFeed(blog.rss.toURL.toString).flatMap {
      case Left(exception) =>
        Output
          .warn(
            s"Error fetching feed for blog ${blog.name}: ${exception.getMessage}"
          )
          .as(Left(exception))
      case Right(feed) =>
        IO.pure(
          Right(
            feed.getEntries.asScala.toList
              .flatMap(toArticle(blog, _, range))
              .distinct
              .sortBy(_.publishedDate.getTime)
              .reverse
          )
        )
    }

  private[scalanews] def fetchArticles(
      range: DateRange,
      configFilePath: String = "config.json"
  ): IO[FetchedArticles] =
    ConfigLoader.load(configFilePath).flatMap { conf =>
      conf.bloggers
        .traverse(blog => fetchBlog(blog, range).map(blog -> _))
        .map { results =>
          FetchedArticles(
            results.flatMap(_._2.getOrElse(Nil)),
            results.collect { case (blog, Left(_)) => blog.name }
          )
        }
    }
}
