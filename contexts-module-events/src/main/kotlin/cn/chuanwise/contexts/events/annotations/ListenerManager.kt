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
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_FILTER
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_INTERCEPT
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_LISTEN
import cn.chuanwise.contexts.events.EventContext
import cn.chuanwise.contexts.events.Listener
import cn.chuanwise.contexts.util.BeanManager
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableEntry
import kotlin.reflect.KClass

/**
 * 监听器管理器。
 *
 * @author Chuanwise
 */
interface ListenerManager {
    /**
     * 上下文。
     */
    val context: Context

    /**
     * 注册监听器。
     *
     * @param T 事件类型
     * @param eventClass 事件类型
     * @param filter 是否过滤
     * @param intercept 是否拦截
     * @param listen 是否在函数正常结束后标记为已监听
     * @param listener 监听器
     * @return 监听器
     */
    fun <T : Any> registerListener(
        eventClass: KClass<T>,
        filter: Boolean = DEFAULT_LISTENER_FILTER,
        intercept: Boolean = DEFAULT_LISTENER_INTERCEPT,
        listen: Boolean = DEFAULT_LISTENER_LISTEN,
        listener: Listener<T>
    ) : MutableEntry<Listener<T>>

    fun <T : Any> registerListener(
        filter: Boolean = DEFAULT_LISTENER_FILTER,
        intercept: Boolean = DEFAULT_LISTENER_INTERCEPT,
        listen: Boolean = DEFAULT_LISTENER_LISTEN,
        listener: Listener<T>
    ) : MutableEntry<Listener<T>>

    /**
     * 发布事件到当前上下文。
     *
     * @param eventContext 事件上下文
     */
    @ContextsInternalApi
    fun publishToContext(eventContext: EventContext<Any>)
}
