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

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

@ContextsInternalApi
class MutableEntries<T> : Iterable<MutableEntry<T>> {
    private inner class MutableEntryImpl<T>(override val value: T) : MutableEntry<T> {
        private val mutableIsRemoved = AtomicBoolean(false)
        override val isRemoved: Boolean get() = mutableIsRemoved.get()

        override fun remove() {
            if (mutableIsRemoved.compareAndSet(false, true)) {
                entries.remove(this)
            }
        }
    }

    private val entries = ConcurrentLinkedDeque<MutableEntry<*>>()

    fun add(value: T) : MutableEntry<T> {
        return MutableEntryImpl(value).also {
            entries.add(it)
        }
    }

    fun clear() {
        entries.clear()
    }

    @Suppress("UNCHECKED_CAST")
    override fun iterator(): Iterator<MutableEntry<T>> {
        return entries.iterator() as Iterator<MutableEntry<T>>
    }
}