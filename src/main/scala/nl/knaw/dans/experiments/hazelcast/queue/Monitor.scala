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

import com.hazelcast.Scala._
import com.hazelcast.Scala.client._
import com.hazelcast.client.config.ClientConfig

import scala.io.StdIn

object Monitor extends Util {

  def monitor() = {
    val conf = new ClientConfig()
    serialization.Defaults.register(conf.getSerializationConfig)
    implicit val hz = conf.newClient()

    val masterToSlaveQueue = hz.getQueue[List[Int]]("master-to-slave")
    val slaveToMasterQueue = hz.getQueue[Int]("slave-to-master")

    def size() = {
      println(
        s"""size of queue:
          |  master-to-slave - ${masterToSlaveQueue.size()}
          |  slave-to-master - ${slaveToMasterQueue.size()}
        """.stripMargin)
    }

    var running = true
    while (running) {
      StdIn.readLine() match {
        case "exit" => running = false
        case "size" => size()
      }
    }
    hz.shutdown()
  }
}
