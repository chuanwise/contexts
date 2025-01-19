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

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.util.AllChildrenAndContextScope
import cn.chuanwise.contexts.util.ContextScope
import cn.chuanwise.contexts.util.Scope

/**
 * 事件发布器。
 *
 * @author Chuanwise
 */
interface EventPublisher<T : Any> {
    /**
     * 上下文。
     */
    val context: Context

    /**
     * 仅仅将事件发布给当前上下文中的监听器。
     *
     * @param event 事件
     */
    fun publishToContext(event: T) = publish(event, ContextScope)

    /**
     * 发布事件。
     *
     * 事件类上可以使用 [Scope] 标注其传播范围，默认的范围是 [AllChildrenAndContextScope]。
     *
     * @param event 事件
     */
    fun publish(event: T, scope: Scope?)
    fun publish(event: T) = publish(event, scope = null)
}
