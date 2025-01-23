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

import cn.chuanwise.contexts.util.BeanFactory
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
    private class OptionalArgumentResolverImpl(private val context: ArgumentResolveContext) : ArgumentResolver {
        override fun resolveArgument(beanFactory: BeanFactory): Any? {
            return beanFactory.getBeanValue(context.parameter.type.javaType, key = context.parameter.name)
        }
    }

    private class RequiredArgumentResolverImpl(private val context: ArgumentResolveContext) : ArgumentResolver {
        override fun resolveArgument(beanFactory: BeanFactory): Any {
            beanFactory.getBeanValue(context.parameter.type.javaType, key = context.parameter.name)?.let { return it }

            error("Cannot resolve argument for parameter ${context.parameter.name} of type ${context.parameter.type} " +
                    "caused by missing bean. " +
                    "while trying to call ${context.function.name} declared in ${context.functionClass.name}. " +
                    "Details: " +
                    "function: ${context.function}, " +
                    "function class: ${context.functionClass.name}. ")
        }
    }

    override fun tryCreateArgumentResolver(context: ArgumentResolveContext): ArgumentResolver {
        return if (context.parameter.isOptional) {
            OptionalArgumentResolverImpl(context)
        } else {
            RequiredArgumentResolverImpl(context)
        }
    }
}