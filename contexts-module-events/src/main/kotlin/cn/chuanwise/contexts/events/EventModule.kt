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
import cn.chuanwise.contexts.ContextPreEnterEvent
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.events.annotations.ListenerManager
import cn.chuanwise.contexts.filters.FilterContext
import cn.chuanwise.contexts.filters.FilterModule
import cn.chuanwise.contexts.filters.filterManager
import cn.chuanwise.contexts.module.ModulePreEnableEvent
import cn.chuanwise.contexts.module.addDependencyModuleClass
import cn.chuanwise.contexts.util.Beans
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeans
import cn.chuanwise.contexts.util.MutableBeans
import cn.chuanwise.contexts.util.MutableEntries
import cn.chuanwise.contexts.util.MutableEntry

/**
 * 事件模块。
 *
 * 事件通过 [Beans.eventPublisher] 的 [EventPublisher.publish] 发布。
 * 模块首先检查事件的类型，找到传播器 [EventProcessor]。若为特殊类型事件，例如 Bukkit
 * 的事件有不同优先级，则应当按照从高到低的顺序激活监听器。
 *
 * 事件传播器通过多次调用 [ListenerManager.publishToContext] 将事件发给监听器。
 *
 * @author Chuanwise
 * @see createEventModule
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
     * @param processor 事件传播器
     * @return 事件发布器注册项
     */
    fun <T : Any> registerEventProcessor(eventClass: Class<T>, processor: EventProcessor<T>) : MutableEntry<EventProcessor<T>>
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class EventModuleImpl @JvmOverloads constructor(
    private val defaultEventSpreader: EventSpreader<Any>? = ChildrenEventSpreader
) : EventModule {
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

    private class EventProcessorImpl<T : Any>(
        val eventClass: Class<T>,
        val eventProcessor: EventProcessor<T>
    ) : EventProcessor<T> by eventProcessor {
        fun safeProcess(eventContext: EventContext<T>) {
            try {
                process(eventContext)
            } catch (e: Throwable) {
                eventContext.context.contextManager.logger.error(e) {
                    "Failed to process event ${eventContext.event} with event process $eventProcessor. " +
                            "Details: " +
                            "event class: ${eventContext.event::class.qualifiedName}, " +
                            "event process: $eventProcessor. "
                }
            }
        }
    }

    private val eventProcessors = MutableEntries<EventProcessorImpl<Any>>()

    override fun <T : Any> registerEventProcessor(
        eventClass: Class<T>,
        processor: EventProcessor<T>
    ): MutableEntry<EventProcessor<T>> {
        val finalProcessors = EventProcessorImpl(eventClass, processor) as EventProcessorImpl<Any>
        return eventProcessors.add(finalProcessors) as MutableEntry<EventProcessor<T>>
    }

    private abstract inner class AbstractEventSpreader<T : Any>(
        val eventSpreader: EventSpreader<T>
    ) : EventSpreader<T> by eventSpreader {
        fun safeSpread(currentContext: Context, eventContext: EventContext<T>) {
            try {
                spread(currentContext, eventContext)
            } catch (e: Throwable) {
                onExceptionOccurred(e, currentContext, eventContext)
            }
        }

        protected abstract fun onExceptionOccurred(e: Throwable, currentContext: Context, eventContext: EventContext<T>)
    }

    private inner class EventSpreaderImpl<T : Any>(
        eventSpreader: EventSpreader<T>
    ) : AbstractEventSpreader<T>(eventSpreader) {
        override fun spread(currentContext: Context, eventContext: EventContext<T>) {
            eventSpreader.spread(currentContext, eventContext)
        }

        override fun onExceptionOccurred(e: Throwable, currentContext: Context, eventContext: EventContext<T>) {
            currentContext.contextManager.logger.error(e) {
                "Failed to spread event $eventContext with event spreader $eventSpreader. " +
                        "Details: " +
                        "event: ${eventContext.event}, " +
                        "event spreader: $eventSpreader. "
            }
        }
    }

    private inner class ClassBasedEventSpreaderImpl<T : Any>(
        val eventClass: Class<T>,
        eventSpreader: EventSpreader<T>
    ) : AbstractEventSpreader<T>(eventSpreader) {
        override fun spread(currentContext: Context, eventContext: EventContext<T>) {
            eventSpreader.spread(currentContext, eventContext)
        }

        override fun onExceptionOccurred(e: Throwable, currentContext: Context, eventContext: EventContext<T>) {
            currentContext.contextManager.logger.error(e) {
                "Failed to spread event $eventContext with event spreader $eventSpreader. " +
                        "Details: " +
                        "event: ${eventContext.event}, " +
                        "event spreader: $eventSpreader, " +
                        "event spreader expected class: ${eventClass.name}. "
            }
        }
    }

    private inner class EventPublisherImpl(
        override val context: Context,
        override var defaultEventSpreader: EventSpreader<Any>?
    ) : EventPublisher<Any> {
        private var noDefaultEventSpreaderLog = true
        private val eventSpreaders = MutableEntries<AbstractEventSpreader<Any>>()

        private fun createEventContext(event: Any): EventContext<Any> {
            val filterContext = context.filterManager.filter(event)
            val beans = InheritedMutableBeans(context).apply {
                registerBean(filterContext, primary = true)
                registerBean(event, primary = true)
            }
            return EventContextImpl(event, context, beans, filterContext).apply {
                beans.registerBean(this, primary = true)
            }
        }

        private fun tryProcessEvent(eventContext: EventContext<Any>): Boolean {
            var eventProcessed = false
            for (entry in eventProcessors) {
                if (entry.value.eventClass.isInstance(eventContext.event)) {
                    entry.value.safeProcess(eventContext)
                    eventProcessed = true
                }
            }
            return eventProcessed
        }

        override fun publish(event: Any): EventContext<Any> {
            val eventContext = createEventContext(event)
            if (tryProcessEvent(eventContext)) {
                return eventContext
            }

            return publish(eventContext)
        }

        private fun doPublishToContext(eventContext: EventContext<Any>){
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

        override fun publishToContext(event: Any): EventContext<Any> {
            val eventContext = createEventContext(event)
            if (tryProcessEvent(eventContext)) {
                return eventContext
            }

            doPublishToContext(eventContext)
            return eventContext
        }

        override fun publish(eventContext: EventContext<Any>): EventContext<Any> {
            if (!eventContext.isIntercepted) {
                doPublishToChildren(eventContext)
            }
            if (!eventContext.isIntercepted) {
                doPublishToContext(eventContext)
            }
            return eventContext
        }

        private fun doPublishToChildren(eventContext: EventContext<Any>): EventContext<Any> {
            var eventSpread = false
            for (entry in eventSpreaders) {
                when (val eventSpreader = entry.value) {
                    is ClassBasedEventSpreaderImpl<Any> -> {
                        if (eventSpreader.eventClass.isInstance(eventContext.event)) {
                            eventSpreader.safeSpread(context, eventContext)
                            eventSpread = true
                        }
                    }
                    is EventSpreaderImpl<Any> -> {
                        eventSpreader.safeSpread(context, eventContext)
                        eventSpread = true
                    }
                    else -> error("Unknown event spreader type: $eventSpreader")
                }
            }
            if (!eventSpread) {
                val defaultEventSpreaderLocal = defaultEventSpreader
                if (defaultEventSpreaderLocal != null) {
                    defaultEventSpreaderLocal.spread(context, eventContext)
                } else if (noDefaultEventSpreaderLog) {
                    context.contextManager.logger.warn {
                        "No event spreader found for event $eventContext in context $context. " +
                                "Which may cause the event to be ignored. " +
                                "If it's not expected, please set a default event spreader. " +
                                "Details: " +
                                "event: ${eventContext.event}. "
                    }
                    noDefaultEventSpreaderLog = false
                }
            }
            return eventContext
        }

        override fun registerEventSpreader(spreader: EventSpreader<Any>): MutableEntry<EventSpreader<Any>> {
            val finalSpreader = EventSpreaderImpl(spreader) as AbstractEventSpreader<Any>
            return eventSpreaders.add(finalSpreader)
        }

        override fun <U : Any> registerEventSpreader(
            eventClass: Class<U>,
            spreader: EventSpreader<U>
        ): MutableEntry<EventSpreader<U>> {
            val finalSpreader = ClassBasedEventSpreaderImpl(eventClass, spreader) as AbstractEventSpreader<Any>
            return eventSpreaders.add(finalSpreader) as MutableEntry<EventSpreader<U>>
        }
    }

    override fun onModulePreEnable(event: ModulePreEnableEvent) {
        event.addDependencyModuleClass<FilterModule>()
    }

    override fun onContextPreEnter(event: ContextPreEnterEvent) {
        val eventPublisher = EventPublisherImpl(event.context, defaultEventSpreader)
        event.context.registerBean(eventPublisher)
    }

    override fun registerEventHandler(eventHandler: EventHandler): MutableEntry<EventHandler> {
        return eventHandlers.add(EventHandlerImpl(eventHandler))
    }
}