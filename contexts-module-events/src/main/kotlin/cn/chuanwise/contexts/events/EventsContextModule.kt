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

package cn.chuanwise.contexts.events

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextModule
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.createChildToParentTopologicalSortingIterator
import java.util.concurrent.ConcurrentLinkedDeque

class EventsContextModule : ContextModule {
    private inner class EventHandlerEntryImpl(
        override val value: EventHandler
    ) : MutableEntry<EventHandler> {
        private var mutableIsRemoved = false
        override val isRemoved: Boolean get() = mutableIsRemoved

        override fun remove() {
            if (mutableIsRemoved) {
                return
            }
            mutableIsRemoved = true
            eventHandlerEntries.remove(this)
        }
    }

    private val eventHandlerEntries = ConcurrentLinkedDeque<MutableEntry<EventHandler>>()

    private class EventContextImpl<T : Any>(
        override val context: Context,
        override val event: T
    ) : EventContext<T> {
        override var isIntercepted: Boolean = false
    }

    private inner class EventPublisherImpl(
        override val context: Context
    ) : EventPublisher {
        @Suppress("UNCHECKED_CAST")
        private fun publishToContextAndAppendExceptions(exceptions: MutableList<Throwable>, eventContext: EventContext<Any>) {
            if (eventContext.isIntercepted) {
                return
            }
            for (entry in eventHandlerEntries) {
                if (eventContext.isIntercepted) {
                    return
                }
                try {
                    entry.value.onEvent(context, eventContext)
                } catch (e: Throwable) {
                    exceptions.add(e)
                }
            }
        }

        override fun publishToContext(event: Any) {
            val eventContext = EventContextImpl(context, event)
            val exceptions = mutableListOf<Throwable>()

            publishToContextAndAppendExceptions(exceptions, eventContext)

            if (exceptions.isNotEmpty()) {
                throw EventPublishException(event, exceptions)
            }
        }

        private fun publishToAllChildrenFromChildToParentAndAppendExceptions(
            exceptions: MutableList<Throwable>, eventContext: EventContext<Any>
        ) = context.allChildren.createChildToParentTopologicalSortingIterator().forEach {
            val eventPublisher = it.eventPublisherOrNull as? EventPublisherImpl ?: return@forEach
            eventPublisher.publishToContextAndAppendExceptions(exceptions, eventContext)
        }

        override fun publishToAllChildrenAndContext(event: Any) {
            val context = EventContextImpl(context, event)
            val exceptions = mutableListOf<Throwable>()

            publishToAllChildrenFromChildToParentAndAppendExceptions(exceptions, context)
            if (!context.isIntercepted) {
                publishToContextAndAppendExceptions(exceptions, context)
            }

            if (exceptions.isNotEmpty()) {
                throw EventPublishException(event, exceptions)
            }
        }
    }

    override fun onContextPostEnter(event: ContextPostEnterEvent) {
        val eventPublisher = EventPublisherImpl(event.context)
        event.context.registerBean(eventPublisher)
    }

    fun registerEventHandler(eventHandler: EventHandler): MutableEntry<EventHandler> {
        val entry = EventHandlerEntryImpl(eventHandler)
        eventHandlerEntries.add(entry)
        return entry
    }
}