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
import laika.io.model.InputTreeBuilder

import java.io.File

/** Files generated for the site at build time, and the facts about it they
  * share with the page metadata (see `SiteTheme`): its URL, and each page's
  * URL and description.
  *
  *   - `feed.xml`: an Atom feed of the editions, newest first, each listing its
  *     articles, so readers can follow new editions in a feed reader;
  *   - `sitemap.xml` (every page) and `robots.txt` (pointing to it), for search
  *     engines.
  */
object SiteFiles {
  val siteUrl = "https://www.scalanews.net/"
  val siteTitle = "Scala News"
  val siteDescription =
    "A curated list of Scala related news from the community: articles from Scala bloggers."
  val feedUrl: String = siteUrl + "feed.xml"
  val socialImageUrl: String = siteUrl + "img/social-card.png"

  /** The page's absolute URL: `/index.md` is the site root, and other pages are
    * rendered to `.html`.
    */
  def pageUrl(path: Path): String =
    if (path == Root / "index.md") siteUrl
    else siteUrl + path.withSuffix("html").toString.stripPrefix("/")

  /** A short description of an edition, for search results and link previews.
    */
  def description(edition: Edition): String = {
    val date = edition.date.format(Edition.headingDateFormat)
    edition.articles.map(_._1) match {
      case Nil => s"Scala News for $date: curated Scala news from the community."
      case first :: rest =>
        val n = rest.size + 1
        val count =
          if (edition.fromBloggers)
            s"$n article${if (n == 1) "" else "s"} from Scala bloggers"
          else s"$n links to Scala news, events and releases"
        val firstTitle =
          if (first.length <= 70) first else first.take(69).trim + "…"
        s"Scala News for $date: $count, including \"$firstTitle\"."
    }
  }

  /** Adds the generated files to the site's input. */
  def add(
      tree: InputTreeBuilder[IO],
      editions: List[Edition],
      docsDir: String = "docs"
  ): IO[InputTreeBuilder[IO]] =
    IO.blocking(pages(new File(docsDir))).map { pagePaths =>
      tree
        .addString(feed(editions), Root / "feed.xml")
        .addString(sitemap(pagePaths, editions), Root / "sitemap.xml")
        .addString(robots, Root / "robots.txt")
    }

  def feed(editions: List[Edition]): String = {
    def entry(edition: Edition): String = {
      val url = pageUrl(edition.path)
      val articles = edition.articles
        .map((title, link) => s"<li><a href=\"${escape(link)}\">${escape(title)}</a></li>")
        .mkString("<ul>", "", "</ul>")
      // The id stays the same when the edition moves from the home page to
      // the archive, so feed readers don't show it twice.
      s"""  <entry>
         |    <title>${escape(edition.title)}</title>
         |    <link rel="alternate" type="text/html" href="${escape(url)}"/>
         |    <id>tag:scalanews.net,${edition.date}:edition</id>
         |    <published>${edition.date}T00:00:00Z</published>
         |    <updated>${edition.date}T00:00:00Z</updated>
         |    <summary>${escape(description(edition))}</summary>
         |    <content type="html">${escape(articles)}</content>
         |  </entry>""".stripMargin
    }
    val updated = editions.headOption.fold("1970-01-01")(_.date.toString)
    s"""<?xml version="1.0" encoding="utf-8"?>
       |<feed xmlns="http://www.w3.org/2005/Atom">
       |  <title>$siteTitle</title>
       |  <subtitle>${escape(siteDescription)}</subtitle>
       |  <link rel="self" type="application/atom+xml" href="$feedUrl"/>
       |  <link rel="alternate" type="text/html" href="$siteUrl"/>
       |  <id>$siteUrl</id>
       |  <updated>${updated}T00:00:00Z</updated>
       |  <author><name>Salar Rahmanian and contributors</name></author>
       |  <icon>${siteUrl}img/favicon-32x32.png</icon>
       |${editions.map(entry).mkString("\n")}
       |</feed>
       |""".stripMargin
  }

  def sitemap(
      pagePaths: List[Path],
      editions: List[Edition]
  ): String = {
    val dates = editions.map(e => e.path -> e.date).toMap
    val urls = pagePaths.sortBy(_.toString).map { path =>
      val lastmod =
        dates.get(path).fold("")(date => s"<lastmod>$date</lastmod>")
      s"  <url><loc>${escape(pageUrl(path))}</loc>$lastmod</url>"
    }
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
       |${urls.mkString("\n")}
       |</urlset>
       |""".stripMargin
  }

  private val robots =
    s"""User-agent: *
       |Allow: /
       |
       |Sitemap: ${siteUrl}sitemap.xml
       |""".stripMargin

  /** The site's pages: every Markdown file in `docs` outside `helium/`. */
  private def pages(docs: File): List[Path] = {
    def walk(dir: File, path: Path): List[Path] =
      Option(dir.listFiles()).toList.flatten.flatMap { file =>
        if (file.isDirectory && !(path == Root && file.getName == "helium"))
          walk(file, path / file.getName)
        else if (file.getName.endsWith(".md")) List(path / file.getName)
        else Nil
      }
    walk(docs, Root)
  }

  /** Escapes text for XML and HTML text and attribute values. */
  def escape(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
}
