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

package cn.chuanwise.contexts.context

import cn.chuanwise.contexts.util.ContextsInternalApi

/**
 * 上下文退出事件。
 *
 * @author Chuanwise
 */
interface ContextExitEvent : ContextEvent {
    val context: Context
}

interface ContextPreExitEvent : ContextExitEvent
interface ContextPostExitEvent : ContextExitEvent

@ContextsInternalApi
data class ContextPreExitEventImpl(
    override val context: Context,
    override val contextManager: ContextManager,
) : ContextPreExitEvent

@ContextsInternalApi
data class ContextPostExitEventImpl(
    override val context: Context,
    override val contextManager: ContextManager,
) : ContextPostExitEvent