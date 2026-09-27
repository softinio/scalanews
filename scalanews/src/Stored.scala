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

import cats.effect.{IO, Resource}
import cats.effect.syntax.all.*
import cats.syntax.all.*

import com.softinio.scalanews.db.tables.ArticleRow

/** The part the stored AI steps (relevance and summaries) share. */
private[scalanews] object Stored {

  /** Calls `service` for `rows` and records each result, returning what was
    * recorded by article id. The service is only acquired when there are rows,
    * so no API key is needed when everything was stored already. Calls run
    * `concurrency` at a time; `record` runs one row at a time, so it can write
    * to the shared database connection.
    */
  def runMissing[S, R, B](
      rows: List[ArticleRow],
      service: Resource[IO, S],
      concurrency: Int
  )(call: (S, ArticleRow) => IO[R])(
      record: (ArticleRow, R) => IO[B]
  ): IO[Map[java.util.UUID, B]] =
    if (rows.isEmpty) IO.pure(Map.empty)
    else
      service
        .use(s =>
          rows.parTraverseN(concurrency)(row => call(s, row).tupleLeft(row))
        )
        .flatMap(
          _.traverse((row, result) => record(row, result).tupleLeft(row.id))
        )
        .map(_.toMap)
}
