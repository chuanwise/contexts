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

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.context.ContextManager
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.context.ContextPreEnterEvent
import cn.chuanwise.contexts.events.EventContext
import cn.chuanwise.contexts.events.EventProcessor
import cn.chuanwise.contexts.events.Listener
import cn.chuanwise.contexts.events.annotations.EventAnnotationModule
import cn.chuanwise.contexts.events.annotations.listenerManager
import cn.chuanwise.contexts.events.eventModule
import cn.chuanwise.contexts.events.eventPublisher
import cn.chuanwise.contexts.filters.annotations.FilterAnnotationModule
import cn.chuanwise.contexts.module.ModulePostDisableEvent
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.module.ModulePreEnableEvent
import cn.chuanwise.contexts.module.addDependencyModuleClass
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.addBean
import cn.chuanwise.contexts.util.getBean
import cn.chuanwise.contexts.util.getBeanOrFail
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.reflect.KClass

/**
 * Bukkit 事件模块，用于处理 Bukkit 事件。
 *
 * @author Chuanwise
 */
interface BukkitEventModule : Module

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class BukkitEventModuleImpl @JvmOverloads constructor(
    private var plugin: Plugin? = null
) : BukkitEventModule {
    private data class EventHandlerPolicy(
        val eventClass: KClass<out Event>,
        val priority: EventPriority
    )

    private lateinit var bukkitEventHandlerInjector: BukkitEventHandlerInjector

    // 插件对每个类型的事件，只往 BukkitEventHandlerInjector 里注册一个将事件发布给所有 root 的监听器。
    private val eventHandlers = ConcurrentHashMap<EventHandlerPolicy, MutableEntry<Consumer<Event>>>()

    private var contextManager: ContextManager? = null
    private fun getContextManager(): ContextManager = contextManager ?: error("BukkitEventModule is not enabled.")

    private fun ensureEventHandlerRegistered(eventClass: KClass<out Event>, priority: EventPriority) {
        val policy = EventHandlerPolicy(eventClass, priority)
        eventHandlers.computeIfAbsent(policy) {
            bukkitEventHandlerInjector.registerEventHandler(policy.eventClass, policy.priority) {
                for (root in getContextManager().rootContexts) {
                    root.eventPublisher.publish(it)
                }
            } as MutableEntry<Consumer<Event>>
        }
    }

    private inner class ListenerImpl<T : Event>(
        private val context: Context,
        private val listener: Listener<T>,
        val priority: EventPriority,
        val ignoreCancelled: Boolean,
        val listen: Boolean
    ) : Listener<T> {
        override fun listen(eventContext: EventContext<T>) {
            val priority = eventContext.beans.getBeanOrFail<EventPriority>()
            if (this.priority != priority) {
                return
            }

            val event = eventContext.event
            if (event is Cancellable) {
                if (event.isCancelled && !ignoreCancelled) {
                    return
                }
            }

            try {
                listener.listen(eventContext)
            } catch (e: Throwable) {
                onException(e, eventContext)
            } finally {
                if (listen) {
                    eventContext.listen()
                }
            }
        }

        private fun onException(e: Throwable, eventContext: EventContext<T>) {
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
            eventClass: KClass<T>,
            priority: EventPriority,
            ignoreCancelled: Boolean,
            filter: Boolean,
            intercept: Boolean,
            listen: Boolean,
            listener: Listener<T>
        ): MutableEntry<Listener<T>> {
            ensureEventHandlerRegistered(eventClass, priority)
            val finalListener = ListenerImpl(context, listener, priority, ignoreCancelled, listen)
            return context.listenerManager.registerListener(filter, intercept, listen = false, finalListener)
        }
    }

    // 处理不同优先级事件的传播。
    private lateinit var eventProcessor: MutableEntry<EventProcessor<Event>>

    private fun getPlugin() : Plugin {
        var pluginLocal = plugin
        if (pluginLocal == null) {
            pluginLocal = getContextManager().getBean<Plugin>() ?: error(
                "Plugin is not found. Set it in the related context or in BukkitEventModule, please."
            )
            plugin = pluginLocal
        }
        return pluginLocal
    }

    override fun onModulePostEnable(event: ModulePostEnableEvent) {
        val contextManager = event.contextManager
        check(this.contextManager == null) {
            "BukkitEventModule is already enabled. Notice that it can not be shared between different context manager. " +
                    "If you want to enable it in another context manager, please create a new instance by createBukkitEventModule()."
        }
        this.contextManager = contextManager

        val plugin = getPlugin()
        bukkitEventHandlerInjector = createBukkitEventHandlerInjector(plugin, contextManager.logger)

        val eventModule = contextManager.eventModule
        eventProcessor = eventModule.registerEventProcessor(Event::class.java, BukkitEventProcessor)
    }

    override fun onModulePreEnable(event: ModulePreEnableEvent) {
        event.addDependencyModuleClass<FilterAnnotationModule>()
        event.addDependencyModuleClass<EventAnnotationModule>()
    }

    override fun onModulePostDisable(event: ModulePostDisableEvent) {
        this.contextManager = null
        eventHandlers.values.forEach { it.remove() }
        eventHandlers.clear()
        eventProcessor.remove()
    }

    override fun onContextPreEnter(event: ContextPreEnterEvent) {
        val bukkitEventManager = BukkitEventManagerImpl(event.context)
        event.context.addBean(bukkitEventManager)
    }
}