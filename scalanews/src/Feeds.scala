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
import com.softinio.scalanews.algebra.{Article, Blog}

/** Fetching the bloggers' RSS feeds and turning Scala-related entries into
  * articles.
  */
object Feeds {
  private val blogsToSkipByUrl = List(
    "petr-zapletal.medium.com",
    "sudarshankasar.medium.com"
  )

  private def isAboutScala(entry: SyndEntry): Boolean = {
    val hasRelevantCategory = Option(entry.getCategories)
      .map(_.asScala.toList)
      .getOrElse(List())
      .exists(category => {
        val name = category.getName.toLowerCase
        name.contains("scala") || name.contains("sbt")
      })

    val hasRelevantTitle =
      Option(entry.getTitle)
        .map(_.toLowerCase)
        .getOrElse("")
        .contains("scala") ||
        Option(entry.getTitle).map(_.toLowerCase).getOrElse("").contains("sbt")

    val hasRelevantDescription = Option(entry.getDescription)
      .map(_.getValue.toLowerCase)
      .getOrElse("")
      .contains("scala") ||
      Option(entry.getDescription)
        .map(_.getValue.toLowerCase)
        .getOrElse("")
        .contains("sbt")

    hasRelevantCategory || hasRelevantTitle || hasRelevantDescription
  }

  // Many feeds (plain RSS) only carry a <description> summary, not full
  // <content>; fall back to it so the article isn't stored empty.
  private def entryContent(entry: SyndEntry): List[SyndContent] =
    entry.getContents.asScala.toList match {
      case Nil      => Option(entry.getDescription).toList
      case contents => contents
    }

  private def getBlogAuthor(entry: SyndEntry, blog: Blog): String =
    Option(entry.getAuthor)
      .filter(_.nonEmpty)
      .filter(_.toLowerCase() != "unknown")
      .getOrElse(blog.name)

  private def getArticlesFromEntries(
      blog: Blog,
      entries: List[SyndEntry],
      startDate: Date,
      endDate: Date
  ): Option[List[Article]] =
    entries
      .filter(_.getPublishedDate != null)
      .filter(_.getLink != null)
      .filter(entryItem =>
        blogsToSkipByUrl.forall(skipItem =>
          !entryItem.getLink.contains(skipItem)
        )
      )
      .filter(_.getTitle != null)
      .filter(isAboutScala)
      .map(entry =>
        Article(
          entry.getTitle,
          entryContent(entry),
          entry.getLink,
          getBlogAuthor(entry, blog),
          entry.getPublishedDate
        )
      )
      .filter { case Article(_, _, _, _, publishedDate) =>
        publishedDate.after(startDate) && publishedDate.before(endDate)
      }
      .distinct
      .sortBy(_.publishedDate.getTime)
      .reverse match {
      case Nil  => None
      case list => Some(list)
    }

  /** Articles fetched from the feeds, and the blogs whose feed couldn't be
    * read.
    */
  private[scalanews] final case class FetchedArticles(
      articles: List[Article],
      failedFeeds: List[String]
  )

  /** A blog's articles in the date range, or why its feed couldn't be read
    * (also reported as a warning).
    */
  private def fetchBlog(
      blog: Blog,
      startDate: Date,
      endDate: Date
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
            getArticlesFromEntries(
              blog,
              feed.getEntries.asScala.toList,
              startDate,
              endDate
            ).getOrElse(Nil)
          )
        )
    }

  def getArticlesForBlogger(
      blog: Blog,
      startDate: Date,
      endDate: Date
  ): IO[Option[List[Article]]] =
    fetchBlog(blog, startDate, endDate).map(
      _.toOption.filter(_.nonEmpty)
    )

  private[scalanews] def fetchArticles(
      startDate: Date,
      endDate: Date,
      configFilePath: String = "config.json"
  ): IO[FetchedArticles] =
    ConfigLoader.load(configFilePath).flatMap { conf =>
      conf.bloggers
        .traverse(blog => fetchBlog(blog, startDate, endDate).map(blog -> _))
        .map { results =>
          FetchedArticles(
            results.flatMap(_._2.getOrElse(Nil)),
            results.collect { case (blog, Left(_)) => blog.name }
          )
        }
    }

  def createBlogList(
      startDate: Date,
      endDate: Date,
      configFilePath: String = "config.json"
  ): IO[List[Article]] =
    fetchArticles(startDate, endDate, configFilePath).map(_.articles)

  def createBlogListFromBloggers(
      bloggers: List[Blog],
      startDate: Date,
      endDate: Date
  ): IO[List[Article]] =
    bloggers.flatTraverse(blog =>
      getArticlesForBlogger(blog, startDate, endDate).map(_.getOrElse(Nil))
    )
}
