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

package com.softinio.scalanews

import com.monovore.decline.Command
import munit.FunSuite

import com.softinio.scalanews.algebra.{AiMode, GenerateMode}
import com.softinio.scalanews.db.Database

class MainSuite extends FunSuite {

  private def parse(args: String*): Either[String, GenerateMode] =
    Command("scalanews", "")(Main.generateOpts)
      .parse(args)
      .map(_.mode)
      .left
      .map(_.errors.mkString("; "))

  private val dates = Seq("generate", "2026-09-01", "2026-09-07")

  test("generate - defaults to the database with Claude summaries") {
    assertEquals(
      parse(dates*),
      Right(
        GenerateMode.Database(
          Database.defaultPath,
          AiMode.Enabled(refresh = false)
        )
      )
    )
  }

  test("generate - --refresh-ai asks jev and Claude again") {
    assertEquals(
      parse((dates :+ "--refresh-ai")*),
      Right(
        GenerateMode.Database(
          Database.defaultPath,
          AiMode.Enabled(refresh = true)
        )
      )
    )
  }

  test("generate - --no-ai keeps the database without AI") {
    assertEquals(
      parse((dates :+ "--no-ai")*),
      Right(GenerateMode.Database(Database.defaultPath, AiMode.Disabled))
    )
  }

  test("generate - --dbpath chooses the database file") {
    assertEquals(
      parse((dates ++ Seq("--dbpath", "data/other.duckdb"))*),
      Right(
        GenerateMode.Database(
          "data/other.duckdb",
          AiMode.Enabled(refresh = false)
        )
      )
    )
  }

  test("generate - --no-db reads the feeds directly") {
    assertEquals(parse((dates :+ "--no-db")*), Right(GenerateMode.Direct))
    assertEquals(
      parse((dates ++ Seq("--no-db", "--no-ai"))*),
      Right(GenerateMode.Direct)
    )
  }

  test("generate - rejects flag combinations that make no sense") {
    assert(parse((dates ++ Seq("--no-db", "--refresh-ai"))*).isLeft)
    assert(parse((dates ++ Seq("--no-db", "--dbpath", "x.duckdb"))*).isLeft)
    assert(parse((dates ++ Seq("--no-ai", "--refresh-ai"))*).isLeft)
  }

  test("reportUserErrors - expected failures exit with an error code") {
    import cats.effect.unsafe.implicits.global
    assertEquals(
      Main
        .reportUserErrors(new UserError("TYPESAFE_API_KEY is not set"))
        .unsafeRunSync(),
      cats.effect.ExitCode.Error
    )
    assertEquals(
      Main
        .reportUserErrors(new java.text.ParseException("bad date", 0))
        .unsafeRunSync(),
      cats.effect.ExitCode.Error
    )
  }

  test("reportUserErrors - unexpected errors keep their stack trace") {
    assert(!Main.reportUserErrors.isDefinedAt(new RuntimeException("boom")))
  }

  test("parseDate - accepts a real date") {
    val cal = java.util.Calendar.getInstance()
    cal.setTime(Main.parseDate("2026-09-26"))
    assertEquals(
      (
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
      ),
      (2026, 9, 26)
    )
  }

  test("parseDate - rejects an impossible date instead of rolling it over") {
    intercept[java.text.ParseException](Main.parseDate("2026-13-45"))
    intercept[java.text.ParseException](Main.parseDate("2026-02-30"))
  }
}
