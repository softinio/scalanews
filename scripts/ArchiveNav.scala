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
import java.time.LocalDate

/** The docs input, with the archive organised by year: `docs/Archive/<year>/`.
  *
  * Nothing about the layout is kept in the repo; on every build this adds:
  *   - `scalanews.archiveYears` (root `directory.conf`): every year from the
  *     first archived one to this year, newest first. The sidebar template
  *     (`docs/helium/templates/mainNav.template.html`) renders each
  *     as a `<details>` group, open for the newest year only;
  *   - a `directory.conf` per year that lists its editions newest first and
  *     reopens that year's group, so the page being read is never folded away.
  */
object ArchiveNav {
  private val editionFile = """scala_news_(\d{4})-\d{2}-\d{2}\.md""".r

  def input(docsDir: String = "docs"): IO[InputTreeBuilder[IO]] =
    IO.blocking(editionsByYear(new File(docsDir, "Archive"))).map { editions =>
      val thisYear = LocalDate.now().getYear
      val firstYear = editions.keys.minOption.getOrElse(thisYear)
      val years = (firstYear to thisYear).toList.reverse

      // `openAttr` and `count` are written into the HTML as they are.
      def yearsConfig(open: Set[Int]): String =
        years
          .map { year =>
            val count = editions.get(year).fold(0)(_.size)
            val openAttr = if (open(year)) "open" else ""
            val countText = if (count == 0) "" else s"($count)"
            s"""  { year = $year, openAttr = "$openAttr", empty = ${count == 0}, count = "$countText" }"""
          }
          .mkString("scalanews.archiveYears = [\n", "\n", "\n]\n")

      editions.foldLeft(
        InputTree[IO]
          .addDirectory(docsDir)
          .addString(yearsConfig(Set(years.head)), Root / "directory.conf")
      ) { case (tree, (year, files)) =>
        tree.addString(
          navigationOrder(files.sorted.reverse) +
            yearsConfig(Set(years.head, year)),
          Root / "Archive" / year.toString / "directory.conf"
        )
      }
    }

  /** Edition file names by year, from `Archive/<year>/scala_news_<date>.md`. */
  private def editionsByYear(archiveDir: File): Map[Int, List[String]] =
    Option(archiveDir.listFiles()).toList.flatten
      .filter(dir => dir.isDirectory && dir.getName.matches("""\d{4}"""))
      .map(dir =>
        dir.getName.toInt -> Option(dir.list()).toList.flatten.collect {
          case name @ editionFile(_) => name
        }
      )
      .filter(_._2.nonEmpty)
      .toMap

  private def navigationOrder(entries: List[String]): String =
    entries
      .map(entry => s"  \"$entry\"")
      .mkString("laika.navigationOrder = [\n", "\n", "\n]\n")
}
