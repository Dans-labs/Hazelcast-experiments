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
package nl.knaw.dans.experiments.hazelcast.map

import com.hazelcast.Scala._
import com.hazelcast.Scala.client._
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.config._
import com.hazelcast.core.HazelcastInstance
import nl.knaw.dans.experiments.hazelcast._

import scala.collection.JavaConverters._
import scala.io.StdIn

class Experiment {
  def shutdown()(implicit hz: HazelcastInstance): Unit = {
    shutdown(() => ())
  }

  def shutdown(hook: () => Unit)(implicit hz: HazelcastInstance): Unit = {
    println("shut down?")
    val response = StdIn.readLine()
    response match {
      case "yes" | "y" =>
        println("shutting down...")
        hook()
        hz.shutdown()
      case "no" | "n" => println("I will keep running indefinitely until you kill me.")
    }
  }
}

object FillMap extends Experiment {

  def fillMap() = {
    val conf = new Config()
    serialization.Defaults.register(conf.getSerializationConfig)
    implicit val hz = conf.newInstance()

    val customers = hz.getMap[Int, String]("customers").asScala

    List("Jan", "Joke", "Paul", "Richard", "Vesa", "Henk")
      .zipWithIndex
      .foreach { case (name, index) => customers.put(index, name) }

    customers.foreach { case (index, name) =>
      println(s"$name has index $index")
      println(s"  ${Thread.currentThread().getName}")
    }

    println("Filled map")

    shutdown()
  }
}

object PrintMap extends Experiment {

  def printMap() = {
    val conf = new Config()
    serialization.Defaults.register(conf.getSerializationConfig)
    implicit val hz = conf.newInstance()

    val customers = hz.getMap[Int, String]("customers").asScala

    customers.foreach { case (index, name) =>
      println(s"$name has index $index")
      println(s"  ${Thread.currentThread().getName}")
    }

    println("end Experiments2")

    shutdown()
  }
}

object ClientMap extends Experiment {

  def clientMap() = {
    val conf = new ClientConfig
    serialization.Defaults.register(conf.getSerializationConfig)
    implicit val hz = conf.newClient()

    val customers = hz.getMap[Int, String]("customers")
    println(customers.size())
    hz.shutdown()

    shutdown()
  }
}

object WatchMap extends Experiment {

  def watchMap() = {
    val conf = new Config()
    serialization.Defaults.register(conf.getSerializationConfig)
    implicit val hz = conf.newInstance()

    val customers = hz.getMap[Int, String]("customers")

    val subscription = customers.observeEntryEvents() {
      case EntryAdded(k, v) => s"Added to customers map: ($k, $v)"
    }
      .doOnSubscribe(println("listening to customers map"))
      .slidingBuffer(2, 2)
      .subscribe(println(_), e => println(e.getMessage), () => println("completed"))

    shutdown(() => subscription.unsubscribe())
  }
}
