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

package com.softinio.scalanews.algebra

import java.text.{ParseException, SimpleDateFormat}
import java.time.{LocalDate, ZoneId}
import java.util.{Calendar, Date}

/** The period a newsletter covers, both dates inclusive: `start` and `end` are
  * midnight at the start of each day, and an article is in range when published
  * from `start` up to (not including) midnight after `end`.
  */
final case class DateRange(start: Date, end: Date) {

  /** Midnight at the end of `end`: one calendar day later, in the local time
    * zone the dates were parsed in, so a daylight-saving change still counts as
    * one day.
    */
  private val endExclusive: Date = {
    val calendar = Calendar.getInstance()
    calendar.setTime(end)
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    calendar.getTime
  }

  def contains(date: Date): Boolean =
    !date.before(start) && date.before(endExclusive)

  /** The last day of the range, in the local time zone it was parsed in. */
  def endDate: LocalDate =
    end.toInstant.atZone(ZoneId.systemDefault).toLocalDate
}

object DateRange {

  /** Parses `yyyy-MM-dd` dates strictly (an impossible date such as 2026-13-45
    * is rejected rather than rolling over into another month) and checks the
    * end isn't before the start.
    */
  def parse(start: String, end: String): Either[String, DateRange] =
    for {
      s <- parseDate(start)
      e <- parseDate(end)
      range <- Either.cond(
        !e.before(s),
        DateRange(s, e),
        s"end date $end is before start date $start"
      )
    } yield range

  private def parseDate(text: String): Either[String, Date] = {
    val format = new SimpleDateFormat("yyyy-MM-dd")
    format.setLenient(false)
    try Right(format.parse(text))
    catch {
      case _: ParseException =>
        Left(s"invalid date '$text' (expected yyyy-MM-dd)")
    }
  }
}
