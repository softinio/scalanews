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

import cats.effect.IO
import io.circe.generic.auto.*
import io.circe.parser.decode

import com.softinio.scalanews.algebra.{AnthropicConfig, Article}

object ArticleEnricher {

  private[scalanews] case class EnrichResult(relevant: Boolean, summary: String)

  private val maxContentChars = 3000

  private def buildPrompt(content: String): String = {
    val truncated = content.take(maxContentChars)
    s"""|You are evaluating articles for a Scala programming newsletter.
        |
        |Given the article content below, do two things:
        |1. Determine if the article is primarily about Scala (the programming language), SBT, or closely related Scala ecosystem tools.
        |2. If it is, write a summary in 200 characters or fewer.
        |
        |Respond with JSON only, no explanation:
        |{"relevant": true, "summary": "your summary here"}
        |or
        |{"relevant": false, "summary": ""}
        |
        |Article content:
        |$truncated""".stripMargin
  }

  // Claude often wraps its JSON in a ```json fence despite being asked not to,
  // so decode just the outermost {...} object.
  private def extractJson(response: String): String = {
    val start = response.indexOf('{')
    val end = response.lastIndexOf('}')
    if (start >= 0 && end > start) response.substring(start, end + 1)
    else response
  }

  private[scalanews] def parseResponse(response: String): Option[String] =
    decode[EnrichResult](extractJson(response)) match {
      case Right(EnrichResult(true, summary)) => Some(summary)
      case _                                  => None
    }

  def enrich(article: Article, config: AnthropicConfig): IO[Option[String]] =
    AnthropicClient
      .sendMessage(buildPrompt(article.content), config)
      .flatMap { response =>
        parseResponse(response) match {
          case some @ Some(_) => IO.pure(some)
          case None           =>
            IO.println(
              s"Article excluded or unparseable for '${article.title}'"
            ) *> IO.pure(None)
        }
      }
      .handleErrorWith { err =>
        IO.println(
          s"AI enrichment failed for '${article.title}': ${err.getMessage}"
        ) *> IO.pure(None)
      }
}
