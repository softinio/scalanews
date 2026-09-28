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
  *     engines;
  *   - the current edition at its permanent URL (see [[Edition.permalink]]),
  *     and a share image for each edition (see [[ShareImages]]).
  */
object SiteFiles {
  val siteHost = "www.scalanews.net"
  val siteUrl = s"https://$siteHost/"
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
      case first :: _ =>
        val firstTitle =
          if (first.length <= 70) first else first.take(69).trim + "…"
        s"Scala News for $date: ${ShareImages.contents(edition)}, including \"$firstTitle\"."
    }
  }

  /** Where an edition's share image is published. */
  def imagePath(edition: Edition): Path =
    Root / "img" / "editions" / s"scala_news_${edition.date}.png"

  /** Adds the generated files to the site's input. */
  def add(
      tree: InputTreeBuilder[IO],
      editions: List[Edition],
      docsDir: String = "docs"
  ): IO[InputTreeBuilder[IO]] =
    IO.blocking {
      val docs = new File(docsDir)
      // The current edition at its permanent URL, unless it's already there.
      val current = editions
        .find(_.path == Root / "index.md")
        .filterNot(e => new File(docs, e.permalink.toString).exists)
        .map(e =>
          e.permalink -> java.nio.file.Files.readString(
            new File(docs, "index.md").toPath
          )
        )
      val images =
        editions.map(e => imagePath(e) -> ShareImages.render(e, siteHost))
      (pages(docs) ++ current.map(_._1), current, images)
    }.map { (pagePaths, current, images) =>
      val withCurrent =
        current.fold(tree)((path, markdown) => tree.addString(markdown, path))
      images
        .foldLeft(withCurrent) { case (t, (path, png)) =>
          t.addBinaryStream(fs2.Stream.emits(png), path)
        }
        .addString(feed(editions), Root / "feed.xml")
        .addString(sitemap(pagePaths, editions), Root / "sitemap.xml")
        .addString(robots, Root / "robots.txt")
    }

  def feed(editions: List[Edition]): String = {
    def entry(edition: Edition): String = {
      val url = pageUrl(edition.permalink)
      val articles = edition.articles
        .map((title, link) => s"<li><a href=\"${escape(link)}\">${escape(title)}</a></li>")
        .mkString("<ul>", "", "</ul>")
      // Linked at its permanent URL, which it keeps when archived.
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
    val dates = editions.flatMap(e => List(e.path -> e.date, e.permalink -> e.date)).toMap
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
