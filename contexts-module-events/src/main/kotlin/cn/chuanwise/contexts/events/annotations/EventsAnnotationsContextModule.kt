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

package cn.chuanwise.contexts.events.annotations

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextModule
import cn.chuanwise.contexts.events.ContextPreEnterEvent
import cn.chuanwise.contexts.events.EventContext
import cn.chuanwise.contexts.events.EventHandler
import cn.chuanwise.contexts.events.EventsContextModule
import cn.chuanwise.contexts.filters.filterManagerOrNull
import cn.chuanwise.contexts.util.Beans
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.Entry
import cn.chuanwise.contexts.util.InheritedMutableBeans
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.coroutineScope
import cn.chuanwise.contexts.util.getBeanValueOrFail
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

@Suppress("UNCHECKED_CAST")
class EventsAnnotationsContextModule : ContextModule {
    private class FunctionalListenerImpl<T : Any>(
        private val eventClass: Class<T>,
        private val listener: cn.chuanwise.contexts.events.Listener<T>,
        private val filter: Boolean,
        private val intercept: Boolean,
        private val context: Context
    ) : cn.chuanwise.contexts.events.Listener<T> {
        override fun onEvent(eventContext: EventContext<T>) {
            if (!eventClass.isAssignableFrom(eventContext.event.javaClass)) {
                return
            }
            if (filter) {
                val filterManager = context.filterManagerOrNull
                if (filterManager != null && filterManager.filterInAllParentsAndContext(eventContext.event) == false) {
                    return
                }
            }
            listener.onEvent(eventContext)

            if (intercept) {
                eventContext.isIntercepted = true
            }
        }
    }

    private class ReflectListenerImpl(
        private val context: Context,
        private val function: KFunction<*>,
        private val instance: Any,
        private val filter: Boolean,
        private val intercept: Boolean,
        eventClass: Class<*>?,
    ) : cn.chuanwise.contexts.events.Listener<Any> {
        interface ArgumentFactory {
            fun create(eventContext: EventContext<Any>, context: Beans): Any
        }
        private val argumentFactories: List<ArgumentFactory>
        class FromBeansArgumentFactory(private val parameter: KParameter) : ArgumentFactory {
            override fun create(eventContext: EventContext<Any>, context: Beans): Any {
                return context.getBeanValueOrFail(parameter.type.javaType, key = parameter.name)
            }
        }

        private val eventClass: Class<*>

        init {
            var eventClassLocal = eventClass.takeIf { it != Nothing::class.java }
            val parameters = function.valueParameters

            argumentFactories = mutableListOf()
            for (parameter in parameters) {
                val eventAnnotation = parameter.findAnnotation<Event>()
                if (eventAnnotation != null) {
                    require(eventClassLocal == null || eventClassLocal == parameter.type.javaType) {
                        "Cannot set multiple different event classes (specified $eventClassLocal and ${parameter.type}) for a listener."
                    }
                    eventClassLocal = parameter.type.javaType as Class<*>
//                    argumentFactories.add(ThatEventArgumentFactory)
//                    continue
                }

                argumentFactories.add(FromBeansArgumentFactory(parameter))
            }

            if (eventClassLocal == null) {
                eventClassLocal = parameters.singleOrNull()?.type?.javaType as? Class<*>
            }
            require(eventClassLocal != null) {
                "Event class must be specified."
            }
            this.eventClass = eventClassLocal
        }

        @OptIn(ContextsInternalApi::class)
        override fun onEvent(eventContext: EventContext<Any>) {
            if (!eventClass.isInstance(eventContext.event)) {
                return
            }
            if (filter) {
                val filterManager = context.filterManagerOrNull
                if (filterManager != null && filterManager.filterInAllParentsAndContext(eventContext.event) == false) {
                    return
                }
            }

            function.isAccessible = true

            val beans = InheritedMutableBeans(context).apply {
                registerBean(eventContext, primary = true)
                registerBean(eventContext.event, primary = true)
            }
            val arguments = argumentFactories.map { it.create(eventContext, beans) }.toTypedArray()

            if (function.isSuspend) {
                beans.coroutineScope.launch {
                    function.callSuspend(instance, *arguments)

                    if (intercept) {
                        eventContext.isIntercepted = true
                    }
                }
            } else {
                function.call(instance, *arguments)

                if (intercept) {
                    eventContext.isIntercepted = true
                }
            }
        }
    }

    private inner class ListenerManagerImpl(
        override val context: Context
    ) : ListenerManager, cn.chuanwise.contexts.events.Listener<Any> {
        private inner class EntryImpl<T>(
            override val value: T,
        ) : MutableEntry<T> {
            private var mutableIsRemoved = AtomicBoolean(false)
            override val isRemoved: Boolean get() = mutableIsRemoved.get()

            override fun remove() {
                if (mutableIsRemoved.compareAndSet(false, true)) {
                    (eventHandlerEntries as MutableCollection<MutableEntry<T>>).remove(this)
                }
            }
        }
        private val eventHandlerEntries = ConcurrentLinkedDeque<Entry<cn.chuanwise.contexts.events.Listener<*>>>()

        override fun <T : Any> registerListener(
            eventClass: Class<T>,
            filter: Boolean,
            intercept: Boolean, listener: cn.chuanwise.contexts.events.Listener<T>
        ): MutableEntry<cn.chuanwise.contexts.events.Listener<T>> {
            val entry = EntryImpl(FunctionalListenerImpl(eventClass, listener, filter, intercept, context))
            eventHandlerEntries.add(entry)
            return entry
        }

        fun registerListener(listener: cn.chuanwise.contexts.events.Listener<Any>) {
            eventHandlerEntries.add(EntryImpl(listener))
        }

        override fun onEvent(eventContext: EventContext<Any>) {
            for (entry in eventHandlerEntries) {
                (entry.value as cn.chuanwise.contexts.events.Listener<Any>).onEvent(eventContext)
            }
        }
    }

    private object ListenerManagerEventHandlerImpl : EventHandler {
        override fun onEvent(currentContext: Context, eventContext: EventContext<*>) {
            val listenerManager = currentContext.listenerManagerOrNull as? ListenerManagerImpl ?: return
            listenerManager.onEvent(eventContext as EventContext<Any>)
        }
    }

    private val initialized = AtomicBoolean(false)

    override fun onContextPreEnter(event: ContextPreEnterEvent) {
        val listenerManager = ListenerManagerImpl(event.context)
        event.context.registerBean(listenerManager)

        for (bean in event.context.contextBeans) {
            val value = bean.value
            val valueClass = value::class

            for (function in valueClass.memberFunctions) {
                val listenerAnnotation = function.findAnnotation<Listener>() ?: continue
                val eventClass = listenerAnnotation.eventClass.takeIf { it != Nothing::class }?.java

                val eventHandler = ReflectListenerImpl(
                    event.context, function, value, listenerAnnotation.filter, listenerAnnotation.intercept, eventClass
                )
                listenerManager.registerListener(eventHandler)
            }
        }

        if (initialized.compareAndSet(false, true)) {
            val eventModule = event.contextManager.beans.getBeanValueOrFail<EventsContextModule>()
            eventModule.registerEventHandler(ListenerManagerEventHandlerImpl)
        }
    }
}