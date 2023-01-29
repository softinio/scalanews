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

import java.time.format.DateTimeFormatter.BASIC_ISO_DATE
import java.time.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

import java.util.stream.Collectors

import munit.CatsEffectSuite
import cats.effect._

class FileHandlerSuite extends CatsEffectSuite {

  val sampleFile = FunFixture[Path](
    setup = { test =>
      val filename = test.name.replace(" ", "_")
      val theFile = Files.createTempFile("tmp", s"${filename}.md")
      Files.write(theFile, "# Scala News\n".getBytes(StandardCharsets.UTF_8))
    },
    teardown = { file =>
      Files.deleteIfExists(file)
      ()
    }
  )

  sampleFile.test("updateFileHeader succesfully") { file =>
    val updated = FileHandler.updateFileHeader(file, LocalDate.now())
    val expectedDate = LocalDate
      .now()
      .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    val expectedHeader = s"# Scala News $expectedDate"
    val result = for {
      got <- updated
      extracted <- got match {
        case Right(path) =>
          IO(
            Files
              .lines(path, StandardCharsets.UTF_8)
              .collect(Collectors.joining(System.lineSeparator()))
          )
        case _ => IO.pure("")
      }
    } yield extracted.contains(expectedHeader)
    assertIO(result, true)
  }

  test("getDate for a valid date") {
    val date = FileHandler.getDate(Some("20221220"))
    val expected = Right(LocalDate.parse("20221220", BASIC_ISO_DATE))
    assertIO(date, expected)
  }

  test("getDate for a invalid date") {
    val date = FileHandler.getDate(Some("2022-12-20"))
    date.map(r => assertEquals(r.isLeft, true))
  }

  test("getDate for today") {
    val date = FileHandler.getDate(None)
    assertIO(date, Right(LocalDate.now()))
  }

  // test("Main should exit succesfully") {
  //   val main = Main.main.withDefault()
  //   assertIO(main, ExitCode.Success)
  // }

}
