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

import cats.effect.{ExitCode, IO, Ref}
import com.rometools.rome.feed.synd.{SyndEntry, SyndEntryImpl, SyndFeed, SyndFeedImpl}
import munit.CatsEffectSuite

import java.net.URI
import scala.jdk.CollectionConverters.*

import com.softinio.scalanews.algebra.Blog

class BloggerCheckSuite extends CatsEffectSuite {
  private def blog(name: String, site: String = "https://example.com") =
    Blog(name, URI.create(site), URI.create(s"$site/${name.toLowerCase}.xml"))

  private val alice = blog("Alice", "https://alice.dev")
  private val bob = blog("Bob", "https://bob.dev")

  private def feed(entries: SyndEntry*): SyndFeed = {
    val f = new SyndFeedImpl()
    f.setFeedType("rss_2.0")
    f.setEntries(entries.toList.asJava)
    f
  }

  private def entry(link: String = "https://x.dev/post", dated: Boolean = true) = {
    val e = new SyndEntryImpl()
    e.setLink(link)
    if (dated) e.setPublishedDate(new java.util.Date(0))
    e
  }

  test("configProblems - a valid directory has none") {
    assertEquals(BloggerCheck.configProblems(List(alice, bob)), Nil)
  }

  test("configProblems - URLs must be absolute http(s) URLs") {
    val problems = BloggerCheck.configProblems(
      List(Blog("Carol", URI.create("carol.dev"), URI.create("ftp://carol.dev/feed")))
    )
    assertEquals(problems.size, 2, problems)
    assert(problems.forall(_.startsWith("Carol:")), problems)
  }

  test("configProblems - names and feeds must be unique") {
    val problems = BloggerCheck.configProblems(
      List(alice, alice.copy(name = "alice "), bob.copy(name = "Bobby"), bob)
    )
    assert(problems.exists(_.startsWith("duplicate name 'alice'")), problems)
    assert(problems.exists(_.startsWith("duplicate rss feed")), problems)
  }

  test("newOrChanged - only entries not in the base") {
    val moved = bob.copy(url = URI.create("https://bob.blog"))
    assertEquals(
      BloggerCheck.newOrChanged(List(alice, moved, blog("Dan")), List(alice, bob)),
      List(moved, blog("Dan"))
    )
  }

  test("feedProblem - a feed needs an entry with a link and a date") {
    assertEquals(BloggerCheck.feedProblem(feed(entry())), None)
    assert(BloggerCheck.feedProblem(feed()).isDefined)
    assert(BloggerCheck.feedProblem(feed(entry(dated = false))).isDefined)
    assert(BloggerCheck.feedProblem(feed(entry(link = ""))).isDefined)
  }

  test("feedProblem - an entry dated only by <updated> is usable") {
    val e = entry(dated = false)
    e.setUpdatedDate(new java.util.Date(0))
    assertEquals(BloggerCheck.feedProblem(feed(e)), None)
  }

  test("check - fetches only new feeds when given a base") {
    for {
      fetched <- Ref.of[IO, List[String]](Nil)
      exit <- BloggerCheck.check(
        List(alice, bob),
        Some(List(alice)),
        url => fetched.update(url :: _).as(Right(feed(entry())))
      )
      urls <- fetched.get
    } yield {
      assertEquals(exit, ExitCode.Success)
      assertEquals(urls, List(bob.rss.toString))
    }
  }

  test("check - an unreadable or empty feed is an error") {
    for {
      unreadable <- BloggerCheck.check(
        List(alice),
        None,
        _ => IO.pure(Left(new RuntimeException("Invalid XML")))
      )
      empty <- BloggerCheck.check(List(alice), None, _ => IO.pure(Right(feed())))
    } yield {
      assertEquals(unreadable, ExitCode.Error)
      assertEquals(empty, ExitCode.Error)
    }
  }

  test("check - config problems fail even when no feed is fetched") {
    BloggerCheck
      .check(List(alice, alice), Some(List(alice)), _ => IO.never)
      .map(exit => assertEquals(exit, ExitCode.Error))
  }
}
