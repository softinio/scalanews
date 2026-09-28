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

import cats.effect.IO
import laika.ast.Path.Root
import laika.io.model.{InputTree, InputTreeBuilder}

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import scala.jdk.CollectionConverters.*

/** The docs input, with the archive organised by year: `docs/Archive/<year>/`.
  *
  * Nothing about the layout is kept in the repo; on every build this adds
  * `scalanews.archiveYears`: every year from the first archived one to this
  * year, newest first, each with its editions newest first. The sidebar
  * template (`docs/helium/templates/mainNav.template.html`) renders each year
  * as a `<details>` group, listing its editions by date alone ("March 3,
  * 2023", from the page's "# Scala News - March 3, 2023" heading).
  *
  * It's set twice: in the root `directory.conf`, with only the newest year
  * open, and in each year's `directory.conf`, with that year open too, so the
  * page being read is never folded away.
  */
object ArchiveNav {
  private final case class Edition(path: String, title: String)

  private val editionFile = """scala_news_(\d{4}-\d{2}-\d{2})\.md""".r
  private val titlePrefix = "Scala News - "

  def input(docsDir: String = "docs"): IO[InputTreeBuilder[IO]] =
    IO.blocking(editionsByYear(new File(docsDir, "Archive"))).map { editions =>
      val thisYear = LocalDate.now().getYear
      val firstYear = editions.keys.minOption.getOrElse(thisYear)
      val years = (firstYear to thisYear).toList.reverse

      def yearsConfig(open: Set[Int]): String =
        years
          .map(year => yearConfig(year, editions.getOrElse(year, Nil), open(year)))
          .mkString("scalanews.archiveYears = [\n", "\n", "\n]\n")

      editions.keys.foldLeft(
        InputTree[IO]
          .addDirectory(docsDir)
          .addString(yearsConfig(Set(years.head)), Root / "directory.conf")
      ) { (tree, year) =>
        tree.addString(
          yearsConfig(Set(years.head, year)),
          Root / "Archive" / year.toString / "directory.conf"
        )
      }
    }

  // `openAttr` and `count` are written into the HTML as they are; `entries`
  // is passed to `@:navigationTree`, which links each edition and marks the
  // current page.
  private def yearConfig(year: Int, editions: List[Edition], open: Boolean) = {
    val entries = editions
      .map(edition =>
        s"""{ target = ${quote(edition.path)}, title = ${quote(edition.title)}, excludeSections = true }"""
      )
      .mkString("[", ", ", "]")
    val openAttr = if (open) "open" else ""
    val count = if (editions.isEmpty) "" else s"(${editions.size})"
    s"""  { year = $year, openAttr = "$openAttr", empty = ${editions.isEmpty}, count = "$count", entries = $entries }"""
  }

  /** Editions by year, newest first, from `Archive/<year>/scala_news_<date>.md`. */
  private def editionsByYear(archiveDir: File): Map[Int, List[Edition]] =
    Option(archiveDir.listFiles()).toList.flatten
      .filter(dir => dir.isDirectory && dir.getName.matches("""\d{4}"""))
      .map { dir =>
        val editions = Option(dir.listFiles()).toList.flatten
          .flatMap(file =>
            file.getName match {
              case editionFile(date) => Some(file -> date)
              case _                 => None
            }
          )
          .sortBy(_._2)
          .reverse
          .map((file, date) =>
            Edition(
              s"/Archive/${dir.getName}/${file.getName}",
              navigationTitle(file).getOrElse(date)
            )
          )
        dir.getName.toInt -> editions
      }
      .filter(_._2.nonEmpty)
      .toMap

  /** The page's first heading without the "Scala News - " prefix. */
  private def navigationTitle(file: File): Option[String] =
    Files
      .readAllLines(file.toPath)
      .asScala
      .map(_.trim)
      .find(_.startsWith("# "))
      .map(_.drop(2).trim.stripPrefix(titlePrefix))
      .filter(_.nonEmpty)

  private def quote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
