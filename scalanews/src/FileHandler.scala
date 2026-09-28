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
import java.time.LocalDate
import java.util.Locale

import fs2.text
import cats.effect.*
import cats.syntax.all.*
import fs2.io.file.*

object FileHandler {
  private val nextFilePath = Path("next/next.md")
  private val templateFilePath = Path("next/template.md")
  private[scalanews] val indexFilePath = Path("docs/index.md")

  private val HEADER_TEXT = "# Scala News"

  // The date in an edition's heading, "# Scala News - November 24, 2024".
  private val headerDateFormat =
    DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)

  def updateFileHeader(
      sourceFile: Path,
      headerDate: LocalDate
  ): IO[Either[Throwable, Path]] = {
    val headerDateString = headerDate.format(headerDateFormat)
    val updatedHeader = s"$HEADER_TEXT - $headerDateString"

    val tempFilePath: Resource[IO, Path] =
      Files[IO].tempFile

    tempFilePath.use { usingTempFile =>
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
    } yield ExitCode.Success

  /** Where the current edition is archived: `docs/Archive/<year>/` by default,
    * so the site's sidebar groups it under that year, or
    * `docs/Archive/<folder>/` when a folder is given. The folder is created if
    * needed.
    */
  private[scalanews] def getArchivePath(
      archiveDate: LocalDate,
      archiveFolder: Option[String],
      archiveRoot: Path = Path("docs/Archive")
  ): IO[Path] = {
    val folder =
      archiveRoot / archiveFolder.getOrElse(archiveDate.getYear.toString)
    Files[IO]
      .createDirectories(folder)
      .as(folder / s"scala_news_$archiveDate.md")
  }

  /** The date in an edition's heading, e.g. November 24, 2024 for "# Scala News -
    * November 24, 2024".
    */
  private[scalanews] def editionDate(edition: Path): IO[Option[LocalDate]] =
    Files[IO]
      .readAll(edition)
      .through(text.utf8.decode)
      .through(text.lines)
      .map(_.trim)
      .find(_.startsWith(s"$HEADER_TEXT - "))
      .compile
      .last
      .map(
        _.flatMap(line =>
          scala.util
            .Try(
              LocalDate.parse(
                line.stripPrefix(s"$HEADER_TEXT - ").trim,
                headerDateFormat
              )
            )
            .toOption
        )
      )

  /** The date to archive the current edition under: the one given, or else the
    * date in its heading.
    */
  private[scalanews] def archiveDateFor(
      archiveDate: Option[String],
      edition: Path = indexFilePath
  ): IO[LocalDate] =
    archiveDate match {
      case Some(date) =>
        getArchiveDate(date).flatMap(
          IO.fromEither(_)
            .adaptError(_ =>
              new UserError(
                s"invalid archive date '$date': expected yyyyMMdd, e.g. 20241124"
              )
            )
        )
      case None =>
        editionDate(edition).flatMap(
          IO.fromOption(_)(
            new UserError(
              s"no dated \"$HEADER_TEXT - <date>\" heading in $edition: pass the archive date (yyyyMMdd)"
            )
          )
        )
    }

  /** Makes `draft` the current edition (docs/index.md), dated `date`, after
    * archiving the current one under docs/Archive/<year>/ (or `archiveFolder`),
    * dated from its heading unless `archiveDate` is given. Returns where the
    * previous edition was archived, if there was one.
    */
  private[scalanews] def publishEdition(
      draft: Path,
      date: LocalDate,
      archiveDate: Option[String] = None,
      archiveFolder: Option[String] = None,
      index: Path = indexFilePath,
      archiveRoot: Path = Path("docs/Archive")
  ): IO[Option[Path]] =
    for {
      indexExists <- Files[IO].exists(index)
      archived <-
        if (indexExists)
          archiveDateFor(archiveDate, index)
            .flatMap(getArchivePath(_, archiveFolder, archiveRoot))
            .flatTap(Files[IO].move(index, _))
            .map(Some(_))
        else IO.pure(None)
      _ <- updateFileHeader(draft, date).flatMap(IO.fromEither)
      _ <- Files[IO].move(draft, index)
    } yield archived

  /** `publish`: makes the draft (next/next.md) the current edition, dated
    * `publishDate` (today by default), and starts a new draft.
    */
  def publish(
      publishDate: Option[String],
      archiveDate: Option[String],
      archiveFolder: Option[String]
  ): IO[ExitCode] =
    for {
      nextExists <- Files[IO].exists(nextFilePath)
      _ <- IO
        .raiseError(
          new UserError(s"nothing to publish: $nextFilePath not found")
        )
        .unlessA(nextExists)
      pDate <- getPublishDate(publishDate).flatMap(
        IO.fromEither(_)
          .adaptError(_ =>
            new UserError(
              s"invalid publish date '${publishDate.getOrElse("")}': expected yyyyMMdd, e.g. 20241124"
            )
          )
      )
      _ <- publishEdition(nextFilePath, pDate, archiveDate, archiveFolder)
      _ <- create(false)
    } yield ExitCode.Success
}
