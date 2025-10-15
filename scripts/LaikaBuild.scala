//> using scala 3.7.3
//> using dep org.typelevel::laika-core:1.3.2
//> using dep org.typelevel::laika-io:1.3.2

import laika.api.*
import laika.format.*
import laika.io.api.*
import laika.io.syntax.*
import laika.theme.*
import cats.effect.*
import laika.ast.Path.Root
import laika.helium.Helium
import laika.helium.config.*

object LaikaBuild extends IOApp.Simple {
  def run: IO[Unit] = for {
    _ <- IO.println("Starting Laika documentation build...")

    heliumTheme = Helium.defaults
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

    transformer = Transformer
      .from(Markdown)
      .to(HTML)
      .using(Markdown.GitHubFlavor)
      .parallel[IO]
      .withTheme(heliumTheme)
      .build

    _ <- IO.println("Running transformation...")
    result <- transformer.use { t =>
      t.fromDirectory("docs")
        .toDirectory("site/target/docs/site")
        .transform
    }

    _ <- IO.println("Documentation site built successfully!")
  } yield ()
}
