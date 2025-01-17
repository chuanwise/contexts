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

import cn.chuanwise.contexts.Context

abstract class AbstractTopologicalSortingIterator(
    private val counts: MutableMap<Context, Int>
) : Iterator<Context> {
    private var context: Context? = null

    override fun next(): Context {
        var context = context
        if (context != null) {
            for (parent in context.parents) {
                counts.computeIfPresent(parent) { _, value -> value - 1 }
            }
        }

        val entries = counts.entries
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()

            if (entry.value == 0) {
                iterator.remove()

                context = entry.key
                this.context = entry.key

                return context
            }
        }

        error("Ring detected in event publishing!")
    }

    override fun hasNext(): Boolean {
        return counts.isNotEmpty()
    }
}

@ContextsInternalApi
class ParentToChildTopologicalSortingImpl(
    contexts: Iterable<Context>
) : AbstractTopologicalSortingIterator(
    contexts.asSequence()
        .associateWith { it.parentCount }
        .toMutableMap()
)

@ContextsInternalApi
class ChildToParentTopologicalSortingImpl(
    contexts: Iterable<Context>
) : AbstractTopologicalSortingIterator(
    contexts.asSequence()
        .associateWith { it.parentCount }
        .reverseCount()
        .toMutableMap()
)

private fun <T> Map<T, Int>.reverseCount(): Map<T, Int> {
    val maxValue = values.maxOrNull() ?: return this
    return mapValues { maxValue - it.value }
}