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

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextModule
import cn.chuanwise.contexts.events.ContextPostEnterEvent
import cn.chuanwise.contexts.filters.filterManagerOrNull
import cn.chuanwise.contexts.util.Beans
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeans
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

class FiltersAnnotationsContextModule : ContextModule {
    private class ReflectFilterImpl(
        private val context: Context,
        private val function: KFunction<*>,
        private val instance: Any,
        filterValueClass: Class<*>?,
    ) : cn.chuanwise.contexts.filters.Filter<Any> {
        interface ArgumentFactory {
            fun create(value: Any, context: Beans): Any
        }
        private val argumentFactories: List<ArgumentFactory>

        object ThatValueArgumentFactory : ArgumentFactory {
            override fun create(value: Any, context: Beans): Any = value
        }
        class FromBeansArgumentFactory(private val parameter: KParameter) : ArgumentFactory {
            override fun create(value: Any, context: Beans): Any {
                return context.getBeanValueOrFail(parameter.type.javaType, key = parameter.name)
            }
        }

        val filterValueClass: Class<*>

        init {
            require(function.returnType == Boolean::class.createType()) {
                "Filter function must return a boolean."
            }

            var filterValueClassLocal = filterValueClass.takeIf { it != Nothing::class.java }
            val parameters = function.valueParameters

            argumentFactories = mutableListOf()
            for (parameter in parameters) {
                val valueAnnotation = parameter.findAnnotation<Value>()
                if (valueAnnotation != null) {
                    require(filterValueClassLocal == null || filterValueClassLocal == parameter.type.javaType) {
                        "Cannot set multiple different value classes (specified $filterValueClassLocal and ${parameter.type}) for a filter."
                    }
                    filterValueClassLocal = parameter.type.javaType as Class<*>
                    argumentFactories.add(ThatValueArgumentFactory)
                    continue
                }

                argumentFactories.add(FromBeansArgumentFactory(parameter))
            }

            if (filterValueClassLocal == null) {
                filterValueClassLocal = parameters.singleOrNull()?.type?.javaType as? Class<*>
            }
            require(filterValueClassLocal != null) {
                "Cannot find value class for filter."
            }
            this.filterValueClass = filterValueClassLocal
        }

        @OptIn(ContextsInternalApi::class)
        override fun onFilter(value: Any): Boolean? {
            if (!filterValueClass.isInstance(value)) {
                return null
            }
            function.isAccessible = true

            val beans = InheritedMutableBeans(context).apply {
                registerBean(value, primary = true)
            }
            val arguments = argumentFactories.map { it.create(value, beans) }.toTypedArray()

            return if (function.isSuspend) runBlocking {
                function.callSuspend(instance, *arguments)
            } else {
                function.call(instance, *arguments)
            } as Boolean?
        }
    }

    override fun onContextPostEnter(event: ContextPostEnterEvent) {
        val filterManager = event.context.filterManagerOrNull ?: return

        for (bean in event.context.contextBeans) {
            val value = bean.value
            val valueClass = value::class
            for (function in valueClass.memberFunctions) {
                val filterAnnotation = function.annotations.singleOrNull { it is Filter } as? Filter ?: continue
                val filterValueClass = filterAnnotation.valueClass.takeIf { it != Nothing::class }

                filterManager.registerFilter(ReflectFilterImpl(event.context, function, value, filterValueClass?.java))
            }
        }
    }
}