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

package cn.chuanwise.contexts.filters.annotations

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.annotation.AnnotationModule
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.annotation.ArgumentResolver
import cn.chuanwise.contexts.annotation.AnnotationFunctionProcessor
import cn.chuanwise.contexts.annotation.annotationModule
import cn.chuanwise.contexts.filters.FilterContext
import cn.chuanwise.contexts.filters.FilterModule
import cn.chuanwise.contexts.filters.filterManagerOrNull
import cn.chuanwise.contexts.module.ModulePostDisableEvent
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.module.ModulePreEnableEvent
import cn.chuanwise.contexts.module.addDependencyModuleClass
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeanManagerImpl
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.callByAndRethrowException
import cn.chuanwise.contexts.util.callSuspendByAndRethrowException
import cn.chuanwise.contexts.util.parseSubjectClassAndCollectArgumentResolvers
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

/**
 * 过滤器注解模块，在进入状态之前把带 [Filter] 注解的函数注册为过滤器。
 *
 * @author Chuanwise
 * @see createFilterAnnotationModule
 */
interface FilterAnnotationModule : Module

@ContextsInternalApi
class FilterAnnotationModuleImpl : FilterAnnotationModule {
    private class ReflectFilterImpl(
        private val context: Context,
        private val functionClass: KClass<*>,
        private val function: KFunction<*>,
        private val argumentResolvers: Map<KParameter, ArgumentResolver>,
        private val filterValueClass: KClass<*>,
    ) : cn.chuanwise.contexts.filters.Filter<Any> {
        override fun filter(filterContext: FilterContext<Any>): Boolean? {
            if (!filterValueClass.isInstance(filterContext.value)) {
                return null
            }

            val beans = InheritedMutableBeanManagerImpl(context, filterContext.beans)
            val arguments = argumentResolvers.mapValues { it.value.resolveArgument(beans) }

            return if (function.isSuspend) runBlocking {
                try {
                    function.callSuspendByAndRethrowException(arguments)
                } catch (e: Throwable) {
                    onException(e, filterContext.value)
                }
            } else {
                try {
                    function.callByAndRethrowException(arguments)
                } catch (e: Throwable) {
                    onException(e, filterContext.value)
                }
            } as Boolean?
        }

        private fun onException(e: Throwable, value: Any) {
            context.contextManager.logger.error(e) {
                "Exception occurred while filtering value $value by function ${function.name} " +
                        "declared in ${functionClass.simpleName} for context $context. " +
                        "Details: " +
                        "function class: ${functionClass.qualifiedName}, " +
                        "function: $function, " +
                        "value class: ${value::class.qualifiedName}."
            }
        }
    }

    private lateinit var annotationFunctionProcessor: MutableEntry<AnnotationFunctionProcessor<Filter>>

    override fun onModulePreEnable(event: ModulePreEnableEvent) {
        event.addDependencyModuleClass<AnnotationModule>()
        event.addDependencyModuleClass<FilterModule>()
    }

    override fun onModulePostEnable(event: ModulePostEnableEvent) {
        val annotationModule = event.contextManager.annotationModule
        annotationFunctionProcessor = annotationModule.registerAnnotationFunctionProcessor(Filter::class) {
            val function = it.function
            val value = it.value
            val context = it.context

            val functionClass = value::class
            val (argumentResolvers, valueClass) = context.parseSubjectClassAndCollectArgumentResolvers<Any>(
                functionClass = functionClass,
                function = function,
                defaultSubjectClass = it.annotation.valueClass.takeIf { it != Nothing::class },
                subjectAnnotationClass = Value::class
            )
            val filter = ReflectFilterImpl(it.context, functionClass, function, argumentResolvers, valueClass)
            context.filterManagerOrNull?.registerFilter(it.annotation.cache, filter)
        }
    }

    override fun onModulePostDisable(event: ModulePostDisableEvent) {
        annotationFunctionProcessor.remove()
    }
}