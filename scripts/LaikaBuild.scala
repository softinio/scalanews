//> using scala 3.9.0
//> using dep org.typelevel::laika-core:1.3.2
//> using dep org.typelevel::laika-io:1.3.2
//> using file ArchiveNav.scala
//> using file SiteTheme.scala
//> using file Editions.scala
//> using file SiteFiles.scala

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

import cats.effect.*
import laika.io.syntax.*

object LaikaBuild extends IOApp.Simple {
  def run: IO[Unit] = for {
    _ <- IO.println("Starting Laika documentation build...")

    editions <- Edition.load()
    docs <- ArchiveNav.input().flatMap(SiteFiles.add(_, editions))
    _ <- IO.println("Running transformation...")
    _ <- SiteTheme.transformer(editions).use { t =>
      t.fromInput(docs)
        .toDirectory("site/target/docs/site")
        .transform
    }

    _ <- IO.println("Documentation site built successfully!")
  } yield ()
}
