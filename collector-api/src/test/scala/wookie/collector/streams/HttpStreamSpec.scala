/*
 * Copyright (C) 2014-2015 by Nokia.
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package wookie.collector.streams

import org.http4s.{Response, Method}
import org.http4s.client.Client
import org.junit.runner.RunWith
import org.scalacheck.{Gen, Arbitrary}
import org.specs2.ScalaCheck
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scalaz.concurrent.Task

/**
  * Created by ljastrze on 11/22/15.
  */
@RunWith(classOf[JUnitRunner])
class HttpStreamSpec extends Specification with ScalaCheck with Mockito {

  def urls: Arbitrary[String] = {
    Arbitrary(for {
      prefix <- Gen.oneOf("http://", "https://", "s3://")
      suffix <- Gen.containerOf[List, String](Gen.oneOf(Gen.const("."), Gen.const("/"), Gen.identifier))
    } yield prefix + suffix.mkString(""))
  }

  private def schemeGen = Arbitrary(Gen.oneOf("http", "https", "s3"))

  private def hostGen = Arbitrary(
    for {
      host <- Gen.containerOf[List, String](Gen.oneOf(Gen.const("."), Gen.identifier))
    } yield host.mkString(""))

  private def pathGen = Arbitrary(
    for {
      path <- Gen.containerOf[List, String] (Gen.oneOf(Gen.const("/"), Gen.identifier))
    } yield path.mkString(""))

  private def alphaNumericMap: Arbitrary[Map[String, String]] = {
    val tupleGen = for {
      a <- Gen.identifier
      value <- Gen.oneOf(Gen.const('='), Gen.alphaNumChar)
      b <- Gen.containerOf[Array, Char](value)
    } yield (a, new String(b))
    Arbitrary(Gen.mapOf(tupleGen))
  }

  "Should parse request" >> prop { (scheme: String, host: String, path: String, query: Map[String, String]) =>
    val res = HttpStream.createRequest(s"$scheme://$host/$path", query)
    val req = res.toOption.get
    req.params must_== query
    req.method must_== Method.GET
    req.uri.path.toString must_== s"/$path"
    req.uri.authority.get.toString must_== host
    req.uri.scheme.get.toString must_== scheme
  }.setArbitraries(schemeGen, hostGen, pathGen, alphaNumericMap)

  "Should create source stream out of http" >> prop { (scheme: String, host: String, path: String, query: Map[String, String]) =>
    val request = HttpStream.createRequest(s"$scheme://$host/$path", query).toOption.get
    val resp = Response()
    val cli = mock[Client]
    cli.apply(request) returns Task.now(resp)
    cli.shutdown() returns Task.now(())

    val process = HttpStream.source(request)(cli)
    val result = process.run.attemptRun
    there was one(cli).apply(request) andThen one(cli).shutdown()
    result.isRight must_== true
  }

}
