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

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.context.ContextPreEnterEvent
import cn.chuanwise.contexts.annotation.AnnotationModule
import cn.chuanwise.contexts.annotation.ArgumentResolver
import cn.chuanwise.contexts.annotation.AnnotationFunctionProcessor
import cn.chuanwise.contexts.annotation.annotationModule
import cn.chuanwise.contexts.events.EventContext
import cn.chuanwise.contexts.events.EventHandler
import cn.chuanwise.contexts.events.EventModule
import cn.chuanwise.contexts.events.EventSpreader
import cn.chuanwise.contexts.events.eventModule
import cn.chuanwise.contexts.events.eventPublisher
import cn.chuanwise.contexts.filters.filterManager
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModulePostDisableEvent
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.module.ModulePreEnableEvent
import cn.chuanwise.contexts.module.addDependencyModuleClass
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeanManagerImpl
import cn.chuanwise.contexts.util.MutableEntries
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.contexts.util.addBeanByCompilationType
import cn.chuanwise.contexts.util.callByAndRethrowException
import cn.chuanwise.contexts.util.callSuspendByAndRethrowException
import cn.chuanwise.contexts.util.coroutineScopeOrNull
import cn.chuanwise.contexts.util.parseSubjectClassAndCollectArgumentResolvers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSuperclassOf

/**
 * 事件注解模块。
 *
 * @author Chuanwise
 * @see createEventAnnotationModule
 */
@NotStableForInheritance
interface EventAnnotationModule : Module {
    /**
     * 注册一个监听器，让事件注解模块忽略既有 [Listener] 又有给定注解的函数。
     *
     * @param T 注解类型
     * @param annotationClass 注解类
     * @return 注解
     */
    fun <T : Annotation> registerIgnoreListenerAnnotationClass(annotationClass: KClass<T>): MutableEntry<Class<T>>

    /**
     * 注册一个监听器函数处理器。
     *
     * @param T 类型
     * @param eventClass 事件类
     * @param processor 处理器
     * @return 处理器
     */
    fun <T : Any> registerListenerFunctionProcessor(
        eventClass: KClass<T>, processor: ListenerFunctionProcessor<T>
    ): MutableEntry<ListenerFunctionProcessor<T>>
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class EventAnnotationModuleImpl : EventAnnotationModule {
    private abstract class AbstractListener<T : Any>(
        val context: Context,
        val eventClass: KClass<T>?,
        val filter: Boolean,
        val intercept: Boolean,
        val listen: Boolean
    ) : cn.chuanwise.contexts.events.Listener<T> {
        override fun listen(eventContext: EventContext<T>) {
            if (eventClass != null && !eventClass.isInstance(eventContext.event)) {
                return
            }
            if (filter) {
                val filterManager = context.filterManager
                if (filterManager.filter(eventContext.filterContext).isFalse) {
                    return
                }
            }

            onEvent0(eventContext)
        }

        protected abstract fun onEvent0(eventContext: EventContext<T>)

        protected fun onEventPosted(eventContext: EventContext<T>) {
            if (listen) {
                eventContext.listen()
            }
            if (intercept && eventContext.isListened) {
                eventContext.intercept()
            }
        }
    }

    private class ListenerImpl<T : Any>(
        private val listener: cn.chuanwise.contexts.events.Listener<T>,
        context: Context,
        eventClass: KClass<T>?,
        filter: Boolean,
        intercept: Boolean,
        listen: Boolean
    ) : AbstractListener<T>(context, eventClass, filter, intercept, listen) {
        override fun onEvent0(eventContext: EventContext<T>) {
            listener.listen(eventContext)
            onEventPosted(eventContext)
        }
    }

    private class ReflectListenerImpl(
        private val functionClass: KClass<*>,
        private val function: KFunction<*>,
        private val argumentResolvers: Map<KParameter, ArgumentResolver>,
        context: Context,
        eventClass: KClass<Any>,
        filter: Boolean,
        intercept: Boolean,
        listen: Boolean
    ) : AbstractListener<Any>(context, eventClass, filter, intercept, listen) {
        override fun onEvent0(eventContext: EventContext<Any>) {
            val beans = InheritedMutableBeanManagerImpl(context, eventContext.beans)
            val arguments = argumentResolvers.mapValues { it.value.resolveArgument(beans) }

            if (function.isSuspend) {
                val block: suspend CoroutineScope.() -> Unit = {
                    try {
                        function.callSuspendByAndRethrowException(arguments)
                        onEventPosted(eventContext)
                    } catch (e: Throwable) {
                        onException(e, eventContext)
                    }
                }

                val coroutineScope = context.coroutineScopeOrNull
                if (coroutineScope == null) {
                    context.contextManager.logger.warn {
                        "Function ${function.name} in class ${functionClass.simpleName} is suspend, " +
                                "but no coroutine scope found. It will blocking caller thread. " +
                                "Details: " +
                                "function class: ${functionClass.qualifiedName}, " +
                                "function: $function. "
                    }
                    runBlocking(block = block)
                } else {
                    coroutineScope.launch(block = block)
                }
            } else {
                try {
                    function.callByAndRethrowException(arguments)
                    onEventPosted(eventContext)
                } catch (e: Throwable) {
                    onException(e, eventContext)
                }
            }
        }

        private fun onException(e: Throwable, eventContext: EventContext<Any>) {
            context.contextManager.logger.error(e) {
                "Exception occurred while processing event ${eventContext.event} by function ${function.name} " +
                        "declared in ${functionClass.simpleName} for context $context. " +
                        "Details: " +
                        "function class: ${functionClass.qualifiedName}, " +
                        "function: $function, " +
                        "event class: ${eventContext.event::class.qualifiedName}."
            }
        }
    }

    private inner class ListenerManagerImpl(
        override val context: Context
    ) : ListenerManager {
        // All are safe.
        private val listeners = MutableEntries<AbstractListener<Any>>()

        override fun <T : Any> registerListener(
            eventClass: KClass<T>,
            filter: Boolean,
            intercept: Boolean,
            listen: Boolean,
            listener: cn.chuanwise.contexts.events.Listener<T>
        ): MutableEntry<cn.chuanwise.contexts.events.Listener<T>> {
            val finalListener =
                ListenerImpl(listener, context, eventClass, filter, intercept, listen) as AbstractListener<Any>
            return listeners.add(finalListener) as MutableEntry<cn.chuanwise.contexts.events.Listener<T>>
        }

        override fun <T : Any> registerListener(
            filter: Boolean,
            intercept: Boolean,
            listen: Boolean,
            listener: cn.chuanwise.contexts.events.Listener<T>
        ): MutableEntry<cn.chuanwise.contexts.events.Listener<T>> {
            val finalListener =
                ListenerImpl(listener, context, eventClass = null, filter, intercept, listen) as AbstractListener<Any>
            return listeners.add(finalListener) as MutableEntry<cn.chuanwise.contexts.events.Listener<T>>
        }

        fun registerListener(listener: AbstractListener<Any>) {
            listeners.add(listener)
        }

        override fun publishToContext(eventContext: EventContext<Any>) {
            for (entry in listeners) {
                (entry.value as cn.chuanwise.contexts.events.Listener<Any>).listen(eventContext)
            }
        }
    }

    private object ListenerManagerEventHandlerImpl : EventHandler {
        override fun handle(currentContext: Context, eventContext: EventContext<*>) {
            val listenerManager = currentContext.listenerManagerOrNull as? ListenerManagerImpl ?: return
            listenerManager.publishToContext(eventContext as EventContext<Any>)
        }
    }

    private val ignoreListenerAnnotationClasses = MutableEntries<KClass<out Annotation>>()

    override fun <T : Annotation> registerIgnoreListenerAnnotationClass(annotationClass: KClass<T>): MutableEntry<Class<T>> {
        return ignoreListenerAnnotationClasses.add(annotationClass) as MutableEntry<Class<T>>
    }

    private class ListenerFunctionProcessorImpl<T : Any>(
        val eventClass: KClass<T>,
        val processor: ListenerFunctionProcessor<T>
    ) : ListenerFunctionProcessor<T> by processor {
        fun safeProcess(context: ListenerAnnotationFunctionProcessContext<T>) {
            try {
                process(context)
            } catch (e: Throwable) {
                context.context.contextManager.logger.error(e) {
                    "Exception occurred while processing listener function processor $processor for event class ${eventClass.simpleName}. " +
                            "Details: " +
                            "event class: ${eventClass.qualifiedName}. "
                }
            }
        }
    }

    private val listenerFunctionProcessors = MutableEntries<ListenerFunctionProcessorImpl<Any>>()

    override fun <T : Any> registerListenerFunctionProcessor(
        eventClass: KClass<T>,
        processor: ListenerFunctionProcessor<T>
    ): MutableEntry<ListenerFunctionProcessor<T>> {
        val finalProcessor = ListenerFunctionProcessorImpl(eventClass, processor) as ListenerFunctionProcessorImpl<Any>
        return listenerFunctionProcessors.add(finalProcessor) as MutableEntry<ListenerFunctionProcessor<T>>
    }

    override fun onContextPreEnter(event: ContextPreEnterEvent) {
        val listenerManager = ListenerManagerImpl(event.context)
        event.context.addBeanByCompilationType(listenerManager)
    }

    private lateinit var listenerAnnotationFunctionProcessor: MutableEntry<AnnotationFunctionProcessor<Listener>>
    private lateinit var eventSpreaderAnnotationFunctionProcessor: MutableEntry<
            AnnotationFunctionProcessor<cn.chuanwise.contexts.events.annotations.EventSpreader>
            >

    private lateinit var eventHandler: MutableEntry<EventHandler>

    private class ReflectEventSpreaderImpl<T : Any>(
        val context: Context,
        val functionClass: KClass<*>,
        val function: KFunction<*>,
        val argumentResolvers: Map<KParameter, ArgumentResolver>
    ) : EventSpreader<T> {
        override fun spread(currentContext: Context, eventContext: EventContext<T>) {
            val beans = InheritedMutableBeanManagerImpl(context, currentContext, eventContext.beans)
            val arguments = argumentResolvers.mapValues { it.value.resolveArgument(beans) }

            if (function.isSuspend) {
                val block: suspend CoroutineScope.() -> Unit = {
                    try {
                        function.callSuspendByAndRethrowException(arguments)
                    } catch (e: Throwable) {
                        onException(e, eventContext)
                    }
                }

                val coroutineScope = context.coroutineScopeOrNull
                if (coroutineScope == null) {
                    context.contextManager.logger.warn {
                        "Function ${function.name} in class ${functionClass.simpleName} is suspend, " +
                                "but no coroutine scope found. It will blocking caller thread. " +
                                "Details: " +
                                "function class: ${functionClass.qualifiedName}, " +
                                "function: $function. "
                    }
                    runBlocking(block = block)
                } else {
                    coroutineScope.launch(block = block)
                }
            } else {
                try {
                    function.callByAndRethrowException(arguments)
                } catch (e: Throwable) {
                    onException(e, eventContext)
                }
            }
        }

        private fun onException(e: Throwable, eventContext: EventContext<T>) {
            context.contextManager.logger.error(e) {
                "Exception occurred while spreading event ${eventContext.event} by function ${function.name} " +
                        "declared in ${functionClass.simpleName} for context $context. " +
                        "Details: " +
                        "function class: ${functionClass.qualifiedName}, " +
                        "function: $function, " +
                        "event class: ${eventContext.event::class.qualifiedName}."
            }
        }

        fun safeSpared(currentContext: Context, eventContext: EventContext<T>) {
            try {
                spread(currentContext, eventContext)
            } catch (e: Throwable) {
                currentContext.contextManager.logger.error(e) {
                    "Exception occurred while spreading event ${eventContext.event} for context $currentContext. " +
                            "Details: " +
                            "event class: ${eventContext.event::class.qualifiedName}. "
                }
            }
        }
    }

    override fun onModulePreEnable(event: ModulePreEnableEvent) {
        event.addDependencyModuleClass<AnnotationModule>()
        event.addDependencyModuleClass<EventModule>()
    }

    override fun onModulePostEnable(event: ModulePostEnableEvent) {
        val contextManager = event.contextManager
        val eventModule = contextManager.eventModule
        eventHandler = eventModule.registerEventHandler(ListenerManagerEventHandlerImpl)

        val annotationModule = contextManager.annotationModule
        listenerAnnotationFunctionProcessor = annotationModule.registerAnnotationFunctionProcessor(Listener::class) {
            val function = it.function
            val value = it.value
            val context = it.context

            val annotations = it.function.annotations
            for (annotation in annotations) {
                if (ignoreListenerAnnotationClasses.any { cls -> cls.value.isInstance(annotation) }) {
                    return@registerAnnotationFunctionProcessor
                }
            }

            val functionClass = value::class
            val (argumentResolvers, eventClass) = context.parseSubjectClassAndCollectArgumentResolvers<Any>(
                functionClass = functionClass,
                function = function,
                defaultSubjectClass = it.annotation.eventClass.takeIf { cls -> cls != Nothing::class },
                subjectAnnotationClass = Event::class
            )

            for (entry in listenerFunctionProcessors) {
                if (entry.value.eventClass.isSuperclassOf(eventClass)) {
                    val listenerFunctionProcessContext =
                        ListenerAnnotationFunctionProcessContextImpl(eventClass, argumentResolvers, it)
                    entry.value.safeProcess(listenerFunctionProcessContext as ListenerAnnotationFunctionProcessContext<Any>)
                    return@registerAnnotationFunctionProcessor
                }
            }

            val listener = ReflectListenerImpl(
                functionClass, function, argumentResolvers, context, eventClass as KClass<Any>,
                it.annotation.filter, it.annotation.intercept, it.annotation.listen
            )

            val listenerManager = context.listenerManager as ListenerManagerImpl
            listenerManager.registerListener(listener)
        }
        eventSpreaderAnnotationFunctionProcessor = annotationModule.registerAnnotationFunctionProcessor(
            cn.chuanwise.contexts.events.annotations.EventSpreader::class
        ) {
            val function = it.function
            val value = it.value
            val context = it.context

            val functionClass = value::class
            val (argumentResolvers, eventClass) = context.parseSubjectClassAndCollectArgumentResolvers<Any>(
                functionClass = functionClass,
                function = function,
                defaultSubjectClass = it.annotation.eventClass.takeIf { cls -> cls != Nothing::class },
                subjectAnnotationClass = Event::class
            )

            val spreader = ReflectEventSpreaderImpl<Any>(it.context, functionClass, function, argumentResolvers)
            val eventPublisher = it.context.eventPublisher
            eventPublisher.registerEventSpreader(eventClass as Class<Any>, spreader)
        }
    }

    override fun onModulePostDisable(event: ModulePostDisableEvent) {
        listenerAnnotationFunctionProcessor.remove()
        eventSpreaderAnnotationFunctionProcessor.remove()
        eventHandler.remove()
    }
}