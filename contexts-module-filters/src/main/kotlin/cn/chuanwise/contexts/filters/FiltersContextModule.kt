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

package cn.chuanwise.contexts.filters

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextModule
import cn.chuanwise.contexts.events.ContextPostEnterEvent
import cn.chuanwise.contexts.util.createChildToParentTopologicalSortingIterator
import java.util.concurrent.ConcurrentLinkedDeque

class FiltersContextModule : ContextModule {
    private class FilterManagerImpl(
        override val context: Context
    ) : FilterManager {
        private inner class FilterEntryImpl(
            override val filter: Filter<Any>
        ) : FilterEntry {
            private var mutableIsRemoved = false
            override val isRemoved: Boolean get() = mutableIsRemoved

            override fun remove() {
                if (mutableIsRemoved) {
                    return
                }

                mutableIsRemoved = true
                filterEntries.remove(this)
            }
        }

        private val filterEntries = ConcurrentLinkedDeque<FilterEntry>()

        private fun filterInContextAndAppendExceptions(exceptions: MutableList<Throwable>, value: Any): Boolean? {
            for (entry in filterEntries) {
                val result = try {
                    entry.filter.onFilter(value)
                } catch (e: Throwable) {
                    exceptions.add(e)
                    continue
                } ?: continue

                return result
            }
            return null
        }

        override fun filterInContext(value: Any): Boolean? {
            val exceptions = mutableListOf<Throwable>()

            filterInContextAndAppendExceptions(exceptions, value)?.let { return it }

            if (exceptions.isNotEmpty()) {
                throw ValueFilterException(value, exceptions)
            }
            return null
        }

        private fun filterInAllParentsFromChildToParentAndAppendExceptions(
            exceptions: MutableList<Throwable>, value: Any
        ): Boolean? {
            context.allParents.createChildToParentTopologicalSortingIterator().forEach { parent ->
                val filterManager = parent.filterManagerOrNull as? FilterManagerImpl ?: return@forEach
                filterManager.filterInContextAndAppendExceptions(exceptions, value)?.let { return it }
            }
            return null
        }

        override fun filterInAllParentsAndContext(value: Any): Boolean? {
            val exceptions = mutableListOf<Throwable>()

            filterInContextAndAppendExceptions(exceptions, value)?.let { return it }
            filterInAllParentsFromChildToParentAndAppendExceptions(exceptions, value)?.let { return it }

            if (exceptions.isNotEmpty()) {
                throw ValueFilterException(value, exceptions)
            }
            return null
        }

        override fun registerFilter(filter: Filter<Any>): FilterEntry {
            val entry = FilterEntryImpl(filter)
            filterEntries.add(entry)
            return entry
        }
    }

    override fun onContextPostEnter(event: ContextPostEnterEvent) {
        val filterManager = FilterManagerImpl(event.context)
        event.context.registerBean(filterManager)
    }
}