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

object Slave {

  def slave() = {
    val conf = new ClientConfig()
    serialization.Defaults.register(conf.getSerializationConfig)
    val hz = conf.newClient()

    val masterToSlaveQueue = hz.getQueue[List[Int]]("master-to-slave")
    val slaveToMasterQueue = hz.getQueue[Int]("slave-to-master")

    val running = new AtomicBoolean(true)
    val shutdownLatch = new CountDownLatch(1)

    def send(i: Int) = {
      println(s"responding: $i")
      slaveToMasterQueue.put(i)
    }

    masterToSlaveQueue.observe(5 seconds)(running.get)
      .map(_.sum)
      .doOnNext(_ => {
        val sleep = new Random().nextGaussian() * 10000
        Thread.sleep(math.abs(sleep.toLong))
      })
      .doOnSubscribe(println("listening to 'master-to-slave' queue"))
      .doOnError(e => println(s"exception in Slave/pollQueue: ${e.getMessage}"))
      .retry
      .subscribe(
        send,
        e => println(s"SHOULD NOT OCCUR: $e"),
        () => {
          println("completed")
          shutdownLatch.countDown()
        })

    while (running.get()) {
      StdIn.readLine(">>> ") match {
        case "exit" => running.compareAndSet(true, false)
        case s => println(s"$s is not a command")
      }
    }
    shutdownLatch.await()
    hz.shutdown()
  }
}
