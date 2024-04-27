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

import munit.CatsEffectSuite

class ConfigLoaderSuite extends CatsEffectSuite {
  val sampleConfig: FunFixture[Path] = FunFixture[Path](
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
  sampleConfig.test("test loading json config") { file =>
    val result = for {
      conf <- ConfigLoader.load(file.toString)
    } yield conf.bloggers.head.name == "Salar Rahmanian"
    assertIO(result, true)
  }
}
