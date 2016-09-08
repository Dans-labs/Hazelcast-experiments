/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.experiments.hazelcast.queue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

import com.hazelcast.Scala._
import com.hazelcast.Scala.client._
import com.hazelcast.client.config.ClientConfig

import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.Random

object Master {

  def master() = {
    val conf = new ClientConfig()
    serialization.Defaults.register(conf.getSerializationConfig)
    val hz = conf.newClient()

    val masterToSlaveQueue = hz.getQueue[List[Int]]("master-to-slave")
    val slaveToMasterQueue = hz.getQueue[Int]("slave-to-master")

    val running = new AtomicBoolean(true)
    val shutdownLatch = new CountDownLatch(1)

    slaveToMasterQueue.observe(5 seconds)(running.get)
      .doOnSubscribe(println("listening to 'slave-to-master' queue"))
      .doOnError(e => println(s"exception in Master/pollQueue: ${e.getMessage}"))
      .retry
      .subscribe(
        result => { }, // printing already happens in pollQueue
        e => println(s"SHOULD NOT OCCUR: $e"),
        () => { println("completed"); shutdownLatch.countDown() })

    def genList: List[Int] = {
      val n = math.max(Random.nextInt(7), 1)
      def value = Random.nextInt(8)

      Seq.fill(n)(value).toList
    }

    def send(list: List[Int]) = {
      println(s"sending list: ${list.mkString("[", ", ", "]")}")
      masterToSlaveQueue.put(list)
    }

    while (running.get()) {
      val line = StdIn.readLine("Give a list of numbers (space separated)...\n")
      line match {
        case "exit" => running.compareAndSet(true, false)
        case s if s.startsWith("produce ") =>
          val n = s.stripPrefix("produce ").toInt
          println(s"producing $n lists:")
          (0 until n).map(_ => genList).foreach(send)
        case l =>
          val numbers = l.split(' ').toList.map(_.toInt)
          send(numbers)
      }
    }
    shutdownLatch.await()
    hz.shutdown()
  }
}
