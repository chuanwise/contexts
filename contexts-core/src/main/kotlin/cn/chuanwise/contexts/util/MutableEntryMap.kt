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
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

class MutableEntryMap<K : Any, V : Any>(
    private val onPreRemove: BiConsumer<K, MutableEntry<V>>? = null,
    private val onPostRemove: BiConsumer<K, MutableEntry<V>>? = null,
) : Iterable<MutableEntry<V>> {
    private inner class MutableEntryImpl(
        private val key: K,
        override val value: V
    ) : MutableEntry<V> {
        private val mutableIsRemoved = AtomicBoolean(false)
        override val isRemoved: Boolean get() = mutableIsRemoved.get()

        override fun tryRemove(): Boolean {
            val result = mutableIsRemoved.compareAndSet(false, true)
            if (result) {
                try {
                    onPreRemove?.accept(key, this)
                } finally {
                    entries.remove(key)
                    onPostRemove?.accept(key, this)
                }
            }
            return result
        }
    }

    private val entries = ConcurrentHashMap<K, MutableEntryImpl>()

    val size: Int get() = entries.size
    val isEmpty: Boolean get() = entries.isEmpty()

    fun put(key: K, value: V) : MutableEntry<V> {
        return MutableEntryImpl(key, value).also {
            entries.put(key, it)?.tryRemove()
        }
    }

    operator fun set(key: K, value: V) {
        put(key, value)
    }

    override fun iterator(): Iterator<MutableEntry<V>> {
        return entries.values.iterator()
    }

    operator fun get(key: K) : MutableEntry<V>? {
        return entries[key]
    }

    fun toMap() : Map<K, MutableEntry<V>> {
        return entries
    }

    fun computeIfAbsent(key: K, mappingFunction: Function<K, V>) : MutableEntry<V> {
        return entries.computeIfAbsent(key) { MutableEntryImpl(key, mappingFunction.apply(key)) }
    }
}