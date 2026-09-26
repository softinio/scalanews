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

/** A non-empty, single-line article summary of at most
  * [[ArticleSummary.MaxLength]] characters.
  */
opaque type ArticleSummary = String

object ArticleSummary {
  val MaxLength = 200

  /** Collapses whitespace and trims to [[MaxLength]] at a word boundary; `None`
    * if nothing is left.
    */
  def from(text: String): Option[ArticleSummary] = {
    val flat = text.split("\\s+").filter(_.nonEmpty).mkString(" ")
    val fitted =
      if (flat.length <= MaxLength) flat
      else {
        val cut = flat.take(MaxLength)
        cut.lastIndexOf(' ') match {
          case -1 => cut
          case i  => cut.take(i)
        }
      }
    Option.when(fitted.nonEmpty)(fitted)
  }

  extension (summary: ArticleSummary) def value: String = summary
}
