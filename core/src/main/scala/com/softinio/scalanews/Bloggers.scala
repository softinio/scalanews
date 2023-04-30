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

import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import cats.effect._
import cats.nio.file.Files

import com.softinio.scalanews.algebra.Blog

object Bloggers {
  val directoryMarkdownFilePath = Paths.get("docs/Resources/Blog_Directory.md")
  def generateDirectory(bloggerList: List[Blog]): IO[String] = {
    IO.blocking {
      val header = """
      |# Blog Directory 

      |A Directory of bloggers producing Scala related content with links to their rss feed when available.

      || Blog        | URL           | RSS Feed  |
      || ------------- |:-------------:| -----:|"""

      val footer = """
      |###### Got a Scala related blog? Add it to this Blog Directory!

      |See [README](https://github.com/softinio/scalanews/blob/main/README.md) for details."""

      val directory = bloggerList.map { blog =>
        s"|| ${blog.name} | <${blog.url}> | [rss feed](${blog.rss}) |"
      }

      s"""
      ${header}
      ${directory.mkString("\n")}
      ${footer}\n""".stripMargin
    }
  }

  def createBloggerDirectory(bloggerList: List[Blog]): IO[ExitCode] = {
    for {
      exists <- Files[IO].exists(directoryMarkdownFilePath)
      _ <- if (exists) Files[IO].delete(directoryMarkdownFilePath) else IO.unit
      directory <- generateDirectory(bloggerList)
      _ <- Files[IO].write(
        directoryMarkdownFilePath,
        directory.getBytes(),
        StandardOpenOption.CREATE_NEW
      )
    } yield (ExitCode.Success)
  }
}
