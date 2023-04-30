package com.softinio.scalanews

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

import munit.CatsEffectSuite

class ConfigLoaderSuite extends CatsEffectSuite {
  val sampleConfig = FunFixture[Path](
      setup = { test =>
        val filename = test.name.replace(" ", "_")
        val theFile = Files.createTempFile("tmp", s"${filename}.json")
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
      conf <- ConfigLoader.load(file.toString())
    } yield conf.bloggers.head.name == "Salar Rahmanian"
    assertIO(result, true)
  }
}


