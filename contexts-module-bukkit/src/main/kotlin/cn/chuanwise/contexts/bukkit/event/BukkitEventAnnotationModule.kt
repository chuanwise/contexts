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
import cn.chuanwise.contexts.context.CoroutineScopeConfiguration
import cn.chuanwise.contexts.annotation.ArgumentResolver
import cn.chuanwise.contexts.annotation.AnnotationFunctionProcessor
import cn.chuanwise.contexts.annotation.annotationModule
import cn.chuanwise.contexts.context.callFunctionAsync
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_LISTEN
import cn.chuanwise.contexts.events.EventContext
import cn.chuanwise.contexts.events.Listener
import cn.chuanwise.contexts.events.annotations.ListenerFunctionProcessor
import cn.chuanwise.contexts.events.annotations.eventAnnotationModule
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModulePostDisableEvent
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.context.toConfiguration
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeanManagerImpl
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.parseSubjectClassAndCollectArgumentResolvers
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

/**
 * 扫描 Bukkit 事件监听器的模块。
 *
 * 使用此模块后，在上下文对象内可以使用 [EventHandler] 或 [Listener] 注册
 * Bukkit 事件的监听器，其将自动通过 [bukkitEventManager] 注册监听器。
 *
 *
 */
interface BukkitEventAnnotationModule : Module

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class BukkitEventAnnotationModuleImpl : BukkitEventAnnotationModule {
    private inner class ReflectListenerImpl(
        private val function: KFunction<*>,
        private val functionClass: KClass<*>,
        private val context: Context,
        private val argumentResolvers: Map<KParameter, ArgumentResolver>,
        private val coroutineScopeConfiguration: CoroutineScopeConfiguration?,
        private val priority: EventPriority = DEFAULT_BUKKIT_EVENT_HANDLER_PRIORITY,
        private val ignoreCancelled: Boolean = DEFAULT_BUKKIT_EVENT_HANDLER_IGNORE_CANCELLED,
        private val listen: Boolean = DEFAULT_LISTENER_LISTEN
    ) : Listener<Event> {
        override fun listen(eventContext: EventContext<Event>) {
            val beans = InheritedMutableBeanManagerImpl(context, eventContext.beans)
            val arguments = argumentResolvers.mapValues { it.value.resolveArgument(beans) }

            callFunctionAsync(
                context, function, functionClass, arguments, coroutineScopeConfiguration,
                runBlocking = false,
                onException = {
                    onException(it, eventContext)
                },
                onFinally = {
                    if (listen) {
                        eventContext.listen()
                    }
                }
            )
        }

        private fun onException(e: Throwable, eventContext: EventContext<Event>) {
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

    // 忽略既有 @Listener 又有 @EventHandler 注解的函数的处理。
    private lateinit var ignoreListenerAnnotationClass: MutableEntry<Class<EventHandler>>

    // 处理 @EventHandler 函数，可能有 @Listener 注解。
    private lateinit var eventHandlerAnnotationClass: MutableEntry<AnnotationFunctionProcessor<EventHandler>>

    // 处理只有 @Listener 注解，没有 @EventHandler 注解的函数。
    private lateinit var listenerFunctionProcessor: MutableEntry<ListenerFunctionProcessor<Event>>

    override fun onModulePostEnable(event: ModulePostEnableEvent) {
        val contextManager = event.contextManager

        val annotationModule = contextManager.annotationModule
        val eventAnnotationsModule = contextManager.eventAnnotationModule

        // 让事件注解模块忽略带 EventHandler 注解的方法。
        ignoreListenerAnnotationClass = eventAnnotationsModule.registerIgnoreListenerAnnotationClass(EventHandler::class)
        listenerFunctionProcessor = eventAnnotationsModule.registerListenerFunctionProcessor(Event::class) {
            val coroutineScopeConfiguration = it.function.findAnnotation<cn.chuanwise.contexts.util.CoroutineScope>()?.toConfiguration()

            // 处理那些只有 @Listener 注解，没有 @EventHandler 注解的函数注册。
            val listener = ReflectListenerImpl(
                it.function, it.value::class, it.context, it.argumentResolvers,
                coroutineScopeConfiguration, listen = it.annotation.listen
            )
            it.context.bukkitEventManager.registerListener(it.eventClass, listener = listener)
        }
        eventHandlerAnnotationClass = annotationModule.registerAnnotationFunctionProcessor(EventHandler::class) {
            val coroutineScopeConfiguration = it.function.findAnnotation<cn.chuanwise.contexts.util.CoroutineScope>()?.toConfiguration()
            val listenerAnn = it.function.findAnnotation<cn.chuanwise.contexts.events.annotations.Listener>()

            val priority = it.annotation.priority
            val ignoreCancelled = it.annotation.ignoreCancelled
            val filter = listenerAnn?.filter ?: true
            val intercept = listenerAnn?.intercept ?: false
            val listen = listenerAnn?.listen ?: true

            val function = it.function
            val value = it.value
            val context = it.context

            val subjectClassFromListenerAnn = listenerAnn?.eventClass
                ?.takeIf { cls -> Event::class.java.isAssignableFrom(cls.java) }

            val functionClass = value::class
            val (argumentResolvers, eventClass) = context.parseSubjectClassAndCollectArgumentResolvers(
                functionClass = functionClass,
                function = function,
                defaultSubjectClass = subjectClassFromListenerAnn,
                subjectAnnotationClass = cn.chuanwise.contexts.events.annotations.Event::class,
                subjectSuperClass = Event::class
            )

            val listener = ReflectListenerImpl(
                function, functionClass, context, argumentResolvers,
                coroutineScopeConfiguration, priority, ignoreCancelled, listen
            )

            context.bukkitEventManager.registerListener(eventClass as KClass<Event>, priority, ignoreCancelled, filter, intercept, listen, listener)
        }
    }

    override fun onModulePostDisable(event: ModulePostDisableEvent) {
        ignoreListenerAnnotationClass.remove()
        listenerFunctionProcessor.remove()
        eventHandlerAnnotationClass.remove()
    }
}