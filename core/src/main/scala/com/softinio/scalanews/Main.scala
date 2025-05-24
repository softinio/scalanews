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

import java.text.SimpleDateFormat

import cats.effect.*
import cats.implicits.*

import com.monovore.decline.*
import com.monovore.decline.effect.*

import com.softinio.scalanews.algebra.EventType

object Main
    extends CommandIOApp(
      name = "scalanews",
      header = "scalanews cli",
      version = "0.1"
    ) {

  private case class Publish(
      publishDate: Option[String],
      archiveDate: String,
      archiveFolder: Option[String]
  )
  private case class Create(overwrite: Boolean)

  private case class Blogger(directory: Boolean)

  private case class Event(directory: Boolean)

  private case class GenerateNextBlog(
      startDate: String,
      endDate: String
  )

  private case class ServerCmd(port: Int)

  private val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")

  private val archiveDateOps: Opts[String] =
    Opts
      .argument[String](metavar = "archiveDate")

  private val startDateOps: Opts[String] =
    Opts
      .argument[String](metavar = "startDate")

  private val endDateOps: Opts[String] =
    Opts
      .argument[String](metavar = "endDate")

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

  private val generateNextBlogOpts: Opts[GenerateNextBlog] =
    Opts.subcommand("generate", "Generate next blog") {
      (startDateOps, endDateOps).mapN(GenerateNextBlog.apply)
    }

  private val serverOpts: Opts[ServerCmd] =
    Opts.subcommand("server", "Start HTTP server") {
      Opts
        .option[Int]("port", "Port to bind server to", short = "p")
        .withDefault(8080)
        .map(ServerCmd.apply)
    }

  override def main: Opts[IO[ExitCode]] =
    (publishOpts orElse createOpts orElse generateNextBlogOpts orElse bloggerOpts orElse eventOpts orElse serverOpts)
      .map {
        case Publish(publishDate, archiveDate, archiveFolder) =>
          FileHandler.publish(publishDate, archiveDate, archiveFolder)
        case Create(overwrite) => FileHandler.create(overwrite)
        case GenerateNextBlog(startDate, endDate) =>
          Bloggers.generateNextBlog(
            dateFormatter.parse(startDate),
            dateFormatter.parse(endDate)
          )
        case Blogger(directory) =>
          if (directory) {
            for {
              config <- ConfigLoader.load()
              result <- Bloggers.createBloggerDirectory(config.bloggers)
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
        case ServerCmd(port) =>
          Server.run(port)
      }
}
