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

package com.softinio.scalanews.service

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

import com.softinio.scalanews.Bloggers
import com.softinio.scalanews.algebra.Article

object BlogService {

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private object StartDateQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[String]("startDate")
  private object EndDateQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[String]("endDate")

  implicit val uriEncoder: Encoder[org.http4s.Uri] =
    Encoder.encodeString.contramap(_.toString)
  implicit val dateEncoder: Encoder[Date] = Encoder.encodeString.contramap {
    date =>
      val instant = date.toInstant
      val localDate =
        instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate
      localDate.format(dateFormatter)
  }
  implicit val articleEncoder: Encoder[Article] = deriveEncoder[Article]

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "blog" :? StartDateQueryParamMatcher(
          startDateOpt
        ) +& EndDateQueryParamMatcher(endDateOpt) =>
      val today = LocalDate.now()
      val endLocalDate =
        endDateOpt.map(LocalDate.parse(_, dateFormatter)).getOrElse(today)
      val startLocalDate = startDateOpt
        .map(LocalDate.parse(_, dateFormatter))
        .getOrElse(today.minusDays(7))

      // Convert LocalDate to java.util.Date for Bloggers API
      val startDate = Date.from(
        startLocalDate
          .atStartOfDay()
          .atZone(java.time.ZoneId.systemDefault())
          .toInstant
      )
      val endDate = Date.from(
        endLocalDate
          .atTime(23, 59, 59)
          .atZone(java.time.ZoneId.systemDefault())
          .toInstant
      )

      for {
        _ <- logger.info(s"Fetching blog articles from ${startLocalDate
            .format(dateFormatter)} to ${endLocalDate.format(dateFormatter)}")
        articles <- Bloggers.createBlogList(startDate, endDate)
        _ <- logger.info(s"Found ${articles.size} articles")
        response <- Ok(articles.asJson)
      } yield response
  }
}
