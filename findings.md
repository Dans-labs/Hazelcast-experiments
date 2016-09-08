#Hazelcast

**This page reports on the research I did with Hazelcast in this repository.**

##Introduction
[Hazelcast](https://hazelcast.org/) is a piece of 'middleware' that provides distributed data structures that can be shared between multiple JVMs. The data is stored in a distributed way and shared over all the nodes in cluster. Clients can connect to the cluster and use the data structures to their liking.

A client connects to the cluster it operates on using the following code:
```scala
import com.hazelcast.Scala.client._
import com.hazelcast.Scala.serialization
import com.hazelcast.client.config.ClientConfig

val conf = new ClientConfig()
serialization.Defaults.register(conf.getSerializationConfig)
val hz = conf.newClient()
```

To set up a cluster (or add a node to a cluster) use the following code: 
```scala
import com.hazelcast.Scala._
import com.hazelcast.Scala.serialization
import com.hazelcast.config.Config

val conf = new Config()
serialization.Defaults.register(conf.getSerializationConfig)
val hz = conf.getInstance()
```

If there is already a cluster present in the network, the node will be added to that cluster directly. One can also connect to an outside cluster by configuring this in the `hazelcast.xml` or programmatically (not done in this project as of now). For configuration of the cluster, we refer to the [documentation](http://docs.hazelcast.org/docs/3.5/manual/html/networkconfiguration.html).

The `hazelcast.xml` is started with the `<hazelcast>` tag. Further fragments of `hazelcast.xml` will discard this boilerplate.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<hazelcast
        xsi:schemaLocation ="http://www.hazelcast.com/schema/config
http://www.hazelcast.com/schema/config/hazelcast-config-3.0.xsd "
        xmlns ="http://www.hazelcast.com/schema/config"
        xmlns:xsi ="http://www.w3.org/2001/XMLSchema-instance">
    
</hazelcast>

```

To register a data structure in the cluster at startup, we declare its type and name in the `hazelcast.xml`, together with some additional properties (if one so desires).

```xml
<queue name="master-to-slave"/>
```

Data structures can be acquired programmatically using the following code or a variant of that with the appropriate data structure and name:

```scala
val masterToSlaveQueue = hz.getQueue[List[Int]]("master-to-slave")
val customers = hz.getMap[Int, String]("customers")
```

All data structures provided by Hazelcast implement the standard interfaces of these data structures as they are defined in Java (both the 'synchronous' and 'concurrent' variants) and can therefore be used as such.

Data structures can be created by both cluster members and clients ([see experiment](https://github.com/rvanheest-DANS-KNAW/Hazelcast-experiments/tree/master/src/main/scala/nl/knaw/dans/experiments/hazelcast/client)).

##Hazelcast and RxJava
Besides the standard methods on these data structures, Hazelcast provides event handlers that notify the listeners of events that occur on the observed data structure. In our research we developed a small wrapper around these event handlers, such that they can be transformed into [RxJava](https://github.com/ReactiveX/RxJava) `Observable`s for further processing in a 'reactive' way.

```scala
implicit class HazelcastMapObservables[K, V](val map: IMap[K, V]) extends AnyVal {
  def observeEntryEvents[T](localOnly: Boolean = false, runOn: ExecutionContext = null)(f: PartialFunction[EntryEvent[K, V], T]): Observable[T] = {
    Observable(subscriber => {
      val subscription = map.onEntryEvents(localOnly, runOn) {
        case event: EntryEvent[K, V] => if (f.isDefinedAt(event)) subscriber.onNext(f(event))
      }
  
      subscriber.add {
        subscription.cancel(); ()
      }
    })
  }
}
```

As this wrapper is defined as an extension method, we can directly use it on a predefined map:
 
```scala
customers.observeEntryEvents() { case EntryAdded(k, v) => s"Added to customers map: ($k, $v)" }
  .doOnSubscribe(println("listening to customers map"))
  .subscribe(println(_), e => println(e.getMessage), () => println("completed"))
```

Of course this extension method can be easily rewritten to support other types of events on `Map` and other data structures.

A `Queue` can be polled using the [`Queue.take()`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/BlockingQueue.html#take--) method, which blocks until an element is present in the queue. We wrapped this as well in an `Observable` in order to let this blocking process run on a separate `Scheduler`. The task of calling `Queue.take()` is scheduled recursively, such that it will poll the queue indefinitely (or until the `Observer` unsubscribes).

```scala
def pollQueue[T](queue: BlockingQueue[T], scheduler: Scheduler = NewThreadScheduler()): Observable[T] = {
  Observable(subscriber => {
    val worker = scheduler.createWorker
    val subscription = worker.scheduleRec {
      try {
        val t = queue.take()
        println(s"received: $t")
        subscriber.onNext(t)
      }
      catch {
        case e: HazelcastInstanceNotActiveException => subscriber.onCompleted()
        case e: Throwable => println("  caught: " + e.getMessage); subscriber.onError(e)
      }
    }
    subscriber.add(CompositeSubscription(subscription, worker))
  })
}
```

The downside of this implementation is that we cannot cancel a `queue.take()` in any other way than to interrupt the thread by unsubscribing from the worker. This however causes an unintended `InterruptedException` as a terminating `onError` event. The solution is to *not* wait indefinitely until the queue receives a next value. Instead we use the `queue.poll(timeout)` operator, which polls the queue for at most `timeout` units (seconds, milliseconds, ...) and either returns a next value in the queue or a `null` if the operation was timed out without yielding any result. We discard these `null`s and only propagate the successful polls. After each poll cycle (which takes at most `timeout` units of time) it is checked whether to proceed with another poll or to call `onCompleted` on the stream. This decision is taken based on the `running` function, which is implemented by the caller of `pollQueue`.

```scala
def pollQueue[T](queue: BlockingQueue[T], timeout: Duration, scheduler: Scheduler = NewThreadScheduler())(running: () => Boolean): Observable[T] = {
  Observable(subscriber => {
    val worker = scheduler.createWorker
    val subscription = worker.scheduleRec {
      try {
        if (running()) {
          val t = Option(queue.poll(timeout.toMillis, TimeUnit.MILLISECONDS))
          println(s"received: $t")
          t.foreach(subscriber.onNext)
        }
        else subscriber.onCompleted()
      }
      catch {
        case e: HazelcastInstanceNotActiveException => subscriber.onCompleted()
        case e: Throwable => println(s"  caught ${e.getClass.getSimpleName}: ${e.getMessage}"); subscriber.onError(e)
      }
    }
    subscriber.add(CompositeSubscription(subscription, worker))
  })
}
```

##Demo applications
This section describes the demo applications created in this study.

###Names Map
A simple application (see `nl.knaw.dans.experiments.hazelcast.map`) that puts names in a `Map` and reads the values from the `Map` in another part of the application. The program can be run in 4 modes:

* "fill" - fills the `Map` with a number of names
* "print" - reads all the values from the `Map`
* "watch" - 'subscribes' to the add-event stream of the `Map` and reports on elements being added
* "client" - connects to the cluster as a client, reads the size of the `Map` and disconnects immediately thereafter from the cluster.

One remarkable thing we found is that when the `Map` is acquired from Hazelcast, one can (at least in the Scala API) use higher-order functions to iterate over it. When the `Map` is taken as a `IMap` (the Hazelcast implementation), lambdas in the higher-order functions are executed distributed. If classic loops are used instead, the code inside the loop will however be executed on the single instance on which the code is ran.

###Bidirectional server-client communication
The package `nl.knaw.dans.experiments.hazelcast.queue` defines a server-client communication demo where a server (called `Master`) generates lists of random numbers and puts them in a queue (called `master-to-slave`). A client (called `Slave`) observes this queue and polls the list from the queue, performs a task on this input data and stores the result in another queue (called `slave-to-master`). `Master` observes this latter queue and in this way gets the result of the 'task'.

It should be noted that there can be any number of servers and clients in this scenario. In this case, there is no guarantee of ordering regarding the requests/responses. Some tasks may take longer than others and tasks may be executed in a distributed way.

A corresponding open question is how to match requests and responses. Though we did not yet implement this, we suggest a mapping that contains a UUID as the key and a `null` or the actual response as its value. The request is then send together with this UUID, such that the client can use this in its response.

Another open issue is the input and output format of tasks. While objects and classes may seem most obvious, it should also be considered that these need to be on the classpath of both server and client (which may very well be in separate JARs) and that over time changes to these classes are likely to happen. This latter issue calls for a versioning of the classes/objects, whereas the former calls for a more uniform format to send data from one service to the other. We suggest to look into a universally parsable format such as JSON or XML, which can be constructed and serialized by the sender and can be parsed, interpreted and transformed into appropriate classes/objects by the receiver.

Further and more general issues involve:
* resubmitting a task when no response has arrived after a certain amount of time
* data persistence when either a node or the whole cluster dies
