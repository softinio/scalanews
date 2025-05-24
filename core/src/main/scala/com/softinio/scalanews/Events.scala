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
import fs2.io.file.*

import com.softinio.scalanews.algebra.Event
import com.softinio.scalanews.algebra.EventType
import com.softinio.scalanews.algebra.Location

object Events {
  private val directoryMarkdownFilePath =
    Path("docs/Resources/Event_Directory.md")

  private def printLocations(locations: List[Location]): String = {
    locations
      .map { location =>
        location.state match {
          case Some(state) =>
            s"- ${location.city}, ${state}, ${location.country}\n"
          case None => s"- ${location.city}, ${location.country}\n"
        }
      }
      .mkString("\n")
  }

  private def generateEvent(event: Event): String = {
    s"""
      |### ${event.name}
      |
      |${event.description}
      |
      |##### Links
      |
      |**Meetup:** <${event.meetupUrl.getOrElse("N/A")}>
      |
      |**Lu.ma:** <${event.lumaUrl.getOrElse("N/A")}>
      |
      |**Social Media:** <${event.socialMediaUrl.getOrElse("N/A")}>
      |
      |**Other:** <${event.otherUrl.getOrElse("N/A")}>
      |
      |##### Location(s)
      |
      |${printLocations(event.locations)}
      |""".stripMargin
  }

  def generateDirectory(
      eventList: List[Event],
      eventType: EventType
  ): IO[String] = {
    IO.blocking {

      val directory = eventList.map(generateEvent)

      s"${directory.mkString("\n")}".stripMargin
    }
  }

  def cleanEventDirectory(): IO[ExitCode] = {
    for {
      exists <- Files[IO].exists(directoryMarkdownFilePath)
      _ <- if (exists) Files[IO].delete(directoryMarkdownFilePath) else IO.unit
    } yield ExitCode.Success
  }

  def addTopHeader(): IO[ExitCode] = {
    val header = IO.blocking {
      s"""
      |# Meetups and Conferences
      \n
      """.stripMargin
    }

    for {
      exists <- Files[IO].exists(directoryMarkdownFilePath)
      headerValue <- header
      _ <- fs2.Stream
        .emits(List(headerValue))
        .through(fs2.text.utf8.encode)
        .through(
          if (exists)
            Files[IO].writeAll(directoryMarkdownFilePath, Flags(Flag.Append))
          else
            Files[IO].writeAll(directoryMarkdownFilePath, Flags(Flag.Create))
        )
        .compile
        .drain
    } yield ExitCode.Success
  }
  def addHeader(eventType: EventType): IO[ExitCode] = {
    val header = IO.blocking {
      s"""
      |## ${eventType.toString} Directory
      \n
      """.stripMargin
    }

    for {
      headerValue <- header
      _ <- fs2.Stream
        .emits(List(headerValue))
        .through(fs2.text.utf8.encode)
        .through(
          Files[IO].writeAll(directoryMarkdownFilePath, Flags(Flag.Append))
        )
        .compile
        .drain
    } yield ExitCode.Success
  }

  def addFooter(): IO[ExitCode] = {
    val footer = IO.blocking {
      s"""
      |###### Do you run a Scala related conference or meetup? Add it to this Directory!

      |See [README](https://github.com/softinio/scalanews/blob/main/README.md) for details.\n
      """.stripMargin
    }

    for {
      footerValue <- footer
      _ <- fs2.Stream
        .emits(List(footerValue))
        .through(fs2.text.utf8.encode)
        .through(
          Files[IO].writeAll(directoryMarkdownFilePath, Flags(Flag.Append))
        )
        .compile
        .drain
    } yield ExitCode.Success
  }

  def createEventDirectory(
      eventList: List[Event],
      eventType: EventType
  ): IO[ExitCode] = {
    for {
      exists <- Files[IO].exists(directoryMarkdownFilePath)
      directory <- generateDirectory(eventList, eventType)
      _ <- fs2.Stream
        .emits(List(directory))
        .through(fs2.text.utf8.encode)
        .through(
          if (exists)
            Files[IO].writeAll(directoryMarkdownFilePath, Flags(Flag.Append))
          else
            Files[IO].writeAll(directoryMarkdownFilePath, Flags(Flag.Create))
        )
        .compile
        .drain
    } yield ExitCode.Success
  }
}
