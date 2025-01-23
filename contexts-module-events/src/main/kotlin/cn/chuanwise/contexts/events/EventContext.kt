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

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.filters.FilterContext
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableBeanFactory
import cn.chuanwise.contexts.util.NotStableForInheritance

/**
 * 事件上下文。
 *
 * @param T 事件类型
 * @author Chuanwise
 */
@NotStableForInheritance
interface EventContext<out T : Any> {
    /**
     * 事件
     */
    val event: T

    /**
     * 事件过滤器上下文。
     */
    val filterContext: FilterContext<T>

    /**
     * 事件是从哪个上下文发布的。
     */
    val context: Context

    /**
     * 和事件发布相关的上下文，用于存储一些和事件发布相关的数据。
     * 继承了 [context] 内的所有数据。
     */
    val beans: MutableBeanFactory

    /**
     * 是否被拦截
     */
    val isIntercepted: Boolean

    /**
     * 是否被监听
     */
    val isListened: Boolean

    /**
     * 拦截事件
     */
    fun intercept()

    /**
     * 标记事件已经被监听
     */
    fun listen()
}

@ContextsInternalApi
class EventContextImpl<T : Any>(
    override val event: T,
    override val context: Context,
    override val beans: MutableBeanFactory,
    override val filterContext: FilterContext<T>
) : EventContext<T> {
    private var mutableIsIntercepted: Boolean = false
    override val isIntercepted: Boolean get() = mutableIsIntercepted

    private var mutableIsListened: Boolean = false
    override val isListened: Boolean get() = mutableIsListened

    override fun intercept() {
        mutableIsIntercepted = true
    }

    override fun listen() {
        mutableIsListened = true
    }
}