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

import cats.effect.*
import cats.implicits.*

import com.monovore.decline.*
import com.monovore.decline.effect.*

import com.softinio.scalanews.algebra.{
  AiMode,
  DateRange,
  EventType,
  GenerateMode
}
import com.softinio.scalanews.db.Database

object Main
    extends CommandIOApp(
      name = "scalanews",
      header = "scalanews cli",
      version = "0.2"
    ) {

  private case class Publish(
      publishDate: Option[String],
      archiveDate: String,
      archiveFolder: Option[String]
  )
  private case class Create(overwrite: Boolean)

  private case class Blogger(directory: Boolean)

  private case class Event(directory: Boolean)

  private case object SelfCheckCmd

  private[scalanews] case class Generate(range: DateRange, mode: GenerateMode)

  private case class IngestBlogs(range: DateRange, dbPath: String)

  private val archiveDateOps: Opts[String] =
    Opts
      .argument[String](metavar = "archiveDate")

  private val startDateOps: Opts[String] =
    Opts
      .argument[String](metavar = "startDate")

  private val endDateOps: Opts[String] =
    Opts
      .argument[String](metavar = "endDate")

  /** The start and end dates, validated when the command line is parsed. */
  private val dateRangeOps: Opts[DateRange] =
    (startDateOps, endDateOps).tupled.mapValidated((start, end) =>
      DateRange.parse(start, end).toValidatedNel
    )

  private val dbPathOps: Opts[String] =
    Opts
      .option[String]("dbpath", "Database file path", short = "d")
      .withDefault(Database.defaultPath)

  private val publishDateOps: Opts[Option[String]] =
    Opts
      .option[String](
        "publishdate",
        "Publish date for current newsletter to",
        short = "p"
      )
      .orNone

  private val archiveFolderOps: Opts[Option[String]] =
    Opts
      .option[String](
        "folder",
        "Folder name to archive current newsletter to",
        short = "f"
      )
      .orNone

  private val publishOpts: Opts[Publish] =
    Opts.subcommand("publish", "Publish next newsletter") {
      (publishDateOps, archiveDateOps, archiveFolderOps).mapN(Publish.apply)
    }

  private val createOpts: Opts[Create] =
    Opts.subcommand("create", "Create file for next newsletter edition") {
      Opts
        .flag("overwrite", "Overwrite next file if it exists", short = "o")
        .orFalse
        .map(Create.apply)
    }

  private val bloggerOpts: Opts[Blogger] =
    Opts.subcommand("blogger", "Blogger directory tasks") {
      Opts
        .flag("directory", "create a new blogger directory page", short = "d")
        .orFalse
        .map(Blogger.apply)
    }

  private val eventOpts: Opts[Event] =
    Opts.subcommand("event", "Event tasks") {
      Opts
        .flag("directory", "create a new event directory page", short = "e")
        .orFalse
        .map(Event.apply)
    }

  /** `generate`: by default ingests into the database and summarises with
    * Claude; `--no-ai` keeps the database without jev or Claude, `--no-db`
    * reads the feeds directly. Flag combinations that make no sense are
    * rejected rather than ignored.
    */
  private[scalanews] val generateOpts: Opts[Generate] =
    Opts.subcommand(
      "generate",
      "Generate the next newsletter (database and Claude summaries by default)"
    ) {
      (
        dateRangeOps,
        Opts
          .flag(
            "no-db",
            "Read the feeds directly instead of via the database (plain summaries)"
          )
          .orFalse,
        Opts
          .flag(
            "no-ai",
            "No jev relevance check or Claude summaries (keyword filter, plain summaries)"
          )
          .orFalse,
        Opts
          .flag(
            "refresh-ai",
            "Ask jev and Claude again even for articles with stored results",
            short = "r"
          )
          .orFalse,
        Opts
          .option[String]("dbpath", "Database file path", short = "d")
          .orNone
      ).tupled.mapValidated {
        case (_, true, _, true, _) =>
          "--refresh-ai needs the database; it can't be used with --no-db".invalidNel
        case (_, true, _, _, Some(_)) =>
          "--dbpath can't be used with --no-db".invalidNel
        case (_, false, true, true, _) =>
          "--refresh-ai only applies with AI; it can't be used with --no-ai".invalidNel
        case (range, noDb, noAi, refresh, dbPath) =>
          val mode =
            if (noDb) GenerateMode.Direct
            else
              GenerateMode.Database(
                dbPath.getOrElse(Database.defaultPath),
                if (noAi) AiMode.Disabled else AiMode.Enabled(refresh)
              )
          Generate(range, mode).validNel
      }
    }

  private val ingestBlogsOpts: Opts[IngestBlogs] =
    Opts.subcommand("ingest", "Ingest blogs into DB") {
      (dateRangeOps, dbPathOps).mapN(IngestBlogs.apply)
    }

  private val selfCheckOpts: Opts[SelfCheckCmd.type] =
    Opts.subcommand(
      "self-check",
      "Check Claude request/reply JSON handling offline"
    )(Opts.unit.as(SelfCheckCmd))

  /** Expected, user-fixable failures end the run with one `error:` line and
    * exit code 1; anything else is unexpected and keeps its stack trace. (Bad
    * dates are rejected earlier, when the command line is parsed.)
    */
  private[scalanews] val reportUserErrors
      : PartialFunction[Throwable, IO[ExitCode]] = {
    case e: UserError => Output.error(e.getMessage).as(ExitCode.Error)
    case e: pureconfig.error.ConfigReaderException[?] =>
      Output
        .error(s"invalid configuration:\n${e.failures.prettyPrint()}")
        .as(ExitCode.Error)
  }

  override def main: Opts[IO[ExitCode]] =
    (publishOpts orElse createOpts orElse generateOpts orElse ingestBlogsOpts orElse bloggerOpts orElse eventOpts orElse selfCheckOpts)
      .map(command =>
        IO.defer(runCommand(command)).recoverWith(reportUserErrors)
      )

  private def runCommand(command: Product): IO[ExitCode] =
    command match {
      case SelfCheckCmd                                     => SelfCheck.run
      case Publish(publishDate, archiveDate, archiveFolder) =>
        FileHandler.publish(publishDate, archiveDate, archiveFolder)
      case Create(overwrite)          => FileHandler.create(overwrite)
      case Generate(range, mode)      => Newsletter.generate(range, mode)
      case IngestBlogs(range, dbPath) =>
        Newsletter.ingestBlogsToDB(range, dbPath)
      case Blogger(directory) =>
        if (directory) {
          for {
            config <- ConfigLoader.load()
            result <- BlogDirectory.createBloggerDirectory(config.bloggers)
          } yield result
        } else IO(ExitCode.Success)
      case Event(directory) =>
        if (directory) {
          for {
            config <- ConfigLoader.loadEventsConfig()
            _ <- Events.cleanEventDirectory()
            _ <- Events.addTopHeader()
            _ <- Events.addHeader(EventType.Meetup)
            _ <- Events.createEventDirectory(config.meetups, EventType.Meetup)
            _ <- Events.addHeader(EventType.Conference)
            _ <- Events.createEventDirectory(
              config.conferences,
              EventType.Conference
            )
            _ <- Events.addFooter()
          } yield ExitCode.Success
        } else IO(ExitCode.Success)
      case other =>
        IO.raiseError(new IllegalStateException(s"Unhandled command: $other"))
    }
}
