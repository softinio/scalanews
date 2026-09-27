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
import laika.ast.Path
import laika.ast.Path.Root

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.util.Try

/** An edition of the newsletter, read from its Markdown: the home page
  * (`index.md`) or an archived one (`Archive/<year>/scala_news_<date>.md`).
  *
  * @param path
  *   its path in the site, e.g. `/Archive/2025/scala_news_2025-06-30.md`
  * @param title
  *   its heading's text, e.g. "Scala News - June 30, 2025"
  * @param articles
  *   the (title, url) of each article it links to
  * @param fromBloggers
  *   whether it lists articles from the bloggers (an "Articles" section, from
  *   2024 on), rather than a mix of news, events and releases (2023)
  */
final case class Edition(
    path: Path,
    date: LocalDate,
    title: String,
    articles: List[(String, String)],
    fromBloggers: Boolean
)

object Edition {
  private val headingPrefix = "# Scala News - "

  // The date in an edition's heading, as FileHandler writes it.
  val headingDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)

  // Article cards (from 2025), and Markdown links (older editions' tables
  // and lists).
  private val cardLink = """<h3><a href="([^"]+)">(.*?)</a></h3>""".r
  private val markdownLink = """\[([^\]]+)\]\((https?://[^)\s]+)\)""".r

  /** Every edition in `docsDir`, newest first. Files without a dated
    * "# Scala News - <date>" heading are left out.
    */
  def load(docsDir: String = "docs"): IO[List[Edition]] =
    IO.blocking {
      val docs = new File(docsDir)
      val archived = Option(new File(docs, "Archive").listFiles()).toList.flatten
        .filter(_.isDirectory)
        .flatMap(year =>
          Option(year.listFiles()).toList.flatten
            .filter(_.getName.endsWith(".md"))
            .map(file => file -> Root / "Archive" / year.getName / file.getName)
        )
      ((new File(docs, "index.md") -> Root / "index.md") :: archived)
        .flatMap(read)
        .sortBy(_.date)(using Ordering[LocalDate].reverse)
    }

  private def read(file: File, path: Path): Option[Edition] =
    Option.when(file.isFile)(Files.readString(file.toPath)).flatMap { text =>
      text.linesIterator
        .map(_.trim)
        .find(_.startsWith(headingPrefix))
        .flatMap { heading =>
          Try(
            LocalDate.parse(heading.stripPrefix(headingPrefix), headingDateFormat)
          ).toOption.map(date =>
            Edition(
              path,
              date,
              heading.stripPrefix("# "),
              articles(text),
              text.linesIterator.exists(_.trim == "## Articles")
            )
          )
        }
    }

  private def articles(markdown: String): List[(String, String)] = {
    val cards = cardLink
      .findAllMatchIn(markdown)
      .map(m => unescapeHtml(m.group(2)) -> unescapeHtml(m.group(1)))
      .toList
    if (cards.nonEmpty) cards
    else
      markdownLink
        .findAllMatchIn(markdown)
        .map(m => m.group(1) -> m.group(2))
        .toList
  }

  private def unescapeHtml(text: String): String =
    text
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&amp;", "&")
}
