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
import cats.syntax.all.*
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

  private val sept20 = LocalDate.of(2026, 9, 20)

  test("getArchivePath - files an edition under its year by default") {
    Files[IO].tempDirectory.use { root =>
      for {
        path <- FileHandler.getArchivePath(sept20, None, root)
        folderExists <- Files[IO].isDirectory(root / "2026")
      } yield {
        assertEquals(path, root / "2026" / "scala_news_2026-09-20.md")
        assert(folderExists)
      }
    }
  }

  test("getArchivePath - uses the folder given instead of the year") {
    Files[IO].tempDirectory.use { root =>
      FileHandler
        .getArchivePath(sept20, Some("special"), root)
        .map(path =>
          assertEquals(path, root / "special" / "scala_news_2026-09-20.md")
        )
    }
  }

  private def edition(dir: Path, name: String, heading: String): IO[Path] = {
    val path = dir / name
    fs2.Stream
      .emit(s"\n$heading\n\nA curated list.\n")
      .through(Files[IO].writeUtf8(path))
      .compile
      .drain
      .as(path)
  }

  test("editionDate - reads the date from the edition's heading") {
    Files[IO].tempDirectory.use { dir =>
      for {
        dated <- edition(dir, "a.md", "# Scala News - November 24, 2024")
        undated <- edition(dir, "b.md", "# Scala News")
        date <- FileHandler.editionDate(dated)
        none <- FileHandler.editionDate(undated)
      } yield {
        assertEquals(date, Some(LocalDate.of(2024, 11, 24)))
        assertEquals(none, None)
      }
    }
  }

  test("archiveDateFor - the date given wins; invalid ones are rejected") {
    Files[IO].tempDirectory.use { dir =>
      for {
        index <- edition(dir, "index.md", "# Scala News - November 24, 2024")
        fromArg <- FileHandler.archiveDateFor(Some("20260920"), index)
        fromHeading <- FileHandler.archiveDateFor(None, index)
        invalid <- FileHandler.archiveDateFor(Some("2026-09-20"), index).attempt
      } yield {
        assertEquals(fromArg, sept20)
        assertEquals(fromHeading, LocalDate.of(2024, 11, 24))
        assert(invalid.left.exists(_.isInstanceOf[UserError]), invalid)
      }
    }
  }

  test("archiveDateFor - an undated edition needs the date given") {
    Files[IO].tempDirectory.use { dir =>
      edition(dir, "index.md", "# Scala News")
        .flatMap(FileHandler.archiveDateFor(None, _).attempt)
        .map(result =>
          assert(result.left.exists(_.isInstanceOf[UserError]), result)
        )
    }
  }

  test("publishEdition - archives the current edition and dates the new one") {
    Files[IO].tempDirectory.use { dir =>
      for {
        index <- edition(dir, "index.md", "# Scala News - August 31, 2026")
        draft <- edition(dir, "draft.md", "# Scala News")
        archived <- FileHandler.publishEdition(
          draft,
          LocalDate.of(2026, 10, 31),
          index = index,
          archiveRoot = dir / "Archive"
        )
        newDate <- FileHandler.editionDate(index)
        archivedDate <- archived.flatTraverse(FileHandler.editionDate)
        draftLeft <- Files[IO].exists(draft)
      } yield {
        assertEquals(
          archived,
          Some(dir / "Archive" / "2026" / "scala_news_2026-08-31.md")
        )
        assertEquals(archivedDate, Some(LocalDate.of(2026, 8, 31)))
        assertEquals(newDate, Some(LocalDate.of(2026, 10, 31)))
        assert(!draftLeft)
      }
    }
  }
}
