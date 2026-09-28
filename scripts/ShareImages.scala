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

import java.awt.{Color, Font, RenderingHints}
import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, File}
import javax.imageio.ImageIO

/** An edition's share image (1200x630, for link previews): the site's name,
  * the edition's date and what it contains, in the site's colours, so each
  * edition's preview looks new. Drawn with Java's own graphics in Lato, the
  * site's font, from the folder in `SCALANEWS_FONTS` (set by the Nix dev shell,
  * so local and CI builds draw the same images); without it, Java's default
  * sans-serif font is used.
  */
object ShareImages {
  val width = 1200
  val height = 630

  private val navy = Color.decode("#002b36")
  private val red = Color.decode("#c42b28")
  private val lightTeal = Color.decode("#a7d4de")

  private def font(file: String, style: Int): Font =
    sys.env
      .get("SCALANEWS_FONTS")
      .map(dir => new File(dir, file))
      .filter(_.isFile)
      .map(Font.createFont(Font.TRUETYPE_FONT, _))
      .getOrElse {
        System.err.println(
          s"warning: $file not found (SCALANEWS_FONTS is set by the Nix dev shell); drawing share images in the default sans-serif font"
        )
        new Font(Font.SANS_SERIF, style, 12)
      }

  private lazy val bold = font("Lato-Bold.ttf", Font.BOLD)
  private lazy val regular = font("Lato-Regular.ttf", Font.PLAIN)

  /** The edition's image as PNG bytes. */
  def render(edition: Edition, siteHost: String): Array[Byte] = {
    System.setProperty("java.awt.headless", "true")
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    try {
      g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON
      )
      g.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON
      )
      g.setColor(navy)
      g.fillRect(0, 0, width, height)
      g.setColor(red)
      g.fillRect(80, 170, 12, 290)

      def text(value: String, f: Font, size: Float, color: Color, y: Int) = {
        g.setFont(f.deriveFont(size))
        g.setColor(color)
        g.drawString(value, 130, y)
      }
      text("Scala News", bold, 104f, Color.WHITE, 280)
      text(
        edition.date.format(Edition.headingDateFormat),
        bold,
        56f,
        lightTeal,
        370
      )
      text(contents(edition), regular, 38f, lightTeal, 440)
      text(siteHost, regular, 32f, lightTeal, 565)
    } finally g.dispose()

    val png = new ByteArrayOutputStream()
    ImageIO.write(image, "png", png)
    png.toByteArray
  }

  /** "7 articles from Scala bloggers", or for 2023's mixed editions "17
    * links to Scala news, events and releases".
    */
  def contents(edition: Edition): String = {
    val n = edition.articles.size
    if (n == 0) "Curated Scala news from the community"
    else if (edition.fromBloggers)
      s"$n article${if (n == 1) "" else "s"} from Scala bloggers"
    else s"$n links to Scala news, events and releases"
  }
}
