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

package cn.chuanwise.contexts

import cn.chuanwise.contexts.util.ContextsInternalApi

/**
 * 上下文移除事件。
 *
 * @author Chuanwise
 */
interface ContextRemoveEvent : ContextEvent {
    val parent: Context
    val child: Context
    val replace: ContextAddEvent?
    val exit: Boolean
}

interface ContextPreRemoveEvent: ContextRemoveEvent {
    /**
     * 移除后也退出那些没有父上下文的子上下文。
     */
    var exitChildIfItWillBeRoot: Boolean
}
interface ContextPostRemoveEvent: ContextRemoveEvent

@ContextsInternalApi
data class ContextPreRemoveEventImpl(
    override val parent: Context,
    override val child: Context,
    override val replace: ContextAddEvent?,
    override val exit: Boolean,
    override val contextManager: ContextManager,
    override var exitChildIfItWillBeRoot: Boolean = true
) : ContextPreRemoveEvent

@ContextsInternalApi
data class ContextPostRemoveEventImpl(
    override val parent: Context,
    override val child: Context,
    override val replace: ContextAddEvent?,
    override val exit: Boolean,
    override val contextManager: ContextManager,
) : ContextPostRemoveEvent