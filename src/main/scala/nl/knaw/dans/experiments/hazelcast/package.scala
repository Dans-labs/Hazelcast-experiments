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
package nl.knaw.dans.experiments

import com.hazelcast.Scala._
import com.hazelcast.core.IMap
import rx.lang.scala.Observable

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

package object hazelcast {

  implicit class HazelcastMapObservables[K, V](val map: IMap[K, V]) extends AnyVal {
    def observeMapEvent[T](localOnly: Boolean = false, runOn: ExecutionContext = null)(f: PartialFunction[MapEvent, T]): Observable[T] = {
      Observable(subscriber => {
        val subscription = map.onMapEvents(localOnly, runOn) {
          case event: MapEvent => if (f.isDefinedAt(event)) subscriber.onNext(f(event))
        }

        subscriber.add {
          subscription.cancel(); ()
        }
      })
    }

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
}
