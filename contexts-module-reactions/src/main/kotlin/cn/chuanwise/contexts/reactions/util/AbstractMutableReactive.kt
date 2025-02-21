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

package cn.chuanwise.contexts.reactions.util

import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.ResolvableType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@ContextsInternalApi
abstract class AbstractMutableReactive<T>(type: ResolvableType<T>) : AbstractReactive<T>(type), MutableReactive<T> {
    private inner class MutableEntryImpl(
        override val value: ReactiveWriteObserver<T>
    ) : MutableEntry<ReactiveWriteObserver<T>> {
        private val mutableRemoved = AtomicBoolean(false)
        override val isRemoved: Boolean get() = mutableRemoved.get()

        override fun tryRemove(): Boolean {
            val result = mutableRemoved.compareAndSet(false, true)
            if (result) {
                writeObservers.remove(value)
            }
            return result
        }
    }

    private val writeObservers = ConcurrentHashMap<ReactiveWriteObserver<T>, MutableEntryImpl>()

    protected fun onValueWrite(value: T) {
        writeObservers.keys.forEach {
            it.onValueWrite(this, value)
        }
    }

    override fun addWriteObserver(observer: ReactiveWriteObserver<T>): MutableEntry<ReactiveWriteObserver<T>> {
        return writeObservers.computeIfAbsent(observer) { MutableEntryImpl(it) }
    }
}