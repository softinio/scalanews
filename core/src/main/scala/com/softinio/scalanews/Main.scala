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

import cats.effect._

import com.monovore.decline._
import com.monovore.decline.effect._

object Main
    extends CommandIOApp(
      name = "scalanews",
      header = "scalanews cli",
      version = "0.1"
    ) {

  case class Publish(archiveDate: Option[String])
  case class Create(overwrite: Boolean)

  val archiveDateOps: Opts[Option[String]] =
    Opts
      .option[String](
        "date",
        "Date to archive current newsletter to",
        short = "d"
      )
      .orNone

  val publishOpts: Opts[Publish] =
    Opts.subcommand("publish", "Publish next newsletter") {
      archiveDateOps.map(Publish)
    }

  val createOpts: Opts[Create] =
    Opts.subcommand("create", "Create file for next newsletter edition") {
      Opts
        .flag("overwrite", "Overwrite next file if it exists", short = "o")
        .orFalse
        .map(Create)
    }

  override def main: Opts[IO[ExitCode]] =
    (publishOpts orElse createOpts).map {
      case Publish(archiveDate) => FileHandler.publish(archiveDate)
      case Create(overwrite)    => FileHandler.create(overwrite)
    }
}
