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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

import cats.effect.IO
import cats.effect.Resource
import munit.CatsEffectSuite

class ConfigLoaderSuite extends CatsEffectSuite {
  val sampleBloggerConfig: FunFixture[Path] = FunFixture[Path](
    setup = { test =>
      val filename = test.name.replace(" ", "_")
      val theFile = Files.createTempFile("tmp", s"$filename.json")
      val sampleJson = """
          {
              "bloggers": [
                  {
                      "name": "Salar Rahmanian",
                      "url": "https://www.softinio.com",
                      "rss": "https://www.softinio.com/index.xml"
                  }
              ]
          }

        """
      Files.write(theFile, sampleJson.getBytes(StandardCharsets.UTF_8))
    },
    teardown = { file =>
      Files.deleteIfExists(file)
      ()
    }
  )
  sampleBloggerConfig.test("test loading blogger json config") { file =>
    val result = for {
      conf <- ConfigLoader.load(file.toString)
    } yield conf.bloggers.head.name == "Salar Rahmanian"
    assertIO(result, true)
  }

  /** A config whose one blogger is called `name`, deleted after `body`. */
  private def withConfig[A](name: String)(body: Path => IO[A]): IO[A] =
    Resource
      .make(IO.blocking {
        val file = Files.createTempFile("config", ".json")
        Files.writeString(
          file,
          s"""{"bloggers": [{"name": "$name", "url": "https://example.com", "rss": "https://example.com/rss.xml"}]}"""
        )
      })(file => IO.blocking(Files.deleteIfExists(file)).void)
      .use(body)

  /** Runs `body` with the `SCALA_NEWS_CONFIG` system property set to `path`. */
  private def withOverride[A](path: Path)(body: IO[A]): IO[A] =
    Resource
      .make(IO(sys.props.put("SCALA_NEWS_CONFIG", path.toString)))(previous =>
        IO(
          previous.fold(sys.props.remove("SCALA_NEWS_CONFIG"))(
            sys.props.put("SCALA_NEWS_CONFIG", _)
          )
        ).void
      )
      .surround(body)

  test("load() reads the config SCALA_NEWS_CONFIG names") {
    withConfig("Override") { overridePath =>
      withOverride(overridePath)(IO.defer(ConfigLoader.load()))
        .map(conf => assertEquals(conf.bloggers.map(_.name), List("Override")))
    }
  }

  test("load(path) reads that path even when SCALA_NEWS_CONFIG is set") {
    withConfig("Override") { overridePath =>
      withConfig("Explicit") { explicitPath =>
        withOverride(overridePath)(
          IO.defer(ConfigLoader.load(explicitPath.toString))
        )
          .map(conf =>
            assertEquals(conf.bloggers.map(_.name), List("Explicit"))
          )
      }
    }
  }
}
