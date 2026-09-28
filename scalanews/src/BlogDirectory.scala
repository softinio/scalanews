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
import fs2.io.file.Path

import com.softinio.scalanews.algebra.Blog

/** The blog directory page listing every blogger and their feed, and an OPML
  * file of all the feeds for importing into a feed reader.
  */
object BlogDirectory {
  private val directoryMarkdownFilePath =
    Path("docs/Resources/Blog_Directory.md")

  // Next to the page, so the page links to it by name.
  private val opmlFileName = "bloggers.opml"
  private val opmlFilePath = Path(s"docs/Resources/$opmlFileName")

  private val opmlTitle = "Scala News bloggers"

  def generateDirectory(bloggerList: List[Blog]): IO[String] =
    IO.blocking {
      val header = List(
        "# Bloggers",
        "",
        "A Directory of bloggers producing Scala related content with links to their rss feed when available.",
        "",
        s"""Follow them all: <a href="$opmlFileName" download>download the OPML file</a> and import it into your feed reader.""",
        "",
        "| Blog | URL | RSS Feed |",
        "| --- |:---:| ---:|"
      )
      val rows = bloggerList.map(blog =>
        s"| ${blog.name} | <${blog.url}> | [rss feed](${blog.rss}) |"
      )
      val footer = List(
        "",
        "###### Got a Scala related blog? Add it to this Blog Directory!",
        "",
        "See [README](https://github.com/softinio/scalanews/blob/main/README.md) for details."
      )
      (header ++ rows ++ footer).mkString("", "\n", "\n")
    }

  /** Every blogger's feed as OPML 2.0 (http://opml.org/spec2.opml), in one
    * folder, which feed readers import in one go.
    */
  def generateOpml(bloggerList: List[Blog]): String = {
    val outlines = bloggerList.map { blog =>
      val name = xmlEscape(blog.name)
      s"""      <outline type="rss" text="$name" title="$name" xmlUrl="${xmlEscape(
          blog.rss.toString
        )}" htmlUrl="${xmlEscape(blog.url.toString)}"/>"""
    }
    (List(
      """<?xml version="1.0" encoding="UTF-8"?>""",
      """<opml version="2.0">""",
      "  <head>",
      s"    <title>$opmlTitle</title>",
      "  </head>",
      "  <body>",
      s"""    <outline text="$opmlTitle" title="$opmlTitle">"""
    ) ++ outlines ++ List("    </outline>", "  </body>", "</opml>"))
      .mkString("", "\n", "\n")
  }

  private def xmlEscape(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  def createBloggerDirectory(bloggerList: List[Blog]): IO[ExitCode] =
    for {
      page <- generateDirectory(bloggerList)
      _ <- TextFiles.write(directoryMarkdownFilePath, page)
      _ <- TextFiles.write(opmlFilePath, generateOpml(bloggerList))
    } yield ExitCode.Success
}
