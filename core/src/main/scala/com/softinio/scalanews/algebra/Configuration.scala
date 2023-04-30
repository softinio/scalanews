package com.softinio.scalanews.algebra

import java.net.URI

import pureconfig._
import pureconfig.generic.auto._

case class Blog(name: String, url: URI, rss: URI)

case class Configuration(bloggers: List[Blog])

