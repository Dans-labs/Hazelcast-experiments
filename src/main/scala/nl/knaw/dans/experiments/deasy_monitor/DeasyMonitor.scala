package nl.knaw.dans.experiments.deasy_monitor

import java.util.UUID

import scala.collection.JavaConverters._
import com.hazelcast.Scala.client._
import com.hazelcast.client.config.ClientConfig

import scala.io.StdIn

object DeasyMonitor extends App {

  val conf = new ClientConfig
  // make sure to have a tunnel set up to deasy!
  conf.getNetworkConfig.addAddress("127.0.0.1:15701")
  val hz = conf.newClient()

  mainREPL()

  hz.shutdown()

  def mainREPL(): Unit = {
    var running = true
    while (running) {
      StdIn.readLine(">>> ") match {
        case "exit" => running = false
        case "cluster" => println(hz.getCluster.getMembers)
        case "structures" => println(Option(hz.getDistributedObjects.asScala).filterNot(_.isEmpty).map(_.mkString("\n")).getOrElse("no structures"))
        case "pid-inbox" => pidInboxREPL()
        case s if s startsWith "result-map " => resultMapREPL(s.replace("result-map ", ""))
        case s => println(s"unknown command: $s")
      }
    }
  }

  def pidInboxREPL(): Unit = {
    lazy val inbox = hz.getQueue[String]("pid_generator_hazelcast_inbox")

    var running = true
    while (running) {
      StdIn.readLine("[pid-inbox] ") match {
        case "done" => running = false
        case "get" => println(Option(inbox.asScala).filterNot(_.isEmpty).map(_.mkString("\n")).getOrElse("no content"))
        case s if s startsWith "generate " =>
          val pidType = s.replace("generates ", "")
          val (uuid, json) = mkJSON(pidType)
          inbox.put(json)
          println(s"response to 'generate $pidType' can be aquired using uuid $uuid")
        case s => println(s"unknown command: $s")
      }
    }

    def mkJSON(pidType: String): (UUID, String) = {
      val uuid = UUID.randomUUID()
      val responseDS = "pid-result"

      (uuid, s"""{"head":{"requestID":"$uuid","responseDS":"$responseDS"},"body":{"pidType":"$pidType"}}""")
    }
  }

  def resultMapREPL(name: String): Unit = {
    lazy val map = hz.getMap[String, String](name)

    var running = true
    while (running) {
      StdIn.readLine(s"[$name] ") match {
        case "done" => running = false
        case "keys" => println(map.keySet().asScala.mkString("\n"))
        case "get" => println(map.asScala.mkString("\n"))
        case s if s startsWith "get " => println(map.get(s.replace("get ", "")))
        case s => println(s"unknown command: $s")
      }
    }
  }
}
