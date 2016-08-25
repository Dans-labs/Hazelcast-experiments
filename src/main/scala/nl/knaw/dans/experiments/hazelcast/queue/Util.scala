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

import java.util.concurrent.BlockingQueue

import com.hazelcast.core.HazelcastInstanceNotActiveException
import rx.lang.scala.{Observable, Scheduler}
import rx.lang.scala.schedulers.NewThreadScheduler

class Util {

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
}
