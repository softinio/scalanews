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

import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Date
import cats.effect._
import cats.nio.file.Files
import com.rometools.rome.feed.synd.SyndEntry
import org.http4s.Uri

import scala.jdk.CollectionConverters._
import com.softinio.scalanews.algebra.Article
import com.softinio.scalanews.algebra.Blog

object Bloggers {
  private val directoryMarkdownFilePath =
    Paths.get("docs/Resources/Blog_Directory.md")
  def generateDirectory(bloggerList: List[Blog]): IO[String] = {
    IO.blocking {
      val header = """
      |# Blog Directory 

      |A Directory of bloggers producing Scala related content with links to their rss feed when available.

      || Blog        | URL           | RSS Feed  |
      || ------------- |:-------------:| -----:|"""

      val footer = """
      |###### Got a Scala related blog? Add it to this Blog Directory!

      |See [README](https://github.com/softinio/scalanews/blob/main/README.md) for details."""

      val directory = bloggerList.map { blog =>
        s"|| ${blog.name} | <${blog.url}> | [rss feed](${blog.rss}) |"
      }

      s"""
      $header
      ${directory.mkString("\n")}
      $footer\n""".stripMargin
    }
  }

  private def getArticlesFromEntries(
      entries: List[SyndEntry],
      startDate: Date,
      endDate: Date
  ): Option[List[Article]] =
    entries
      .filter(_.getPublishedDate != null)
      .filter(_.getLink != null)
      .filter(_.getTitle != null)
      .map { entry =>
        Article(
          entry.getTitle,
          Uri
            .fromString(entry.getLink)
            .getOrElse(Uri.unsafeFromString("https://www.scala-lang.org/")),
          entry.getPublishedDate
        )
      }
      .filter { case Article(_, _, publishedDate) =>
        publishedDate.after(startDate) && publishedDate.before(endDate)
      }
      .distinct
      .sortBy(_.publishedDate.getTime)
      .reverse match {
      case Nil  => None
      case list => Some(list)
    }

  def getArticlesForBlogger(
      blog: Blog,
      startDate: Date,
      endDate: Date
  ): IO[Option[List[Article]]] =
    for {
      feedResult <- Rome.fetchFeed(blog.rss.toURL.toString)
    } yield {
      getArticlesFromEntries(
        feedResult
          .map(_.getEntries.asScala.toList)
          .getOrElse(List[SyndEntry]()),
        startDate,
        endDate
      )
    }

  def createBlogList(startDate: Date, endDate: Date): IO[List[Article]] =
    ConfigLoader.load().flatMap { conf =>
      conf.bloggers.foldLeft(IO.pure(List[Article]()))((acc, blog) =>
        acc.flatMap { articleList =>
          getArticlesForBlogger(blog, startDate, endDate).map {
            maybeArticleList =>
              articleList ++ maybeArticleList.getOrElse(List[Article]())
          }
        }
      )
    }

  def createBloggerDirectory(bloggerList: List[Blog]): IO[ExitCode] = {
    for {
      exists <- Files[IO].exists(directoryMarkdownFilePath)
      _ <- if (exists) Files[IO].delete(directoryMarkdownFilePath) else IO.unit
      directory <- generateDirectory(bloggerList)
      _ <- Files[IO].write(
        directoryMarkdownFilePath,
        directory.getBytes(),
        StandardOpenOption.CREATE_NEW
      )
    } yield ExitCode.Success
  }
}
