package com.softinio.scalanews

import java.net.URI

import munit.CatsEffectSuite

import com.softinio.scalanews.algebra.Blog

class BloggersSuite extends CatsEffectSuite {
  test("generateDirectory - test the new blogger directory is generated") {
    val blog = Blog("Salar Rahmanian", new URI("https://www.softinio.com"), new URI("https://www.softinio.com/index.xml"))
    val obtained = for {
      result <- Bloggers.generateDirectory(List(blog)) 
    } yield (result.contains("| Salar Rahmanian | <https://www.softinio.com> | [rss feed](https://www.softinio.com/index.xml) |"))
    assertIO(obtained, true)
  }
}

