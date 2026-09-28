//> using scala 3.9.0
//> using dep org.typelevel::laika-core:1.3.2
//> using dep org.typelevel::laika-io:1.3.2
//> using file ArchiveNav.scala
//> using file SiteTheme.scala
//> using file Editions.scala
//> using file SiteFiles.scala
//> using file ShareImages.scala
//> using dep org.typelevel::laika-preview:1.3.2
//> using dep org.http4s::http4s-ember-server:0.23.37
//> using dep org.http4s::http4s-dsl:0.23.37

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
import com.comcast.ip4s.*
import laika.io.syntax.*

object LaikaPreview extends IOApp.Simple {
  def run: IO[Unit] = {
    import org.http4s._
    import org.http4s.dsl.io._
    import org.http4s.ember.server.EmberServerBuilder
    import org.http4s.server.staticcontent._

    Edition.load().flatMap(editions => SiteTheme.transformer(editions).use { t =>
      val siteDir = "site/target/docs/preview"

      for {
        _ <- IO.println("Building documentation site...")
        docs <- ArchiveNav.input().flatMap(SiteFiles.add(_, editions))
        _ <- t.fromInput(docs).toDirectory(siteDir).transform
        _ <- IO.println(s"Site built at: $siteDir")

        // Serve the built site with HTTP server
        httpApp = HttpRoutes.of[IO] {
          case request @ GET -> path =>
            val filePath = if (path.toString == "/" || path.toString.isEmpty) "/index.html" else path.toString
            StaticFile
              .fromPath(fs2.io.file.Path(siteDir + filePath), Some(request))
              .getOrElseF(NotFound())
        }.orNotFound

        _ <- IO.println("Starting preview server at http://localhost:4242")
        _ <- IO.println("Press Ctrl+C to stop")

        _ <- EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"4242")
          .withHttpApp(httpApp)
          .build
          .use(_ => IO.never)
      } yield ()
    })
  }
}
