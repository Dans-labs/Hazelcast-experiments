package nl.knaw.dans.experiments.hazelcast.queue

import java.util.UUID

case class Message[T](header: Header, body: Body[T])
case class Header(id: UUID, version: String, messageType: String, returnQueue: String)
abstract sealed class Body[T](content: T)
case class ListBody(list: List[Int]) extends Body(list)
case class SumBody(sum: Int) extends Body(sum)
