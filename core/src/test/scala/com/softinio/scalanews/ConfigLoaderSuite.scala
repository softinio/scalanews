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
import org.http4s.Uri

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
  val sampleEventConfig: FunFixture[Path] = FunFixture[Path](
    setup = { test =>
      val filename = test.name.replace(" ", "_")
      val theFile = Files.createTempFile("tmp", s"$filename.json")
      val sampleJson = """
          {
            "meetups": [
              {
                "name": "SF Scala",
                "meetup-url": "http://www.meetup.com/SF-Scala/",
                "luma-url": "https://lu.ma/scala",
                "social-media-url": null,
                "other-url": "https://www.sfscala.org/",
                "locations": [
                  {
                    "city": "San Francisco",
                    "state": "California",
                    "country": "USA"
                  }
                ],
                "description": "SF Scala is a group for functional programmers who use Scala to build software who are based in San Francisco or nearby. We welcome programmers of all skill levels to our events."
              }
            ],
            "conferences": []
          }

        """
      Files.write(theFile, sampleJson.getBytes(StandardCharsets.UTF_8))
    },
    teardown = { eventFile =>
      Files.deleteIfExists(eventFile)
      ()
    }
  )
  sampleBloggerConfig.test("test loading blogger json config") { file =>
    val result = for {
      conf <- ConfigLoader.load(file.toString)
    } yield conf.bloggers.head.name == "Salar Rahmanian"
    assertIO(result, true)
  }
  sampleEventConfig.test("test loading events json config") { eventFile =>
    val result = for {
      conf <- ConfigLoader.loadEventsConfig(eventFile.toString)
    } yield conf.meetups.head.meetupUrl == Uri
      .fromString("http://www.meetup.com/SF-Scala/")
      .toOption
    assertIO(result, true)
  }
}
