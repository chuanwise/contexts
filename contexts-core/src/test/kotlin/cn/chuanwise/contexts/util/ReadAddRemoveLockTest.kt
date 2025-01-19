/*
 * Copyright 2025 Chuanwise and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.chuanwise.contexts.util

import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

@OptIn(ContextsInternalApi::class)
class ReadAddRemoveLockTest {
    @Test
    fun onReadAddRemove() {
        val lock = ReentrantReadAddRemoveLock()
        val pool = Executors.newCachedThreadPool()

        pool.submit {
            println("A: acquire READ lock")
            lock.readLock.lock()
            println("A: acquired READ lock")

            Thread.sleep(1000)

            println("A: release READ lock")
            lock.readLock.unlock()
            println("A: released READ lock")
        }

        Thread.sleep(1000)
        pool.submit {
            println("B: acquire ADD lock")
            lock.addLock.lock()
            println("B: acquired ADD lock")

            Thread.sleep(1000)

            println("B: release ADD lock")
            lock.addLock.unlock()
            println("B: released ADD lock")
        }

        Thread.sleep(1000)
        pool.submit {
            println("C: acquire REMOVE lock")
            lock.readLock.lock()
            println("C: acquired REMOVE lock")

            Thread.sleep(1000)

            println("C: release REMOVE lock")
            lock.readLock.unlock()
            println("C: released REMOVE lock")
        }

        pool.submit {
            println("D: acquire ADD lock")
            lock.addLock.lock()
            println("D: acquired ADD lock")

            Thread.sleep(1000)

            println("D: release ADD lock")
            lock.addLock.unlock()
            println("D: released ADD lock")
        }

        pool.shutdown()
    }
}