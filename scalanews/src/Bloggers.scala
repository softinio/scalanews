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
import com.softinio.verdict4s.{
  Ask,
  Verdict4sClient,
  Verdict4sEnv,
  Verdict4sError
}
import com.softinio.verdict4s.algebra.{ApiFailure, Probability, Question}
import io.circe.Encoder

import scala.jdk.CollectionConverters.*
import com.softinio.scalanews.algebra.Article
import com.softinio.scalanews.algebra.ArticleSummary
import com.softinio.scalanews.algebra.{AiMode, GenerateMode}
import com.softinio.scalanews.algebra.Blog
import com.softinio.scalanews.db.Database
import com.softinio.duck4s.DuckDBConnection
import com.softinio.scalanews.db.tables.{
  ArticleRepository,
  ArticleRow,
  ArticleSchema,
  StoredRelevance,
  StoredSummary
}

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

  /** What jev sees when judging an article's relevance: its title and the start
    * of its stored text.
    */
  private[scalanews] final case class RelevanceContext(
      title: String,
      text: String
  ) derives Encoder.AsObject

  private val relevanceTextChars = 2000

  // jev calls made at once; failed checks keep the article.
  private val relevanceConcurrency = 4

  // jev answers each question with P(yes). Excluding a real Scala post loses it
  // silently while including an off-topic one only costs a reader a skip, so
  // both thresholds lean towards keeping articles. Calibrated on real feeds:
  // off-topic posts scored at most 0.12 for "about Scala" while Scala
  // community news (e.g. Typelevel governance posts) scored 0.41-0.66; release
  // announcements scored 0.84-0.94 for "just an announcement" and substantive
  // posts at most 0.31.
  private[scalanews] val aboutScalaThreshold = 0.3
  private[scalanews] val announcementThreshold = 0.7

  // Both questions go to jev in a single request.
  private[scalanews] val relevanceQuestions = Ask(
    (
      Question.noul(
        "Is this article about the Scala programming language or its ecosystem?",
        "It is mainly about Scala itself or Scala libraries, frameworks, tools or build tools (such as sbt, Mill or Scala CLI).",
        "It is mainly about something else, and mentions Scala only in passing or not at all."
      ),
      Question.noul(
        "Is this article just an announcement of a new version of a Scala library or application?",
        "It only announces a release: a version number, a list of changes or release notes, with little else.",
        "It has substance beyond announcing a release, such as explanation, tutorial, design discussion or opinion."
      )
    )
  )

  /** Relevant when jev judged it about Scala and not just a release
    * announcement. Decided from stored probabilities, so retuning the
    * thresholds needs no new jev calls.
    */
  private[scalanews] def relevant(
      aboutScala: Probability,
      announcement: Probability
  ): Boolean =
    (aboutScala.value: Double) >= aboutScalaThreshold &&
      (announcement.value: Double) < announcementThreshold

  private[scalanews] def relevant(stored: StoredRelevance): Boolean =
    relevant(stored.aboutScala, stored.announcement)

  /** jev's answers to both relevance questions for a stored article. */
  private[scalanews] def assessRelevance(
      row: ArticleRow,
      client: Verdict4sClient[IO]
  ): IO[StoredRelevance] =
    client
      .ask(
        relevanceQuestions,
        RelevanceContext(row.title, row.content.take(relevanceTextChars))
      )
      .map((aboutScala, announcement) =>
        StoredRelevance(
          aboutScala.value,
          announcement.value,
          client.config.model.name
        )
      )

  /** Errors every jev call would hit (bad or missing key, no permission), so a
    * run should stop instead of keeping articles one by one.
    */
  private[scalanews] def isFatalVerdict(error: Throwable): Boolean =
    error match {
      case api: Verdict4sError.Api =>
        api.failure == ApiFailure.Unauthorized ||
        api.failure == ApiFailure.Forbidden
      case _: Verdict4sError.Invalid                    => true
      case e if e.getCause != null && (e.getCause ne e) =>
        isFatalVerdict(e.getCause)
      case _ => false
    }

  /** Adds a pointer to `--no-ai` when verdict4s reports the API key missing. */
  private[scalanews] val missingTypesafeKeyHint
      : PartialFunction[Throwable, Throwable] = {
    case e: Verdict4sError.Validation if e.field == "TYPESAFE_API_KEY" =>
      new RuntimeException(
        "TYPESAFE_API_KEY is not set: set it for jev relevance checks, or use --no-ai",
        e
      )
  }

  /** One verdict4s client for the run, configured from the environment:
    * TYPESAFE_API_KEY, and optionally TYPESAFE_DEFAULT_MODEL (jev-latest by
    * default) and TYPESAFE_BASE_URL.
    */
  private def verdictClient: Resource[IO, Verdict4sClient[IO]] =
    Verdict4sEnv.default[IO].adaptError(missingTypesafeKeyHint)

  /** The rows jev judges relevant, reusing stored answers unless `refresh` and
    * storing new ones. jev is only contacted (and TYPESAFE_API_KEY only needed)
    * when some row has no stored answer. A failed check keeps the article and
    * isn't stored, so it's retried next run.
    */
  private def filterRelevant(
      conn: DuckDBConnection,
      rows: List[ArticleRow],
      refresh: Boolean
  ): IO[List[ArticleRow]] = {
    val reusable: ArticleRow => Option[StoredRelevance] =
      row => if (refresh) None else row.storedRelevance
    val toCheck = rows.filter(reusable(_).isEmpty)

    val fresh: IO[Map[java.util.UUID, Option[StoredRelevance]]] =
      if (toCheck.isEmpty) IO.pure(Map.empty)
      else
        verdictClient
          .use(client =>
            // jev calls run concurrently; database writes below run one at a
            // time on the shared connection.
            toCheck.parTraverseN(relevanceConcurrency)(row =>
              assessRelevance(row, client).attempt.flatMap {
                case Left(error) if isFatalVerdict(error) =>
                  IO.raiseError(
                    new RuntimeException(
                      "Typesafe rejected the request; check TYPESAFE_API_KEY and its permissions",
                      error
                    )
                  )
                case result => IO.pure(row -> result)
              }
            )
          )
          .flatMap(_.traverse {
            case (row, Right(stored)) =>
              ArticleRepository
                .saveRelevance(conn, row.id, stored)
                .as(row.id -> Some(stored))
            case (row, Left(error)) =>
              IO.println(
                s"Relevance check failed for '${row.title}', keeping it: ${error.getMessage}"
              ).as(row.id -> None)
          })
          .map(_.toMap)

    for {
      checked <- fresh
      kept <- rows.traverseFilter { row =>
        reusable(row).orElse(checked.getOrElse(row.id, None)) match {
          case Some(stored) if !relevant(stored) =>
            IO.println(
              f"Not relevant: '${row.title}' (P(about Scala)=${stored.aboutScala.value: Double}%.2f, P(announcement)=${stored.announcement.value: Double}%.2f)"
            ).as(None)
          case _ => IO.pure(Some(row))
        }
      }
      _ <- IO.println(
        s"Checked ${toCheck.size} articles with jev, reused ${rows.size - toCheck.size} stored; ${rows.size - kept.size} not relevant"
      )
    } yield kept
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

  /** Fetches the feeds for the date range and stores new articles, skipping
    * ones already stored.
    */
  private def ingestInto(
      conn: DuckDBConnection,
      startDate: Date,
      endDate: Date
  ): IO[Unit] =
    for {
      articleList <- createBlogList(startDate, endDate)
      inserted <- articleList.traverse(ArticleRepository.insert(conn, _))
      added = inserted.sum
      _ <- IO.println(
        s"Ingested $added new articles (${articleList.size - added} already stored)"
      )
    } yield ()

  def ingestBlogsToDB(
      startDate: Date,
      endDate: Date,
      dbPath: String = Database.defaultPath
  ): IO[ExitCode] =
    Database
      .connect(dbPath, Seq(ArticleSchema))
      .use(ingestInto(_, startDate, endDate))
      .as(ExitCode.Success)

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

  /** The stored form of a summarisation outcome Claude decided; `None` for
    * outcomes that shouldn't be stored (no text is decided locally, and
    * failures should be retried).
    */
  private[scalanews] def storedFor(
      outcome: ArticleSummariser.Summarisation,
      model: String
  ): Option[StoredSummary] = {
    import ArticleSummariser.{NoSummaryReason, Summarisation}
    outcome match {
      case Summarisation.Summarised(summary) =>
        Some(StoredSummary.Summarised(summary, model))
      case Summarisation.NoSummary(NoSummaryReason.InsufficientContent(r)) =>
        Some(StoredSummary.NoSummary(r, model))
      case Summarisation.NoSummary(NoSummaryReason.NoText) |
          Summarisation.Failed(_) =>
        None
    }
  }

  /** The summary to show for a stored outcome. */
  private[scalanews] def summaryOf(
      stored: StoredSummary
  ): Option[ArticleSummary] =
    stored match {
      case StoredSummary.Summarised(summary, _) => Some(summary)
      case StoredSummary.NoSummary(_, _)        => None
    }

  private def toArticle(row: ArticleRow): Article =
    Article(
      row.title,
      row.content,
      row.url.flatMap(u => org.http4s.Uri.fromString(u).toOption),
      row.author,
      row.publishedDate
    )

  /** Summaries for `rows`, reusing stored outcomes unless `refresh`, and
    * storing new ones. Claude is only contacted (and the API key only needed)
    * when some article has no stored outcome.
    */
  private def summariseWithClaude(
      conn: DuckDBConnection,
      rows: List[ArticleRow],
      refresh: Boolean
  ): IO[List[(Article, Option[ArticleSummary])]] = {
    val reusable: ArticleRow => Option[StoredSummary] =
      row => if (refresh) None else row.storedSummary
    val toSummarise = rows.filter(reusable(_).isEmpty)

    val fresh: IO[Map[java.util.UUID, Option[ArticleSummary]]] =
      if (toSummarise.isEmpty) IO.pure(Map.empty)
      else
        ConfigLoader.loadAnthropicConfig().flatMap { config =>
          AnthropicClient
            .resource(config)
            .use(client =>
              // Claude calls run concurrently; database writes below run
              // one at a time on the shared connection.
              toSummarise.parTraverseN(summaryConcurrency)(row =>
                ArticleSummariser
                  .summarise(toArticle(row), client)
                  .map(row -> _)
              )
            )
            .flatMap(_.traverse { (row, outcome) =>
              storedFor(outcome, config.model.asString)
                .traverse_(ArticleRepository.saveSummary(conn, row.id, _)) >>
                summaryFor(toArticle(row), outcome).map(row.id -> _)
            })
            .map(_.toMap)
        }

    for {
      summaries <- fresh
      _ <- IO.println(
        s"Summarised ${toSummarise.size} articles with Claude, reused ${rows.size - toSummarise.size} stored"
      )
    } yield rows.map(row =>
      toArticle(row) -> reusable(row).fold(summaries(row.id))(summaryOf)
    )
  }

  /** Writes the next newsletter draft, replacing any existing one. */
  private def writeNextNewsletter(
      articles: List[(Article, Option[ArticleSummary])]
  ): IO[Unit] =
    for {
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
    } yield ()

  /** Builds the next newsletter for the date range. */
  def generate(
      startDate: Date,
      endDate: Date,
      mode: GenerateMode
  ): IO[ExitCode] =
    mode match {
      case GenerateMode.Direct =>
        createBlogList(startDate, endDate)
          .map(_.map(a => a -> simpleSummary(a.content)))
          .flatMap(writeNextNewsletter)
          .as(ExitCode.Success)

      case GenerateMode.Database(dbPath, ai) =>
        Database.connect(dbPath, Seq(ArticleSchema)).use { conn =>
          for {
            _ <- ingestInto(conn, startDate, endDate)
            rows <- ArticleRepository
              .findAll(conn)
              .filter(row =>
                row.publishedDate.after(startDate) &&
                  row.publishedDate.before(endDate)
              )
              .compile
              .toList
            articles <- ai match {
              case AiMode.Enabled(refresh) =>
                filterRelevant(conn, rows, refresh)
                  .flatMap(summariseWithClaude(conn, _, refresh))
              case AiMode.Disabled =>
                IO.pure(
                  rows.map(row => toArticle(row) -> simpleSummary(row.content))
                )
            }
            _ <- writeNextNewsletter(articles)
          } yield ExitCode.Success
        }
    }
}
