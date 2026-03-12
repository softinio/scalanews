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

package com.softinio.scalanews.db

import com.softinio.duck4s.DuckDBConnection
import com.softinio.duck4s.algebra.DuckDBConfig
import com.softinio.duck4s.effect.DuckDBIO
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import java.nio.file.{Files, Paths}

type RowsAffected = Int

trait TableSchema:
  def createDdl: String

trait Repository[Entity, Row]:
  def insert(conn: DuckDBConnection, entity: Entity): IO[RowsAffected]
  def findAll(conn: DuckDBConnection): Stream[IO, Row]
  def findById(conn: DuckDBConnection, id: java.util.UUID): IO[Option[Row]]

object Database:

  def connect(
      path: String,
      tables: Seq[TableSchema]
  ): Resource[IO, DuckDBConnection] =
    Resource.eval(
      IO.blocking(Files.createDirectories(Paths.get(path).getParent))
    ) >>
      DuckDBIO
        .connect(DuckDBConfig.persistent(path))
        .evalMap(conn => initialize(conn, tables).as(conn))

  private def initialize(
      conn: DuckDBConnection,
      tables: Seq[TableSchema]
  ): IO[Unit] =
    tables.traverse_ { table =>
      for
        stmt <- IO.fromEither(
          conn
            .prepareStatement(table.createDdl)
            .left
            .map(e => new RuntimeException(e.toString))
        )
        _ <- IO
          .blocking(stmt.executeUpdate())
          .flatMap(r =>
            IO.fromEither(r.left.map(e => new RuntimeException(e.toString)))
          )
        _ <- IO(stmt.close())
      yield ()
    }

end Database
