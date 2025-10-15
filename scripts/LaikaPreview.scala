//> using scala 3.7.3
//> using dep org.typelevel::laika-core:1.3.2
//> using dep org.typelevel::laika-io:1.3.2
//> using dep org.typelevel::laika-preview:1.3.2
//> using dep org.http4s::http4s-ember-server:0.23.32
//> using dep org.http4s::http4s-dsl:0.23.32

import laika.preview.*
import laika.api.*
import laika.format.*
import laika.io.api.*
import laika.io.syntax.*
import laika.io.model.*
import laika.theme.*
import cats.effect.*
import cats.syntax.all.*
import laika.ast.Path.Root
import laika.helium.Helium
import laika.helium.config.*
import scala.concurrent.duration.*
import com.comcast.ip4s.*

object LaikaPreview extends IOApp.Simple {
  def run: IO[Unit] = {
    val heliumTheme = Helium.defaults
      .all.metadata(
        title = Some("Scala News"),
        language = Some("en")
      )
      .site.topNavigationBar(
        homeLink = IconLink.internal(Root / "index.md", HeliumIcon.home)
      )
      .site.favIcons(
        Favicon.internal(Root / "img" / "favicon-32x32.png", sizes = "32x32")
      )
      .site.mainNavigation(depth = 3)
      .site.footer(
        """<br/>
          |Created by <a href="https://www.softinio.com">Salar Rahmanian</a> and Contributors.
          |<br/>
          |<a rel="license" href="http://creativecommons.org/licenses/by/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by/4.0/80x15.png" /></a><br />The content on this site by <span xmlns:cc="http://creativecommons.org/ns#" property="cc:attributionName">Salar Rahmanian and contributors</span> is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution 4.0 International License</a>.<br/>
          |Made with ❤️ in San Francisco using: | <a href="https://typelevel.org/cats-effect/">cats-effect</a> | <a href="https://typelevel.org/Laika/">Laika</a> |
          |""".stripMargin
      )
      .build

    import org.http4s._
    import org.http4s.dsl.io._
    import org.http4s.ember.server.EmberServerBuilder
    import org.http4s.server.staticcontent._
    import java.nio.file.Paths

    val transformer = Transformer
      .from(Markdown)
      .to(HTML)
      .using(Markdown.GitHubFlavor)
      .parallel[IO]
      .withTheme(heliumTheme)
      .build

    transformer.use { t =>
      val siteDir = "site/target/docs/preview"

      for {
        _ <- IO.println("Building documentation site...")
        _ <- t.fromDirectory("docs").toDirectory(siteDir).transform
        _ <- IO.println(s"Site built at: $siteDir")

        // Serve the built site with HTTP server
        httpApp = HttpRoutes.of[IO] {
          case request @ GET -> path =>
            val filePath = if (path.toString == "/" || path.toString.isEmpty) "/index.html" else path.toString
            val file = Paths.get(siteDir, filePath).toFile
            if (file.exists()) {
              StaticFile.fromFile[IO](file, Some(request)).getOrElseF(NotFound())
            } else {
              NotFound()
            }
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
    }
  }
}
