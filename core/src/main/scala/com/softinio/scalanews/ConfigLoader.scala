package com.softinio.scalanews

import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import cats.effect.IO

import com.softinio.scalanews.algebra.Configuration


object ConfigLoader {
  def load(filePath: String = "config.json"): IO[Configuration] = {
    ConfigSource.file(filePath).loadF[IO, Configuration]()
  }
}
