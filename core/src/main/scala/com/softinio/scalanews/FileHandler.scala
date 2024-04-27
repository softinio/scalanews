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

import java.time.format.DateTimeFormatter.BASIC_ISO_DATE
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.LocalDate

import fs2.text
import cats.effect._
import fs2.io.file._

object FileHandler {
  private val nextFilePath = Path("next/next.md")
  private val templateFilePath = Path("next/template.md")
  private val indexFilePath = Path("docs/index.md")

  def updateFileHeader(
      sourceFile: Path,
      headerDate: LocalDate
  ): IO[Either[Throwable, Path]] = {
    val HEADER_TEXT = "# Scala News"
    val headerDateString =
      headerDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    val updatedHeader = s"$HEADER_TEXT - $headerDateString"

    Files[IO].tempFile().use { tempFile =>
      val usingTempFile = Path.fromNioPath(tempFile)
      val updatedContent = for {
        _ <- Files[IO]
          .readAll(sourceFile)
          .through(text.utf8.decode)
          .through(text.lines)
          .map(line =>
            if (line.startsWith(HEADER_TEXT)) updatedHeader else line
          )
          .intersperse("\n")
          .through(text.utf8.encode)
          .through(Files[IO].writeAll(usingTempFile))
          .compile
          .drain
        _ <- Files[IO]
          .move(usingTempFile, sourceFile, CopyFlags(CopyFlag.ReplaceExisting))
      } yield sourceFile

      updatedContent.attempt
    }
  }

  def getArchiveDate(archiveDate: String): IO[Either[Throwable, LocalDate]] =
    IO.blocking {
      LocalDate.parse(archiveDate, BASIC_ISO_DATE)
    }.attempt

  def getPublishDate(
      publishDate: Option[String]
  ): IO[Either[Throwable, LocalDate]] =
    IO.blocking {
      publishDate match {
        case Some(aDate) => LocalDate.parse(aDate, BASIC_ISO_DATE)
        case None        => LocalDate.now()
      }
    }.attempt

  def create(overwrite: Boolean): IO[ExitCode] =
    for {
      exists <- Files[IO].exists(nextFilePath)
      _ <-
        if (exists && overwrite)
          Files[IO].copy(
            templateFilePath,
            nextFilePath,
            CopyFlags(CopyFlag.ReplaceExisting)
          )
        else if (!exists) Files[IO].copy(templateFilePath, nextFilePath)
        else IO.unit
    } yield (ExitCode.Success)

  private def createArchiveFolderPath(
      archiveFolder: Option[String]
  ): IO[String] = {
    IO.blocking {
      val folderPath = archiveFolder match {
        case Some(folder) => s"docs/Archive/${folder}/"
        case None         => s"docs/Archive/"
      }
      Files[IO].createDirectories(Path(folderPath))
      folderPath
    }
  }

  private def createArchiveFileName(archiveDate: String): IO[String] =
    for {
      aDate <- getArchiveDate(archiveDate)
      fileName <- aDate match {
        case Right(rDate) => IO(s"scala_news_${rDate}.md")
        case _            => IO("")
      }
    } yield (fileName)

  private def getArchivePath(
      archiveDate: String,
      archiveFolder: Option[String]
  ): IO[Path] =
    for {
      fileName <- createArchiveFileName(archiveDate)
      folderPath <- createArchiveFolderPath(archiveFolder)
    } yield (Path(s"${folderPath}${fileName}"))

  def publish(
      publishDate: Option[String],
      archiveDate: String,
      archiveFolder: Option[String]
  ): IO[ExitCode] =
    for {
      pDate <- getPublishDate(publishDate)
      newFilePath <- getArchivePath(archiveDate, archiveFolder)
      indexExists <- Files[IO].exists(indexFilePath)
      _ <-
        if (indexExists) Files[IO].move(indexFilePath, newFilePath) else IO.unit
      nextExists <- Files[IO].exists(nextFilePath)
      _ <-
        if (nextExists) {
          pDate match {
            case Right(lDate) => updateFileHeader(nextFilePath, lDate)
            case _            => IO.unit
          }
        } else IO.unit
      _ <-
        if (nextExists) Files[IO].move(nextFilePath, indexFilePath) else IO.unit
      _ <- create(false)
    } yield (ExitCode.Success)
}
