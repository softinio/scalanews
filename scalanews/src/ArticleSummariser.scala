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
import io.circe.{Codec, Encoder, Json}
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax.*
import sttp.ai.core.agent.{ResponseSchema, Variant}
import sttp.tapir.Schema

import com.softinio.scalanews.algebra.{AnthropicConfig, Article, ArticleSummary}

/** Summarises articles with Claude, using structured outputs for both the
  * request and the reply.
  */
object ArticleSummariser {

  /** The article text sent to Claude; there is no case for "no text". */
  enum ArticleText {
    case Complete(text: String)
    case Truncated(text: String, originalLength: Int)
  }

  object ArticleText {
    val MaxChars = 3000

    def from(content: String): Option[ArticleText] = {
      val trimmed = content.trim
      if (trimmed.isEmpty) None
      else if (trimmed.length <= MaxChars) Some(Complete(trimmed))
      else Some(Truncated(trimmed.take(MaxChars), trimmed.length))
    }

    given Encoder[ArticleText] = Encoder.instance {
      case Complete(text) =>
        Json.obj("kind" -> "complete".asJson, "text" -> text.asJson)
      case Truncated(text, originalLength) =>
        Json.obj(
          "kind" -> "truncated".asJson,
          "text" -> text.asJson,
          "originalLength" -> originalLength.asJson
        )
    }
  }

  final case class ArticleInput(
      title: String,
      author: String,
      url: Option[String],
      text: ArticleText
  ) derives Encoder.AsObject

  /** The reply shape Claude is constrained to by the response schema. */
  sealed trait SummaryResponse
  object SummaryResponse {
    final case class Summary(text: String) extends SummaryResponse
    final case class InsufficientContent(reason: String) extends SummaryResponse

    given Schema[Summary] = Schema.derived
    given Schema[InsufficientContent] = Schema.derived
    given Codec[Summary] = deriveCodec
    given Codec[InsufficientContent] = deriveCodec

    val responseSchema: ResponseSchema[SummaryResponse] =
      ResponseSchema.oneOf(Variant[Summary], Variant[InsufficientContent])
  }

  enum NoSummaryReason {
    case NoText
    case InsufficientContent(reason: String)
  }

  /** What happened when summarising an article. */
  enum Summarisation {
    case Summarised(summary: ArticleSummary)
    case NoSummary(reason: NoSummaryReason)
    case Failed(error: Throwable)
  }

  private val systemPrompt =
    s"""|You write summaries of blog posts for a Scala programming newsletter.
        |
        |The user message is a JSON article with a title, author, optional url and its text.
        |The text may be the full post or only a short excerpt, and is marked "truncated"
        |when it was cut short.
        |
        |Write a factual summary of what the article covers in ${ArticleSummary.MaxLength}
        |characters or fewer. If the text doesn't say enough about the article to summarise
        |it (for example it is only a link), report insufficient content instead of guessing
        |from the title.""".stripMargin

  private[scalanews] def interpret(
      response: SummaryResponse
  ): Summarisation =
    response match {
      case SummaryResponse.Summary(text) =>
        ArticleSummary
          .from(text)
          .fold(
            Summarisation.NoSummary(
              NoSummaryReason.InsufficientContent("empty summary")
            )
          )(Summarisation.Summarised(_))
      case SummaryResponse.InsufficientContent(reason) =>
        Summarisation.NoSummary(NoSummaryReason.InsufficientContent(reason))
    }

  def summarise(article: Article, config: AnthropicConfig): IO[Summarisation] =
    ArticleText.from(article.content) match {
      case None => IO.pure(Summarisation.NoSummary(NoSummaryReason.NoText))
      case Some(text) =>
        val input = ArticleInput(
          article.title,
          article.author,
          article.url.map(_.toString),
          text
        )
        AnthropicClient
          .structured(
            systemPrompt,
            input,
            SummaryResponse.responseSchema,
            config
          )
          .map(interpret)
          .handleError(Summarisation.Failed(_))
    }
}
