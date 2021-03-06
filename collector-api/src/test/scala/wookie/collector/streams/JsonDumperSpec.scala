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

import java.util.concurrent.{Future => JFuture}

import argonaut._
import Argonaut._
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata, KafkaProducer}
import org.apache.kafka.common.TopicPartition
import org.http4s.client.Client
import org.http4s.{ParseException, Response, Uri, Request}
import org.junit.runner.RunWith
import org.mockito.Matchers
import org.specs2.ScalaCheck
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import scodec.bits.ByteVector

import scalaz.concurrent.Task


case class TestObj(x: String, y: Int)
/**
  * Created by ljastrze on 11/24/15.
  */
@RunWith(classOf[JUnitRunner])
class JsonDumperSpec extends Specification with ScalaCheck with Mockito {

  val sampleJson = Response(body=scalaz.stream.Process.eval(Task.now(ByteVector("""{ "x": "Alfa", "y": 10 }""".getBytes))))
  val incorrectJson = Response(body=scalaz.stream.Process.eval(Task.now(ByteVector(""""x": "Alfa", "y": 10""".getBytes))))

  "Should dump json objects" in new context {
    MockedConfig.httpClient.apply(req) returns Task.now(sampleJson)

    val dumper = JsonDumper(decoder, encoder)
    val pipe = dumper.push(req, List("a"), (a: String) => (a, a))
    val result = pipe(MockedConfig).run.attemptRun

    result.isRight must_== true
    there was one(MockedConfig.httpClient).apply(req) andThen one(MockedConfig.kafkaProducer).send(Matchers.any[ProducerRecord[String, String]]) andThen
      one(mockedFuture).get andThen one(MockedConfig.kafkaProducer).close andThen one(MockedConfig.httpClient).shutdown()
  }

  "Should not dump incorrect json objects" in new context {
    MockedConfig.httpClient.apply(req) returns Task.now(incorrectJson)

    val dumper = JsonDumper(decoder, encoder)
    val pipe = dumper.push(req, List("a"), (a: String) => (a, a))
    val result = pipe(MockedConfig).run.attemptRun

    result.isLeft must_== true
    result.toEither.left.get.getClass must_== classOf[ParseException]
    there was one(MockedConfig.httpClient).apply(req) andThen one(MockedConfig.httpClient).shutdown()
    there was no(MockedConfig.kafkaProducer).send(Matchers.any[ProducerRecord[String, String]])
    there was no(MockedConfig.kafkaProducer).close
  }

  trait context extends Scope {
    object MockedConfig extends Config {
      lazy val httpClient = mock[Client]
      lazy val kafkaProducer = mock[KafkaProducer[String, String]]
    }

    private val codec = casecodec2(TestObj.apply, TestObj.unapply)("x", "y")
    val decoder: DecodeJson[TestObj] = codec
    val encoder: EncodeJson[TestObj] = codec
    val mockedFuture = mock[JFuture[RecordMetadata]]

    MockedConfig.httpClient.shutdown() returns Task.now(())
    MockedConfig.kafkaProducer.send(Matchers.any[ProducerRecord[String, String]]) returns mockedFuture
    mockedFuture.get returns new RecordMetadata(new TopicPartition("XXX", 1), 1L, 2L)
  }

  val req = Request(uri=Uri.uri("http://xxx.com/v1/2"))

}
