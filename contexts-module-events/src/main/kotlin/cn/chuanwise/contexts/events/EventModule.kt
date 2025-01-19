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
import cn.chuanwise.contexts.Module
import cn.chuanwise.contexts.events.annotations.ListenerManager
import cn.chuanwise.contexts.filters.FilterContext
import cn.chuanwise.contexts.filters.filterManager
import cn.chuanwise.contexts.util.AllChildrenAndContextScope
import cn.chuanwise.contexts.util.Beans
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeans
import cn.chuanwise.contexts.util.MutableBeans
import cn.chuanwise.contexts.util.MutableEntries
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.Scope
import cn.chuanwise.contexts.util.getBeanValue
import cn.chuanwise.contexts.util.getBeanValueOrFail
import cn.chuanwise.contexts.util.getInstanceOrFail

/**
 * 事件模块。
 *
 * 事件通过 [Beans.eventPublisher] 的 [EventPublisher.publish] 发布。
 * 模块首先检查事件的类型，找到传播器 [EventSpreader]。若为特殊类型事件，例如 Bukkit
 * 的事件有不同优先级，则应当按照从高到低的顺序激活监听器。
 *
 * 事件传播器通过多次调用 [ListenerManager.publishToContext] 将事件发给监听器。
 *
 * @author Chuanwise
 * @see createEventsModule
 */
interface EventModule : Module {
    /**
     * 注册事件处理器。
     *
     * @param eventHandler 事件处理器
     * @return 事件处理器注册信息
     */
    fun registerEventHandler(eventHandler: EventHandler): MutableEntry<EventHandler>

    /**
     * 注册一个事件传播器。
     *
     * @param T 事件类型
     * @param eventClass 事件类
     * @param spreader 事件传播器
     * @return 事件发布器注册项
     */
    fun <T : Any> registerEventSpreader(eventClass: Class<T>, spreader: EventSpreader<T>) : MutableEntry<EventSpreader<T>>
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class EventModuleImpl : EventModule {
    private class EventHandlerImpl(
        private val eventHandler: EventHandler
    ) : EventHandler by eventHandler {
        fun safeHandle(currentContext: Context, eventContext: EventContext<Any>) {
            try {
                handle(currentContext, eventContext)
            } catch (e: Throwable) {
                currentContext.contextManager.logger.error(e) {
                    "Failed to handle event $eventContext with event handler $eventHandler. " +
                            "Details: " +
                            "event: ${eventContext.event}, " +
                            "event handler: $eventHandler. "
                }
            }
        }
    }

    private val eventHandlers = MutableEntries<EventHandlerImpl>()

    private class EventContextImpl<T : Any>(
        override val event: T,
        override val scope: Scope,
        override val context: Context,
        override val beans: MutableBeans,
        override val filterContext: FilterContext<T>
    ) : EventContext<T> {
        private var mutableIsIntercepted: Boolean = false
        override val isIntercepted: Boolean get() = mutableIsIntercepted

        override fun intercept() {
            mutableIsIntercepted = true
        }
    }

    private class EventSpreaderImpl<T : Any>(
        val eventClass: Class<T>,
        val eventSpreader: EventSpreader<T>
    ) : EventSpreader<T> by eventSpreader {
        fun safeSpread(eventContext: EventContext<T>) {
            try {
                spread(eventContext)
            } catch (e: Throwable) {
                eventContext.context.contextManager.logger.error(e) {
                    "Failed to spread event ${eventContext.event} with event spreader $eventSpreader. " +
                            "Details: " +
                            "event class: ${eventContext.event::class.qualifiedName}, " +
                            "event spreader: $eventSpreader. "
                }
            }
        }
    }

    private val eventSpreaders = MutableEntries<EventSpreaderImpl<Any>>()

    override fun <T : Any> registerEventSpreader(
        eventClass: Class<T>,
        spreader: EventSpreader<T>
    ): MutableEntry<EventSpreader<T>> {
        val finalPublisher = EventSpreaderImpl(eventClass, spreader) as EventSpreaderImpl<Any>
        return eventSpreaders.add(finalPublisher) as MutableEntry<EventSpreader<T>>
    }

    private inner class EventPublisherImpl(
        override val context: Context
    ) : EventPublisher<Any> {
        override fun publish(event: Any, scope: Scope?) {
            val scopeAnnotation = event.javaClass.getAnnotation(cn.chuanwise.contexts.events.annotations.Scope::class.java)
            val finalScope = scope ?: scopeAnnotation?.scopeClass?.java?.getInstanceOrFail() ?: AllChildrenAndContextScope

            val filterContext = context.filterManager.filter(event)
            val beans = InheritedMutableBeans(context).apply {
                registerBean(filterContext, primary = true)
                registerBean(event, primary = true)
            }
            val eventContext = EventContextImpl(event, finalScope, context, beans, filterContext).apply {
                beans.registerBean(this, primary = true)
            }

            var eventPublished = false
            for (entry in eventSpreaders) {
                if (entry.value.eventClass.isInstance(event)) {
                    entry.value.safeSpread(eventContext)
                    eventPublished = true
                }
            }
            if (eventPublished) {
                return
            }

            // 默认事件传播方法：只传播一次。
            for (context in finalScope.createIterator(context)) {
                if (eventContext.isIntercepted) {
                    return
                }

                for (entry in eventHandlers) {
                    if (eventContext.isIntercepted) {
                        return
                    }
                    entry.value.safeHandle(context, eventContext)
                }
            }
        }
    }

    override fun onContextPreEnter(event: ContextPreEnterEvent) {
        val eventPublisher = EventPublisherImpl(event.context)
        event.context.registerBean(eventPublisher)
    }

    override fun registerEventHandler(eventHandler: EventHandler): MutableEntry<EventHandler> {
        return eventHandlers.add(EventHandlerImpl(eventHandler))
    }
}