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

package cn.chuanwise.contexts.annotation

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.context.ContextBeanPostAddEvent
import cn.chuanwise.contexts.context.ContextInitEvent
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.context.ContextPostEnterEvent
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableEntries
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.addBean
import kotlin.reflect.KClass
import kotlin.reflect.full.functions

/**
 * 负责注解扫描的模块。
 *
 * 该模块在每个上下文内注册一个 [AnnotationManager]。
 *
 * @author Chuanwise
 * @see createAnnotationModule
 */
interface AnnotationModule : Module {
    /**
     * 注册一个注解类型函数处理器 。
     *
     * @param T 类型
     * @param annotationClass 注解类
     * @param processor 扫描器
     * @return 扫描器
     */
    fun <T : Annotation> registerAnnotationFunctionProcessor(
        annotationClass: KClass<T>, processor: AnnotationFunctionProcessor<T>
    ): MutableEntry<AnnotationFunctionProcessor<T>>

    /**
     * 注册一个参数解析器工厂。
     *
     * @param argumentResolverFactory 参数解析器工厂
     * @return 参数解析器工厂
     */
    fun registerArgumentResolverFactory(
        argumentResolverFactory: ArgumentResolverFactory
    ): MutableEntry<ArgumentResolverFactory>

    /**
     * 尝试获取一个参数解析器。
     *
     * @param argumentResolveContext 参数解析器上下文
     * @return 参数解析器
     */
    fun createArgumentResolver(argumentResolveContext: ArgumentResolveContext): ArgumentResolver?
}

@Suppress("UNCHECKED_CAST")
@ContextsInternalApi
class AnnotationModuleImpl : AnnotationModule {
    private class AnnotationFunctionProcessorImpl<T : Annotation>(
        val annotationClass: KClass<T>,
        val processor: AnnotationFunctionProcessor<T>
    ) : AnnotationFunctionProcessor<T> {
        override fun process(context: AnnotationFunctionProcessContext<T>) {
            processor.process(context)
        }
    }

    private val functionProcessors = MutableEntries<AnnotationFunctionProcessorImpl<Annotation>>()
    private val argumentResolverFactories = MutableEntries<ArgumentResolverFactory>()

    override fun <T : Annotation> registerAnnotationFunctionProcessor(
        annotationClass: KClass<T>,
        processor: AnnotationFunctionProcessor<T>
    ): MutableEntry<AnnotationFunctionProcessor<T>> {
        val finalFunctionProcessor = AnnotationFunctionProcessorImpl(annotationClass, processor) as AnnotationFunctionProcessorImpl<Annotation>
        return functionProcessors.add(finalFunctionProcessor) as MutableEntry<AnnotationFunctionProcessor<T>>
    }

    override fun registerArgumentResolverFactory(
        argumentResolverFactory: ArgumentResolverFactory
    ): MutableEntry<ArgumentResolverFactory> {
        return argumentResolverFactories.add(argumentResolverFactory)
    }

    override fun createArgumentResolver(argumentResolveContext: ArgumentResolveContext): ArgumentResolver? {
        for (entry in argumentResolverFactories) {
            try {
                entry.value.tryCreateArgumentResolver(argumentResolveContext)?.let { return it }
            } catch (e: Throwable) {
                argumentResolveContext.context.contextManager.logger.error(e) {
                    "Error occurred while creating argument resolver by factory ${entry.value::class.qualifiedName} " +
                            "for function ${argumentResolveContext.function.name}. " +
                            "Details: " +
                            "context: ${argumentResolveContext.context}, " +
                            "resolving parameter name: ${argumentResolveContext.parameter.name}, " +
                            "resolving function: ${argumentResolveContext.function}."
                }
            }
        }
        return null
    }

    private inner class AnnotationManagerImpl(override val context: Context) : AnnotationManager {
        override fun scan(value: Any) {
            val valueClass = value::class

            for (function in valueClass.functions) {
                for (annotation in function.annotations) {
                    for (entry in functionProcessors) {
                        if (entry.value.annotationClass.isInstance(annotation)) {
                            val annotationContext = AnnotationFunctionProcessContextImpl(function, annotation, value, context)
                            try {
                                entry.value.process(annotationContext)
                            } catch (e: Throwable) {
                                context.contextManager.logger.error(e) {
                                    "Error occurred while processing annotation @${annotation.annotationClass.simpleName}: " +
                                            "for function ${function.name} in class ${valueClass.simpleName}, " +
                                            "which was thrown by function processor ${entry.value.processor}. " +
                                            "Details: " +
                                            "class where function in: ${valueClass.qualifiedName}, " +
                                            "function id: $function, " +
                                            "annotation class name: ${annotation.annotationClass.qualifiedName}, " +
                                            "processor class name: ${entry.value.processor::class.qualifiedName}. "
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onContextInit(event: ContextInitEvent) {
        event.context.addBean(AnnotationManagerImpl(event.context))
    }

    override fun onContextBeanPostAdd(event: ContextBeanPostAddEvent<*>) {
        val annotationManager = event.context.annotationManagerOrNull ?: return
        val value = event.value ?: return
        annotationManager.scan(value)
    }
}