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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class MutableEntrySet<T : Any>(
    private val onPreRemove: Consumer<MutableEntry<T>>? = null,
    private val onPostRemove: Consumer<MutableEntry<T>>? = null,
) : Iterable<MutableEntry<T>> {
    private inner class MutableEntryImpl(override val value: T) : MutableEntry<T> {
        private val mutableIsRemoved = AtomicBoolean(false)
        override val isRemoved: Boolean get() = mutableIsRemoved.get()

        override fun tryRemove(): Boolean {
            val result = mutableIsRemoved.compareAndSet(false, true)
            if (result) {
                try {
                    onPreRemove?.accept(this)
                } finally {
                    entries.remove(value)
                    onPostRemove?.accept(this)
                }
            }
            return result
        }
    }

    private val entries = ConcurrentHashMap<T, MutableEntry<T>>()

    val size: Int get() = entries.size
    val isEmpty: Boolean get() = entries.isEmpty()

    fun add(value: T) : MutableEntry<T> {
        return MutableEntryImpl(value).also {
            entries.put(value, it)?.tryRemove()
        }
    }

    override fun iterator(): Iterator<MutableEntry<T>> {
        return entries.values.iterator()
    }
}