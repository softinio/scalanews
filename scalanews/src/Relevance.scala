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

package com.softinio.scalanews

import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.softinio.verdict4s.{
  Ask,
  Verdict4sClient,
  Verdict4sEnv,
  Verdict4sError
}
import com.softinio.verdict4s.algebra.{ApiFailure, Probability, Question}
import io.circe.Encoder

import com.softinio.duck4s.DuckDBConnection
import com.softinio.scalanews.db.tables.{
  ArticleRepository,
  ArticleRow,
  StoredRelevance
}

/** Checking whether articles belong in the newsletter with jev (Typesafe),
  * storing its verdicts.
  */
object Relevance {

  /** What jev sees when judging an article's relevance: its title and the start
    * of its stored text.
    */
  private[scalanews] final case class RelevanceContext(
      title: String,
      text: String
  ) derives Encoder.AsObject

  private val relevanceTextChars = 2000

  // jev calls made at once; failed checks keep the article.
  private val relevanceConcurrency = 4

  // jev answers each question with P(yes). Excluding a real Scala post loses it
  // silently while including an off-topic one only costs a reader a skip, so
  // both thresholds lean towards keeping articles. Calibrated on real feeds:
  // off-topic posts scored at most 0.12 for "about Scala" while Scala
  // community news (e.g. Typelevel governance posts) scored 0.41-0.66; release
  // announcements scored 0.84-0.94 for "just an announcement" and substantive
  // posts at most 0.31.
  private[scalanews] val aboutScalaThreshold = 0.3
  private[scalanews] val announcementThreshold = 0.7

  // Both questions go to jev in a single request.
  private[scalanews] val relevanceQuestions = Ask(
    (
      Question.noul(
        "Is this article about the Scala programming language or its ecosystem?",
        "It is mainly about Scala itself or Scala libraries, frameworks, tools or build tools (such as sbt, Mill or Scala CLI).",
        "It is mainly about something else, and mentions Scala only in passing or not at all."
      ),
      Question.noul(
        "Is this article just an announcement of a new version of a Scala library or application?",
        "It only announces a release: a version number, a list of changes or release notes, with little else.",
        "It has substance beyond announcing a release, such as explanation, tutorial, design discussion or opinion."
      )
    )
  )

  /** Relevant when jev judged it about Scala and not just a release
    * announcement. Decided from stored probabilities, so retuning the
    * thresholds needs no new jev calls.
    */
  private[scalanews] def relevant(
      aboutScala: Probability,
      announcement: Probability
  ): Boolean =
    (aboutScala.value: Double) >= aboutScalaThreshold &&
      (announcement.value: Double) < announcementThreshold

  private[scalanews] def relevant(stored: StoredRelevance): Boolean =
    relevant(stored.aboutScala, stored.announcement)

  /** jev's answers to both relevance questions for a stored article. */
  private[scalanews] def assessRelevance(
      row: ArticleRow,
      client: Verdict4sClient[IO]
  ): IO[StoredRelevance] =
    client
      .ask(
        relevanceQuestions,
        RelevanceContext(row.title, row.content.take(relevanceTextChars))
      )
      .map((aboutScala, announcement) =>
        StoredRelevance(
          aboutScala.value,
          announcement.value,
          client.config.model.name
        )
      )

  /** Errors every jev call would hit (bad or missing key, no permission), so a
    * run should stop instead of keeping articles one by one.
    */
  private[scalanews] def isFatalVerdict(error: Throwable): Boolean =
    error match {
      case api: Verdict4sError.Api =>
        api.failure == ApiFailure.Unauthorized ||
        api.failure == ApiFailure.Forbidden
      case _: Verdict4sError.Invalid                    => true
      case e if e.getCause != null && (e.getCause ne e) =>
        isFatalVerdict(e.getCause)
      case _ => false
    }

  /** Adds a pointer to `--no-ai` when verdict4s reports the API key missing. */
  private[scalanews] val missingTypesafeKeyHint
      : PartialFunction[Throwable, Throwable] = {
    case e: Verdict4sError.Validation if e.field == "TYPESAFE_API_KEY" =>
      new UserError(
        "TYPESAFE_API_KEY is not set: set it for jev relevance checks, or use --no-ai",
        e
      )
  }

  /** One verdict4s client for the run, configured from the environment:
    * TYPESAFE_API_KEY, and optionally TYPESAFE_DEFAULT_MODEL (jev-latest by
    * default) and TYPESAFE_BASE_URL.
    */
  private[scalanews] def verdictClient: Resource[IO, Verdict4sClient[IO]] =
    Verdict4sEnv.default[IO].adaptError(missingTypesafeKeyHint)

  /** The rows jev judges relevant, reusing stored answers unless `refresh` and
    * storing new ones. The checker is only acquired (and TYPESAFE_API_KEY only
    * needed) when some row has no stored answer. A failed check keeps the
    * article and isn't stored, so it's retried next run; a bad key stops the
    * run.
    */
  private[scalanews] def filterRelevant(
      conn: DuckDBConnection,
      rows: List[ArticleRow],
      refresh: Boolean,
      checker: Resource[IO, RelevanceChecker]
  ): IO[List[ArticleRow]] = {
    val reusable: ArticleRow => Option[StoredRelevance] =
      row => if (refresh) None else row.storedRelevance
    val toCheck = rows.filter(reusable(_).isEmpty)

    for {
      checked <- Stored.runMissing(toCheck, checker, relevanceConcurrency)(
        (c, row) =>
          c.check(row).attempt.flatMap {
            case Left(error) if isFatalVerdict(error) =>
              IO.raiseError(
                new UserError(
                  "Typesafe rejected the request; check TYPESAFE_API_KEY and its permissions",
                  error
                )
              )
            case result => IO.pure(result)
          }
      ) {
        case (row, Right(stored)) =>
          ArticleRepository.saveRelevance(conn, row.id, stored).as(Some(stored))
        case (row, Left(error)) =>
          Output
            .warn(
              s"Relevance check failed for '${row.title}', keeping it: ${error.getMessage}"
            )
            .as(None)
      }
      // Only verdicts jev gave on this run are logged one by one; stored
      // rejections were logged when they were made and are just counted.
      kept <- rows.traverseFilter { row =>
        reusable(row).orElse(checked.getOrElse(row.id, None)) match {
          case Some(stored) if !relevant(stored) =>
            Output
              .info(
                f"Not relevant: '${row.title}' (P(about Scala)=${stored.aboutScala.value: Double}%.2f, P(announcement)=${stored.announcement.value: Double}%.2f)"
              )
              .whenA(checked.contains(row.id))
              .as(None)
          case _ => IO.pure(Some(row))
        }
      }
      notRelevant = rows.size - kept.size
      _ <- Output.info(
        s"Checked ${toCheck.size} articles with jev, reused ${rows.size - toCheck.size} stored; $notRelevant not relevant"
      )
    } yield kept
  }
}
