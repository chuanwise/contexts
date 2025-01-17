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
import cn.chuanwise.contexts.ContextManager
import cn.chuanwise.contexts.util.ContextsInternalApi

/**
 * 上下文进入事件。
 *
 * @author Chuanwise
 */
interface ContextEnterEvent : ContextEvent {
    val context: Context
}

interface ContextPreEnterEvent : ContextEnterEvent
interface ContextPostEnterEvent : ContextEnterEvent

@ContextsInternalApi
data class ContextPreEnterEventImpl(
    override val context: Context,
    override val contextManager: ContextManager,
) : ContextPreEnterEvent

@ContextsInternalApi
data class ContextPostEnterEventImpl(
    override val context: Context,
    override val contextManager: ContextManager,
) : ContextPostEnterEvent