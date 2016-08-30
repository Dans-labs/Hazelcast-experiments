package nl.knaw.dans.experiments.hazelcast.client

object Command {

  def main(args: Array[String]): Unit = {
    args(0) match {
      case "server" => Server.server()
      case "client" => Client.client()
      case "client2" => Client2.client()
    }
  }
}
