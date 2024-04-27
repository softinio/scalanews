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
import java.time.LocalDate
import fs2.io.file.*
import java.nio.charset.StandardCharsets

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

import munit.CatsEffectSuite
import cats.effect.*

class FileHandlerSuite extends CatsEffectSuite {

  val sampleFile: FunFixture[Path] = FunFixture[Path](
    setup = { test =>
      val filename = test.name.replace(" ", "_")
      val content =
        fs2.Stream.emits("# Scala News\n".getBytes(StandardCharsets.UTF_8))
      val theFile = Path.apply(s"$filename.md")
      Files[IO].writeAll(theFile)(content).compile.drain.unsafeRunSync()
      theFile
    },
    teardown = { file =>
      Files[IO].deleteIfExists(file).unsafeRunSync()
      ()
    }
  )

  sampleFile.test("test testfile") { file =>
    val result = Files[IO]
      .readAll(file)
      .through(fs2.text.utf8.decode)
      .compile
      .foldMonoid
      .map(_.trim)
    assertIO(result, "# Scala News")
  }

  sampleFile.test("updateFileHeader successfully") { file =>
    val expectedDate = LocalDate
      .now()
      .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    val expectedHeader = s"# Scala News - $expectedDate"
    val result = for {
      got <- FileHandler.updateFileHeader(file, LocalDate.now())
      exists <- got match {
        case Right(path) =>
          Files[IO]
            .readAll(path)
            .through(fs2.text.utf8.decode)
            .through(fs2.text.lines)
            .exists(line => line.contains(expectedHeader))
            .compile
            .lastOrError
        case _ => IO.pure("")
      }
    } yield exists
    assertIO(result, true)
  }

  test("getArchiveDate for a valid date") {
    val date = FileHandler.getArchiveDate("20221220")
    val expected = Right(LocalDate.parse("20221220", BASIC_ISO_DATE))
    assertIO(date, expected)
  }

  test("getPublishDate for a valid date") {
    val date = FileHandler.getPublishDate(Some("20221220"))
    val expected = Right(LocalDate.parse("20221220", BASIC_ISO_DATE))
    assertIO(date, expected)
  }

  test("getPublishDate for a invalid date") {
    val date = FileHandler.getPublishDate(Some("2022-12-20"))
    date.map(r => assertEquals(r.isLeft, true))
  }

  test("getPublishDate for today") {
    val date = FileHandler.getPublishDate(None)
    assertIO(date, Right(LocalDate.now()))
  }
}
