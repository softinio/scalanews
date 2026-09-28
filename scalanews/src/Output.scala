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

import cats.effect.IO
import cats.effect.std.Console

/** What the CLI prints: progress on stdout, warnings and errors on stderr, so
  * problems can be told apart from normal output (e.g. `2>errors.log`).
  */
object Output {
  def info(message: String): IO[Unit] = IO.println(message)
  def warn(message: String): IO[Unit] =
    Console[IO].errorln(s"warning: $message")
  def error(message: String): IO[Unit] = Console[IO].errorln(s"error: $message")
}

/** An expected failure the user can fix (a missing or rejected API key, an
  * unsupported model). Reported as one `error:` line, not a stack trace.
  */
final class UserError(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)
