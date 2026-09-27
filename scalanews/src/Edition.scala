/*
 * Copyright 2026 Salar Rahmanian
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
import cats.syntax.all.*
import fs2.io.file.{Files, Path}

import com.softinio.scalanews.algebra.{AiMode, DateRange, GenerateMode}

/** `edition`: the whole cycle for a new edition in one command. */
object Edition {

  /** Generates the edition for `range` from the database, with jev relevance
    * checks and Claude summaries, then archives the current edition (under
    * docs/Archive/<year>/, dated from its heading) and makes the new one the
    * home page, dated the range's last day.
    *
    * The page is generated into a temporary file, so the draft in next/next.md
    * is left alone, and nothing is published when the range has no articles or
    * isn't newer than the current edition.
    */
  def run(
      range: DateRange,
      dbPath: String,
      refresh: Boolean,
      services: Services = Services.live,
      index: Path = FileHandler.indexFilePath,
      archiveRoot: Path = Path("docs/Archive")
  ): IO[ExitCode] = {
    val date = range.endDate
    for {
      current <- Files[IO]
        .exists(index)
        .ifM(FileHandler.editionDate(index), IO.pure(None))
      _ <- current.traverse_(currentDate =>
        IO.raiseError(
          new UserError(
            s"the current edition is dated $currentDate: a new edition must end after it"
          )
        ).unlessA(date.isAfter(currentDate))
      )
      exitCode <- Files[IO].tempFile(None, "edition-", ".md", None).use {
        page =>
          for {
            _ <- Newsletter.generate(
              range,
              GenerateMode.Database(dbPath, AiMode.Enabled(refresh)),
              services,
              page
            )
            articles <- Files[IO]
              .readUtf8(page)
              .compile
              .string
              .map("class=\"article-card\"".r.findAllMatchIn(_).size)
            _ <- IO
              .raiseError(
                new UserError(
                  "no articles for this date range: nothing published"
                )
              )
              .whenA(articles == 0)
            archived <- FileHandler.publishEdition(
              page,
              date,
              index = index,
              archiveRoot = archiveRoot
            )
            _ <- Output.info(
              s"Published the $date edition to $index" +
                archived
                  .fold("")(path => s"; archived the previous one to $path")
            )
          } yield ExitCode.Success
      }
    } yield exitCode
  }
}
