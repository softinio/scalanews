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

import munit.FunSuite

import com.softinio.scalanews.algebra.DateRange

class DateRangeSuite extends FunSuite {

  private def range(start: String, end: String) =
    DateRange.parse(start, end).toOption.get

  test("parse - accepts real dates in order") {
    assert(DateRange.parse("2026-09-01", "2026-09-26").isRight)
    assert(DateRange.parse("2026-09-26", "2026-09-26").isRight)
  }

  test("parse - rejects impossible dates instead of rolling them over") {
    assert(DateRange.parse("2026-13-45", "2026-09-26").isLeft)
    assert(DateRange.parse("2026-09-01", "2026-02-30").isLeft)
    assert(DateRange.parse("26/09/2026", "2026-09-30").isLeft)
  }

  test("parse - rejects an end before the start") {
    assertEquals(
      DateRange.parse("2026-09-30", "2026-09-01"),
      Left("end date 2026-09-01 is before start date 2026-09-30")
    )
  }

  test("contains - excludes both ends (midnight on each date)") {
    val r = range("2026-09-01", "2026-09-26")
    val day = 24 * 60 * 60 * 1000L
    assert(r.contains(new java.util.Date(r.start.getTime + day)))
    assert(!r.contains(r.start))
    assert(!r.contains(r.end))
    assert(!r.contains(new java.util.Date(r.end.getTime + day / 2)))
  }
}
