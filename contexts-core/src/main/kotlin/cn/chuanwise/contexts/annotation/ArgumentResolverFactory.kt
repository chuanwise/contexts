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

import cn.chuanwise.contexts.util.Bean
import cn.chuanwise.contexts.util.BeanManager
import cn.chuanwise.contexts.util.createResolvableType
import cn.chuanwise.contexts.util.getBean
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaType

/**
 * 用于创建参数解析器的工厂。
 *
 * @author Chuanwise
 */
interface ArgumentResolverFactory {
    /**
     * 尝试创建参数解析器。
     *
     * @param context 参数解析上下文
     * @return 参数解析器，若失败则返回 `null`。
     */
    fun tryCreateArgumentResolver(context: ArgumentResolveContext): ArgumentResolver?
}

object DefaultArgumentResolverFactory : ArgumentResolverFactory {
    private interface BeanResolver {
        fun resolve(beanManager: BeanManager, context: ArgumentResolveContext): Any?
    }

    private object DefaultBeanResolver : BeanResolver {
        override fun resolve(beanManager: BeanManager, context: ArgumentResolveContext): Any? {
            val beanType = createResolvableType(context.parameter.type)
            beanManager.getBean(beanType, context.parameter.name)?.let { return it }
            beanManager.getBean(beanType)?.let { return it }
            return null
        }
    }

    private class AnnotatedBeanResolver(
        private val id: String?, private val primary: Boolean?
    ) : BeanResolver {
        override fun resolve(beanManager: BeanManager, context: ArgumentResolveContext): Any? {
            return beanManager.getBean(id, primary)
        }
    }

    private class OptionalArgumentResolverImpl(
        private val resolver: BeanResolver,
        private val context: ArgumentResolveContext
    ) : ArgumentResolver {
        override fun resolveArgument(beanManager: BeanManager): Any? = resolver.resolve(beanManager, context)
    }

    private class RequiredArgumentResolverImpl(
        private val resolver: BeanResolver,
        private val context: ArgumentResolveContext
    ) : ArgumentResolver {
        override fun resolveArgument(beanManager: BeanManager): Any {
            resolver.resolve(beanManager, context)?.let { return it }

            error("Cannot resolve argument for parameter ${context.parameter.name} of type ${context.parameter.type} " +
                    "caused by missing bean. " +
                    "while trying to call ${context.function.name} declared in ${context.functionClass.name}. " +
                    "Details: " +
                    "function: ${context.function}, " +
                    "function class: ${context.functionClass.name}. ")
        }
    }

    override fun tryCreateArgumentResolver(context: ArgumentResolveContext): ArgumentResolver {
        val beanAnnotation = context.parameter.findAnnotation<Bean>()
        val resolver: BeanResolver = if (beanAnnotation == null) {
            DefaultBeanResolver
        } else {
            AnnotatedBeanResolver(beanAnnotation.id.ifEmpty { null }, beanAnnotation.primary.toBooleanOrNull())
        }

        return if (context.parameter.isOptional) {
            OptionalArgumentResolverImpl(resolver, context)
        } else {
            RequiredArgumentResolverImpl(resolver, context)
        }
    }
}