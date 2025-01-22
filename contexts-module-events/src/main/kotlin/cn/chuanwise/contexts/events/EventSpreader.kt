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
import cn.chuanwise.contexts.util.ContextsInternalApi

/**
 * 事件传播器，负责处理事件给一个上下文的所有子发布的流程。
 *
 * @param T 事件类型
 * @author Chuanwise
 */
interface EventSpreader<T : Any> {
    fun spread(currentContext: Context, eventContext: EventContext<T>)
}

/**
 * 将事件发布给所有子上下文的事件传播器。
 *
 * @author Chuanwise
 */
object ChildrenEventSpreader : EventSpreader<Any> {
    @OptIn(ContextsInternalApi::class)
    override fun spread(currentContext: Context, eventContext: EventContext<Any>) {
        currentContext.children.forEach {
            it.eventPublisher.publish(eventContext)
        }
    }
}