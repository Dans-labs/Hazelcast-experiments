#Hazelcast

**This page reports on what I did with Hazelcast for this repository.**

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
    subscriber.add(subscription)
    subscriber.add(worker)
  })
}
```

#Future research topics
* proper shutdown strategies for clients (without loosing data)
* data persistence when the cluster dies or one node in the cluster dies
