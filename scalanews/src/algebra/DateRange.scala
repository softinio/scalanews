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
import java.util.Date

/** The period a newsletter covers. An article is in range when published
  * strictly after `start` and strictly before `end`; both are midnight, so
  * articles published on the end date itself are not included.
  */
final case class DateRange(start: Date, end: Date) {
  def contains(date: Date): Boolean = date.after(start) && date.before(end)
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
