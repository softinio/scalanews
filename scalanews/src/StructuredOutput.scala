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

import io.circe.{Decoder, DecodingFailure, Json}

import scala.compiletime.{constValueTuple, erasedValue, summonInline}
import scala.deriving.Mirror

/** The JSON schema of a type sent to Claude as a structured-output field. */
trait JsonSchema[A] {
  def schema: Json
}

object JsonSchema {

  private def primitive[A](jsonType: String): JsonSchema[A] =
    new JsonSchema[A] {
      val schema: Json = Json.obj("type" -> Json.fromString(jsonType))
    }

  given JsonSchema[String] = primitive("string")
  given JsonSchema[Boolean] = primitive("boolean")
  given JsonSchema[Int] = primitive("integer")
  given JsonSchema[Long] = primitive("integer")
  given JsonSchema[Double] = primitive("number")

  given [A](using items: JsonSchema[A]): JsonSchema[List[A]] =
    new JsonSchema[List[A]] {
      val schema: Json =
        Json.obj("type" -> Json.fromString("array"), "items" -> items.schema)
    }

  /** A case class is an object whose fields are all required. Structured
    * outputs also require `additionalProperties: false` on every object.
    */
  inline def derived[A](using m: Mirror.ProductOf[A]): JsonSchema[A] = {
    val fields = labels[m.MirroredElemLabels].zip(schemas[m.MirroredElemTypes])
    new JsonSchema[A] {
      val schema: Json = objectSchema(fields)
    }
  }

  private[scalanews] def objectSchema(fields: List[(String, Json)]): Json =
    Json.obj(
      "type" -> Json.fromString("object"),
      "properties" -> Json.obj(fields*),
      "required" -> Json.arr(fields.map((name, _) => Json.fromString(name))*),
      "additionalProperties" -> Json.False
    )

  private[scalanews] inline def labels[T <: Tuple]: List[String] =
    constValueTuple[T].toList.map(_.toString)

  private inline def schemas[T <: Tuple]: List[Json] =
    inline erasedValue[T] match {
      case _: EmptyTuple     => Nil
      case _: (head *: tail) =>
        summonInline[JsonSchema[head]].schema :: schemas[tail]
    }
}

/** The schema Claude's reply is constrained to, and the decoder for that reply,
  * both derived from the same type so they can't drift apart.
  */
final case class StructuredOutput[A](schema: Json, decoder: Decoder[A])

object StructuredOutput {

  /** For a sealed trait of case classes: Claude replies with one case, tagged
    * by a `kind` field holding the case's name. The reply is wrapped as
    * `{"result": ...}` because a structured output's root must be an object.
    */
  inline def oneOf[A](using m: Mirror.SumOf[A]): StructuredOutput[A] = {
    val kinds = JsonSchema.labels[m.MirroredElemLabels]
    val caseSchemas = schemas[m.MirroredElemTypes]
    val caseDecoders = decoders[m.MirroredElemTypes]

    val variants =
      kinds.zip(caseSchemas).map((kind, schema) => tagged(kind, schema))
    val schema = JsonSchema.objectSchema(
      List("result" -> Json.obj("anyOf" -> Json.arr(variants*)))
    )

    val decoder = Decoder.instance[A] { cursor =>
      val result = cursor.downField("result")
      result.get[String]("kind").flatMap { kind =>
        kinds.indexOf(kind) match {
          case -1 =>
            Left(DecodingFailure(s"Unknown kind: $kind", result.history))
          case i => result.as(using caseDecoders(i).asInstanceOf[Decoder[A]])
        }
      }
    }

    StructuredOutput(schema, decoder)
  }

  /** Adds the required `kind` tag to a case's object schema. */
  private def tagged(kind: String, caseSchema: Json): Json = {
    val kindSchema = Json.obj(
      "type" -> Json.fromString("string"),
      "enum" -> Json.arr(Json.fromString(kind))
    )
    caseSchema.mapObject { obj =>
      obj
        .add(
          "properties",
          obj("properties")
            .flatMap(_.asObject)
            .fold(Json.obj("kind" -> kindSchema))(props =>
              Json.fromJsonObject(("kind" -> kindSchema) +: props)
            )
        )
        .add(
          "required",
          Json.arr(
            Json.fromString("kind") :: obj("required")
              .flatMap(_.asArray)
              .fold(List.empty[Json])(_.toList)*
          )
        )
    }
  }

  private inline def schemas[T <: Tuple]: List[Json] =
    inline erasedValue[T] match {
      case _: EmptyTuple     => Nil
      case _: (head *: tail) =>
        summonInline[JsonSchema[head]].schema :: schemas[tail]
    }

  private inline def decoders[T <: Tuple]: List[Decoder[?]] =
    inline erasedValue[T] match {
      case _: EmptyTuple     => Nil
      case _: (head *: tail) =>
        summonInline[Decoder[head]] :: decoders[tail]
    }
}
