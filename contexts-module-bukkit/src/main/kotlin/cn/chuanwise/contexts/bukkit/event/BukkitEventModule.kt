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

package cn.chuanwise.contexts.bukkit.event

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextManager
import cn.chuanwise.contexts.Module
import cn.chuanwise.contexts.annotations.ArgumentResolver
import cn.chuanwise.contexts.annotations.FunctionProcessor
import cn.chuanwise.contexts.annotations.annotationModule
import cn.chuanwise.contexts.events.ContextPreEnterEvent
import cn.chuanwise.contexts.events.EventContext
import cn.chuanwise.contexts.events.EventSpreader
import cn.chuanwise.contexts.events.Listener
import cn.chuanwise.contexts.events.annotations.ListenerFunctionProcessor
import cn.chuanwise.contexts.events.annotations.eventAnnotationsModule
import cn.chuanwise.contexts.events.annotations.listenerManager
import cn.chuanwise.contexts.events.eventModule
import cn.chuanwise.contexts.events.eventPublisher
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeans
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.callByAndRethrowException
import cn.chuanwise.contexts.util.callSuspendByAndRethrowException
import cn.chuanwise.contexts.util.coroutineScope
import cn.chuanwise.contexts.util.coroutineScopeOrNull
import cn.chuanwise.contexts.util.getBeanValueOrFail
import cn.chuanwise.contexts.util.parseSubjectClassAndCollectArgumentResolvers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

interface BukkitEventModule : Module

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class BukkitEventModuleImpl : BukkitEventModule {
    private data class EventHandlerPolicy(
        val eventClass: Class<out Event>,
        val priority: EventPriority
    )

    private lateinit var bukkitEventHandlerInjector: BukkitEventHandlerInjector

    // 插件对每个类型的事件，只往 BukkitEventHandlerInjector 里注册一个将事件发布给所有 root 的监听器。
    private val eventHandlers = ConcurrentHashMap<EventHandlerPolicy, MutableEntry<Consumer<Event>>>()

    private var contextManager: ContextManager? = null
    private fun getContextManager(): ContextManager = contextManager ?: error("BukkitEventModule is not enabled.")

    private fun ensureEventHandlerRegistered(eventClass: Class<out Event>, priority: EventPriority) {
        val policy = EventHandlerPolicy(eventClass, priority)
        eventHandlers.computeIfAbsent(policy) {
            bukkitEventHandlerInjector.registerEventHandler(policy.eventClass, policy.priority) {
                for (root in getContextManager().roots) {
                    root.eventPublisher.publish(it)
                }
            } as MutableEntry<Consumer<Event>>
        }
    }

    private abstract inner class AbstractListener<T : Event>(
        val priority: EventPriority,
        val ignoreCancelled: Boolean
    ) : Listener<T> {
        override fun listen(eventContext: EventContext<T>) {
            val priority = eventContext.beans.getBeanValueOrFail<EventPriority>()
            if (this.priority != priority) {
                return
            }

            val event = eventContext.event
            if (event is Cancellable) {
                if (event.isCancelled && !ignoreCancelled) {
                    return
                }
            }

            listen0(eventContext)
        }

        protected abstract fun listen0(eventContext: EventContext<T>)

        protected abstract fun onExceptionOccurred(e: Throwable, eventContext: EventContext<T>)
    }

    private inner class ReflectListenerImpl(
        private val function: KFunction<*>,
        private val functionClass: Class<*>,
        private val context: Context,
        private val argumentResolvers: Map<KParameter, ArgumentResolver>,
        priority: EventPriority,
        ignoreCancelled: Boolean
    ) : AbstractListener<Event>(priority, ignoreCancelled) {
        override fun listen0(eventContext: EventContext<Event>) {
            val beans = InheritedMutableBeans(context, eventContext.beans)
            val arguments = argumentResolvers.mapValues { it.value.resolveArgument(beans) }

            if (function.isSuspend) {
                val block: suspend CoroutineScope.() -> Unit = {
                    try {
                        function.callSuspendByAndRethrowException(arguments)
                    } catch (e: Throwable) {
                        onExceptionOccurred(e, eventContext)
                    }
                }
                val coroutineScope = context.coroutineScopeOrNull
                if (coroutineScope == null) {
                    context.contextManager.logger.warn {
                        "Function ${function.name} in class ${functionClass.simpleName} is suspend, " +
                                "but no coroutine scope found. It will blocking caller thread. " +
                                "Details: " +
                                "function class: ${functionClass.name}, " +
                                "function: $function. "
                    }
                    runBlocking(block = block)
                } else {
                    context.coroutineScope.launch(block = block)
                }
            } else {
                try {
                    function.callByAndRethrowException(arguments)
                } catch (e: Throwable) {
                    onExceptionOccurred(e, eventContext)
                }
            }
        }

        override fun onExceptionOccurred(e: Throwable, eventContext: EventContext<Event>) {
            context.contextManager.logger.error(e) {
                "Exception occurred while listening event ${eventContext.event::class.simpleName} " +
                        "by method ${function.name} declared in ${function::class.simpleName} for context $context. " +
                        "Details: " +
                        "method class: ${function::class.qualifiedName}, " +
                        "method: $function, " +
                        "event class: ${eventContext.event::class.qualifiedName}, " +
                        "priority: $priority, " +
                        "ignoreCancelled: $ignoreCancelled."
            }
        }
    }

    private inner class ListenerImpl(
        private val context: Context,
        private val listener: Listener<Event>,
        priority: EventPriority,
        ignoreCancelled: Boolean
    ) : AbstractListener<Event>(priority, ignoreCancelled) {
        override fun listen0(eventContext: EventContext<Event>) {
            listener.listen(eventContext)
        }

        override fun onExceptionOccurred(e: Throwable, eventContext: EventContext<Event>) {
            context.contextManager.logger.error(e) {
                "Exception occurred while listening event ${eventContext.event::class.simpleName} " +
                        "by listener ${listener::class.simpleName} for context $context. " +
                        "Details: " +
                        "listener class: ${listener::class.qualifiedName}, " +
                        "listener: $listener, " +
                        "event class: ${eventContext.event::class.qualifiedName}, " +
                        "priority: $priority, " +
                        "ignoreCancelled: $ignoreCancelled."
            }
        }
    }

    private inner class BukkitEventManagerImpl(
        override val context: Context
    ) : BukkitEventManager {
        override fun <T : Event> registerListener(
            eventClass: Class<T>,
            priority: EventPriority,
            ignoreCancelled: Boolean,
            filter: Boolean,
            intercept: Boolean,
            listener: Listener<T>
        ): MutableEntry<Listener<T>> {
            ensureEventHandlerRegistered(eventClass, priority)
            val finalListener = ListenerImpl(context, listener as Listener<Event>, priority, ignoreCancelled)
            return context.listenerManager.registerListener(filter, intercept, finalListener) as MutableEntry<Listener<T>>
        }
    }

    // 忽略既有 @Listener 又有 @EventHandler 注解的函数的处理。
    private lateinit var ignoreListenerAnnotationClass: MutableEntry<Class<EventHandler>>

    // 处理 @EventHandler 函数，可能有 @Listener 注解。
    private lateinit var eventHandlerAnnotationClass: MutableEntry<FunctionProcessor<EventHandler>>

    // 处理只有 @Listener 注解，没有 @EventHandler 注解的函数。
    private lateinit var listenerFunctionProcessor: MutableEntry<ListenerFunctionProcessor<Event>>

    // 处理不同优先级事件的传播。
    private lateinit var eventSpreader: MutableEntry<EventSpreader<Event>>

    override fun onEnable(contextManager: ContextManager) {
        check(this.contextManager == null) {
            "BukkitEventModule is already enabled. Notice that it can not be shared between different context manager. " +
                    "If you want to enable it in another context manager, please create a new instance by createBukkitEventModule()."
        }
        this.contextManager = contextManager

        val plugin = contextManager.getBeanValueOrFail<Plugin>()
        bukkitEventHandlerInjector = createBukkitEventHandlerInjector(plugin, contextManager.logger)

        val annotationModule = contextManager.annotationModule
        val eventAnnotationsModule = contextManager.eventAnnotationsModule
        val eventModule = contextManager.eventModule

        // 让事件注解模块忽略带 EventHandler 注解的方法。
        ignoreListenerAnnotationClass = eventAnnotationsModule.registerIgnoreListenerAnnotationClass(EventHandler::class.java)
        listenerFunctionProcessor = eventAnnotationsModule.registerListenerFunctionProcessor(Event::class.java) {
            // 处理那些只有 @Listener 注解，没有 @EventHandler 注解的函数注册。
            val priority = EventPriority.NORMAL
            val ignoreCancelled = false

            val listener = ReflectListenerImpl(it.function, it.eventClass, it.context, it.argumentResolvers, priority, ignoreCancelled)
            it.context.listenerManager.registerListener(it.annotation.filter, it.annotation.intercept, listener)
        }
        eventHandlerAnnotationClass = annotationModule.registerFunctionProcessor(EventHandler::class.java) {
            val listenerAnn = it.function.annotations
                .singleOrNull { ann -> ann is cn.chuanwise.contexts.events.annotations.Listener }
                    as? cn.chuanwise.contexts.events.annotations.Listener

            val priority = it.annotation.priority
            val ignoreCancelled = it.annotation.ignoreCancelled
            val filter = listenerAnn?.filter ?: true
            val intercept = listenerAnn?.intercept ?: false

            val function = it.function
            val value = it.value
            val context = it.context

            val subjectClassFromListenerAnn = listenerAnn?.eventClass
                ?.takeIf { cls -> Event::class.java.isAssignableFrom(cls.java) }?.java

            val functionClass = value::class.java
            val (argumentResolvers, eventClass) = context.parseSubjectClassAndCollectArgumentResolvers(
                functionClass = functionClass,
                function = function,
                defaultSubjectClass = subjectClassFromListenerAnn,
                subjectAnnotationClass = cn.chuanwise.contexts.events.annotations.Event::class.java,
                subjectSuperClass = Event::class.java
            )

            val listener = ReflectListenerImpl(function, eventClass, context, argumentResolvers, priority, ignoreCancelled)
            context.listenerManager.registerListener(filter, intercept, listener)
        }



        eventSpreader = eventModule.registerEventSpreader(Event::class.java, BukkitEventSpreader)
    }

    override fun onDisable(contextManager: ContextManager) {
        this.contextManager = null
        eventHandlers.values.forEach { it.remove() }
        eventHandlers.clear()
        ignoreListenerAnnotationClass.remove()
        listenerFunctionProcessor.remove()
        eventHandlerAnnotationClass.remove()
        eventSpreader.remove()
    }

    override fun onContextPreEnter(event: ContextPreEnterEvent) {
        val bukkitEventManager = BukkitEventManagerImpl(event.context)
        event.context.registerBean(bukkitEventManager)
    }
}