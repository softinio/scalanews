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

import cats.effect.{IO, Resource}
import laika.api.*
import laika.ast.*
import laika.ast.Path.Root
import laika.format.*
import laika.helium.Helium
import laika.helium.config.*
import laika.io.api.TreeTransformer
import laika.io.syntax.*
import laika.theme.config.Color

/** The site's Helium theme and Markdown transformer, shared by `LaikaBuild`
  * and `LaikaPreview`.
  *
  * Colours: Helium's teal, with scala-lang.org's red (deepened a little, to
  * `c42b28`, so small text keeps AA contrast on the sidebar's pale background)
  * and its dark navy top bar (set in `docs/css/scalanews.css`). Dark mode keeps
  * Helium's own palette.
  */
object SiteTheme {

  private val helium = Helium.defaults
    .all.metadata(
      title = Some("Scala News"),
      description = Some(
        "A curated list of Scala related news from the community: articles from Scala bloggers, events and releases."
      ),
      language = Some("en")
    )
    .all.themeColors(
      primary = Color.hex("007c99"),
      primaryMedium = Color.hex("a7d4de"),
      primaryLight = Color.hex("eef5f6"),
      secondary = Color.hex("c42b28"),
      text = Color.hex("5f5f5f"),
      background = Color.hex("ffffff"),
      bgGradient = (Color.hex("002b36"), Color.hex("007c99"))
    )
    .site.topNavigationBar(
      homeLink = TextLink.internal(Root / "index.md", "Scala News"),
      navLinks = Seq(
        TextLink.internal(Root / "Resources" / "Blog_Directory.md", "Bloggers"),
        TextLink.external("https://www.scala-lang.org/community", "Community"),
        IconLink.external(
          "https://github.com/softinio/scalanews",
          HeliumIcon.github
        )
      ),
      highContrast = true
    )
    .site.favIcons(
      Favicon.internal(Root / "img" / "favicon-32x32.png", sizes = "32x32")
    )
    .site.mainNavigation(depth = 3)
    // Newsletter pages are short and flat; the sidebar is enough.
    .site.pageNavigation(enabled = false)
    .site.internalCSS(Root / "css" / "scalanews.css")
    .site.footer(
      """<br/>
        |Created by <a href="https://www.softinio.com">Salar Rahmanian</a> and Contributors.
        |<br/>
        |<a rel="license" href="http://creativecommons.org/licenses/by/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by/4.0/80x15.png" /></a><br />The content on this site by <span xmlns:cc="http://creativecommons.org/ns#" property="cc:attributionName">Salar Rahmanian and contributors</span> is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution 4.0 International License</a>.<br/>
        |Made with ❤️ in San Francisco using: | <a href="https://typelevel.org/cats-effect/">cats-effect</a> | <a href="https://typelevel.org/Laika/">Laika</a> |
        |""".stripMargin
    )
    .build

  private val editionPrefix = "Scala News - "

  /** Renders an edition's "# Scala News - March 3, 2023" heading as a small
    * "Scala News" label above the date: the top bar already names the site.
    * The heading's text is unchanged, so the page's `<title>` keeps the full
    * name.
    */
  private val editionTitle: RewriteRule[Block] = {
    case header @ Header(1, Seq(Text(text, textOptions)), _)
        if text.startsWith(editionPrefix) =>
      RewriteAction.Replace(
        header.withContent(
          Seq(
            Text(editionPrefix.stripSuffix(" - "), Styles("edition-brand")),
            Text(" - ", Styles("edition-separator")),
            Text(text.stripPrefix(editionPrefix), textOptions)
          )
        )
      )
  }

  def transformer: Resource[IO, TreeTransformer[IO]] =
    Transformer
      .from(Markdown)
      .to(HTML)
      .using(Markdown.GitHubFlavor)
      // Newsletter pages embed HTML (the article cards); render it rather than escape it.
      .withRawContent
      .usingBlockRule(editionTitle)
      .parallel[IO]
      .withTheme(helium)
      .build
}
