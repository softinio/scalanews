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

package com.softinio.scalanews.algebra

/** How `generate` builds the next newsletter. */
enum GenerateMode {

  /** Ingest the feeds into the database (skipping articles already stored),
    * then build the newsletter from it.
    */
  case Database(dbPath: String, ai: AiMode)

  /** Build the newsletter straight from the feeds, with plain summaries. */
  case Direct
}

/** Whether AI is used in [[GenerateMode.Database]]: jev checks each article's
  * relevance and Claude writes the summaries.
  */
enum AiMode {

  /** jev and Claude results are stored and reused on later runs, unless
    * `refresh` asks both again.
    */
  case Enabled(refresh: Boolean)

  /** Keyword filtering only, and plain summaries (the first sentences of each
    * article).
    */
  case Disabled
}
