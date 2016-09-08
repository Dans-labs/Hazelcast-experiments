package nl.knaw.dans.experiments.hazelcast

import java.util.concurrent.{BlockingQueue, TimeUnit}

import com.hazelcast.core.HazelcastInstanceNotActiveException
import rx.lang.scala.{Observable, Scheduler}
import rx.lang.scala.schedulers.NewThreadScheduler
import rx.lang.scala.subscriptions.CompositeSubscription

import scala.concurrent.duration.Duration

package object queue {

  implicit class ObserveBlockingQueue[T](val queue: BlockingQueue[T]) extends AnyVal {
    def observe(timeout: Duration, scheduler: Scheduler = NewThreadScheduler())(running: () => Boolean): Observable[T] = {
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
  }
}
