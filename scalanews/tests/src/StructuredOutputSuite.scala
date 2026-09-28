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

import io.circe.{Decoder, Json}
import io.circe.parser.decode
import munit.FunSuite

object StructuredOutputSuite {
  sealed trait Shape
  final case class Circle(radius: Double) extends Shape
      derives JsonSchema,
        Decoder
  final case class Label(text: String, tags: List[String], count: Int)
      extends Shape derives JsonSchema, Decoder
}

class StructuredOutputSuite extends FunSuite {
  import StructuredOutputSuite.*

  private val output = StructuredOutput.oneOf[Shape]
  private val root = output.schema.hcursor

  /** Every object in the schema, at any depth. */
  private def objects(json: Json): List[Json] =
    json.fold(
      Nil,
      _ => Nil,
      _ => Nil,
      _ => Nil,
      _.toList.flatMap(objects),
      obj =>
        (if (obj("type").contains(Json.fromString("object"))) List(json)
         else Nil) ++ obj.values.toList.flatMap(objects)
    )

  test("case class schema - fields typed and all required") {
    val schema = summon[JsonSchema[Label]].schema.hcursor
    assertEquals(
      schema.downField("properties").downField("tags").get[String]("type"),
      Right("array")
    )
    assertEquals(
      schema
        .downField("properties")
        .downField("tags")
        .downField("items")
        .get[String]("type"),
      Right("string")
    )
    assertEquals(
      schema.downField("properties").downField("count").get[String]("type"),
      Right("integer")
    )
    assertEquals(
      schema.get[List[String]]("required"),
      Right(List("text", "tags", "count"))
    )
  }

  test("oneOf - root wraps the result as a required object") {
    assertEquals(root.get[String]("type"), Right("object"))
    assertEquals(root.get[List[String]]("required"), Right(List("result")))
  }

  test("oneOf - one variant per case, tagged by kind") {
    val variants = root
      .downField("properties")
      .downField("result")
      .get[List[Json]]("anyOf")
      .getOrElse(Nil)
    val kinds = variants.flatMap(
      _.hcursor
        .downField("properties")
        .downField("kind")
        .get[List[String]]("enum")
        .getOrElse(Nil)
    )
    assertEquals(kinds, List("Circle", "Label"))
    assert(
      variants.forall(
        _.hcursor.get[List[String]]("required").exists(_.head == "kind")
      )
    )
  }

  test("oneOf - every object disallows additional properties") {
    val all = objects(output.schema)
    assertEquals(all.size, 3)
    assert(
      all.forall(_.hcursor.get[Boolean]("additionalProperties") == Right(false))
    )
  }

  test("oneOf - decodes each case by kind") {
    assertEquals(
      decode("""{"result":{"kind":"Circle","radius":2.5}}""")(using
        output.decoder
      ),
      Right(Circle(2.5))
    )
    assertEquals(
      decode(
        """{"result":{"kind":"Label","text":"x","tags":["a"],"count":1}}"""
      )(using output.decoder),
      Right(Label("x", List("a"), 1))
    )
  }

  test("oneOf - rejects an unknown kind or a missing result") {
    assert(
      decode("""{"result":{"kind":"Square","side":1}}""")(using
        output.decoder
      ).isLeft
    )
    assert(decode("""{"kind":"Circle","radius":1}""")(using output.decoder).isLeft)
  }

  test("toSdkSchema - keeps the derived schema's structure") {
    val sdk = AnthropicClient.toSdkSchema(output)
    val props = sdk._additionalProperties()
    assertEquals(props.get("type").toString, "object")
    assert(props.containsKey("properties"))
    assert(props.containsKey("additionalProperties"))
  }
}
