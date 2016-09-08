package nl.knaw.dans.experiments.hazelcast.queue

import com.hazelcast.Scala._
import com.hazelcast.config.Config

import scala.io.StdIn

object ClusterMember {

  def member() = {
    val conf = new Config()
    serialization.Defaults.register(conf.getSerializationConfig)
    val hz = conf.newInstance()

    var running = true
    while (running) {
      StdIn.readLine(">>> ") match {
        case "exit" => running = false
      }
    }
    hz.shutdown()
  }
}
