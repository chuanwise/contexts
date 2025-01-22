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
 * 上下文添加事件。
 *
 * @author Chuanwise
 */
interface ContextAddEvent : ContextEvent {
    val parent: Context
    val child: Context
    val isEntering: Boolean
}

interface ContextPreAddEvent: ContextAddEvent
interface ContextPostAddEvent: ContextAddEvent

@ContextsInternalApi
data class ContextPreAddEventImpl(
    override val parent: Context,
    override val child: Context,
    override val isEntering: Boolean,
    override val contextManager: ContextManager
) : ContextPreAddEvent

@ContextsInternalApi
data class ContextPostAddEventImpl(
    override val parent: Context,
    override val child: Context,
    override val isEntering: Boolean,
    override val contextManager: ContextManager
) : ContextPostAddEvent