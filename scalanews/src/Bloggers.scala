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
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.io.file.*
import com.rometools.rome.feed.synd.{SyndContent, SyndEntry}

import scala.jdk.CollectionConverters.*
import com.softinio.scalanews.algebra.Article
import com.softinio.scalanews.algebra.ArticleSummary
import com.softinio.scalanews.algebra.Blog
import com.softinio.scalanews.db.Database
import com.softinio.scalanews.db.tables.{ArticleRepository, ArticleSchema}

object Bloggers {
  private val nextMarkdownFilePath =
    Path("next/next.md")
  private val directoryMarkdownFilePath =
    Path("docs/Resources/Blog_Directory.md")
  // Claude calls made at once when summarising; failed calls (e.g. rate
  // limits) fall back to the plain summary.
  private val summaryConcurrency = 4
  private val blogsToSkipByUrl = List(
    "petr-zapletal.medium.com",
    "sudarshankasar.medium.com"
  )
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

  private[scalanews] def simpleSummary(
      content: String
  ): Option[ArticleSummary] =
    ArticleSummary.from(
      content.linesIterator
        .map(_.trim)
        .filterNot(l => l.startsWith("#") || l.isEmpty)
        .mkString(" ")
    )

  // Articles shown as cards; the rest are listed compactly under "More articles".
  private[scalanews] val highlightCount = 18

  private def escapeHtml(text: String): String =
    text.flatMap {
      case '&'  => "&amp;"
      case '<'  => "&lt;"
      case '>'  => "&gt;"
      case '"'  => "&quot;"
      case '\'' => "&#39;"
      case c    => c.toString
    }

  private[scalanews] def generateNews(
      articles: List[(Article, Option[ArticleSummary])]
  ): IO[String] = {
    IO.blocking {
      val header =
        """|# Scala News
           |
           |A curated list of Scala related news from the community.
           |
           |## Articles""".stripMargin

      def link(article: Article): String = {
        val url = escapeHtml(article.url.map(_.toString).getOrElse("#"))
        s"""<a href="$url">${escapeHtml(article.title)}</a>"""
      }

      def card(article: Article, summary: Option[ArticleSummary]): String = {
        val summaryLine = summary.fold("")(s =>
          s"""\n  <p class="article-summary">${escapeHtml(s.value)}</p>"""
        )
        s"""|<div class="article-card">
            |  <h3>${link(article)}</h3>
            |  <span class="article-author">${escapeHtml(
             article.author
           )}</span>$summaryLine
            |</div>""".stripMargin
      }

      def listItem(article: Article): String =
        s"""  <li>${link(article)} <span class="article-author">${escapeHtml(
            article.author
          )}</span></li>"""

      // Articles with a summary get the cards first; order within each part is by title.
      val (summarised, unsummarised) =
        articles.sortBy(_._1.title).partition(_._2.isDefined)
      val (highlights, rest) =
        (summarised ++ unsummarised).splitAt(highlightCount)

      val cards =
        s"""|<div class="article-cards">
            |${highlights.map(card.tupled).mkString("\n")}
            |</div>""".stripMargin

      val moreArticles =
        if (rest.isEmpty) ""
        else
          s"""|
              |
              |### More articles
              |
              |<ul class="more-articles">
              |${rest.map(_._1).sortBy(_.title).map(listItem).mkString("\n")}
              |</ul>""".stripMargin

      s"""|$header
          |
          |$cards$moreArticles
          |""".stripMargin
    }
  }

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

  def getArticlesForBlogger(
      blog: Blog,
      startDate: Date,
      endDate: Date
  ): IO[Option[List[Article]]] =
    for {
      feedResult <- Rome.fetchFeed(blog.rss.toURL.toString)
      result <- feedResult match {
        case Left(exception) =>
          IO.println(
            s"Error fetching feed for blog ${blog.name}: ${exception.getMessage}"
          ) *> IO.pure(None)
        case Right(feed) =>
          IO.pure(
            getArticlesFromEntries(
              blog,
              feed.getEntries.asScala.toList,
              startDate,
              endDate
            )
          )
      }
    } yield result

  def createBlogList(
      startDate: Date,
      endDate: Date,
      configFilePath: String = "config.json"
  ): IO[List[Article]] =
    ConfigLoader.load(configFilePath).flatMap { conf =>
      createBlogListFromBloggers(conf.bloggers, startDate, endDate)
    }

  def createBlogListFromBloggers(
      bloggers: List[Blog],
      startDate: Date,
      endDate: Date
  ): IO[List[Article]] =
    bloggers.foldLeft(IO.pure(List[Article]()))((acc, blog) =>
      acc.flatMap { articleList =>
        getArticlesForBlogger(blog, startDate, endDate).map {
          maybeArticleList =>
            articleList ++ maybeArticleList.getOrElse(List[Article]())
        }
      }
    )

  def createBloggerDirectory(bloggerList: List[Blog]): IO[ExitCode] = {
    for {
      exists <- Files[IO].exists(directoryMarkdownFilePath)
      _ <- if (exists) Files[IO].delete(directoryMarkdownFilePath) else IO.unit
      directory <- generateDirectory(bloggerList)
      _ <- fs2.Stream
        .emits(List(directory))
        .through(fs2.text.utf8.encode)
        .through(
          Files[IO].writeAll(directoryMarkdownFilePath, Flags(Flag.CreateNew))
        )
        .compile
        .drain
    } yield ExitCode.Success
  }

  def ingestBlogsToDB(
      startDate: Date,
      endDate: Date,
      dbPath: String = Database.defaultPath
  ): IO[ExitCode] =
    Database.connect(dbPath, Seq(ArticleSchema)).use { conn =>
      for {
        articleList <- createBlogList(startDate, endDate)
        _ <- articleList
          .traverse_(article => ArticleRepository.insert(conn, article))
      } yield ExitCode.Success
    }

  /** Picks the summary to show for an AI summarisation outcome, logging why an
    * article didn't get a Claude summary.
    */
  private[scalanews] def summaryFor(
      article: Article,
      outcome: ArticleSummariser.Summarisation
  ): IO[Option[ArticleSummary]] = {
    import ArticleSummariser.{NoSummaryReason, Summarisation}
    outcome match {
      case Summarisation.Summarised(summary) => IO.pure(Some(summary))
      case Summarisation.NoSummary(NoSummaryReason.NoText) =>
        IO.println(s"No summary for '${article.title}': article has no text")
          .as(None)
      case Summarisation.NoSummary(
            NoSummaryReason.InsufficientContent(reason)
          ) =>
        IO.println(s"No summary for '${article.title}': $reason").as(None)
      case Summarisation.Failed(error) =>
        IO.println(
          s"Summarisation failed for '${article.title}', using plain summary: ${error.getMessage}"
        ).as(simpleSummary(article.content))
    }
  }

  def generateNextBlogUsingDB(
      startDate: Date,
      endDate: Date,
      dbPath: String,
      aI: Boolean
  ): IO[ExitCode] =
    Database.connect(dbPath, Seq(ArticleSchema)).use { conn =>
      for {
        articleList <- ArticleRepository
          .findAll(conn)
          .filter(row =>
            row.publishedDate.after(startDate) && row.publishedDate.before(
              endDate
            )
          )
          .map(row =>
            Article(
              row.title,
              row.content,
              row.url.flatMap(u => org.http4s.Uri.fromString(u).toOption),
              row.author,
              row.publishedDate
            )
          )
          .compile
          .toList
        articles <-
          if (aI)
            ConfigLoader
              .loadAnthropicConfig()
              .flatMap(config =>
                AnthropicClient
                  .resource(config)
                  .use(client =>
                    articleList.parTraverseN(summaryConcurrency)(a =>
                      ArticleSummariser
                        .summarise(a, client)
                        .flatMap(outcome => summaryFor(a, outcome))
                        .map(a -> _)
                    )
                  )
              )
          else
            IO.pure(articleList.map(a => a -> simpleSummary(a.content)))
        exists <- Files[IO].exists(nextMarkdownFilePath)
        _ <- if (exists) Files[IO].delete(nextMarkdownFilePath) else IO.unit
        news <- generateNews(articles)
        _ <- fs2.Stream
          .emits(List(news))
          .through(fs2.text.utf8.encode)
          .through(
            Files[IO].writeAll(nextMarkdownFilePath, Flags(Flag.CreateNew))
          )
          .compile
          .drain
      } yield ExitCode.Success
    }

  def generateNextBlog(
      startDate: Date,
      endDate: Date
  ): IO[ExitCode] = {
    for {
      exists <- Files[IO].exists(nextMarkdownFilePath)
      _ <- if (exists) Files[IO].delete(nextMarkdownFilePath) else IO.unit
      articleList <- createBlogList(startDate, endDate)
      news <- generateNews(articleList.map(a => a -> simpleSummary(a.content)))
      _ <- fs2.Stream
        .emits(List(news))
        .through(fs2.text.utf8.encode)
        .through(
          Files[IO].writeAll(nextMarkdownFilePath, Flags(Flag.CreateNew))
        )
        .compile
        .drain
    } yield ExitCode.Success
  }
}
