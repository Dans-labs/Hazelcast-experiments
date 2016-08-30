package nl.knaw.dans.experiments.hazelcast.client

import com.hazelcast.Scala._
import com.hazelcast.Scala.client._
import com.hazelcast.client.config.ClientConfig

import scala.io.StdIn

object Client {

  def client() = {
    val conf = new ClientConfig
    serialization.Defaults.register(conf.getSerializationConfig)
    val hz = conf.newClient()

    println("client up")

    val map = hz.getMap[String, String]("client_registered_map")
    map.put("abc", "def")

    println(s"client registered a map: $map")

    StdIn.readLine("shut down???")

    hz.shutdown()
  }
}
