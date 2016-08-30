package nl.knaw.dans.experiments.hazelcast.client

import com.hazelcast.Scala._
import com.hazelcast.config._

import scala.io.StdIn

object Server {

  def server() = {
    val conf = new Config()
    serialization.Defaults.register(conf.getSerializationConfig)
    val hz = conf.newInstance()

    println("server up")

    StdIn.readLine("shut down???")

    hz.shutdown()
  }
}
