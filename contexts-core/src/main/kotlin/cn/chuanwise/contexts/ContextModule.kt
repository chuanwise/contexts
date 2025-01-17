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

import cn.chuanwise.contexts.events.ContextPostAddEvent
import cn.chuanwise.contexts.events.ContextPostEnterEvent
import cn.chuanwise.contexts.events.ContextPostExitEvent
import cn.chuanwise.contexts.events.ContextPostRemoveEvent
import cn.chuanwise.contexts.events.ContextPreAddEvent
import cn.chuanwise.contexts.events.ContextPreEnterEvent
import cn.chuanwise.contexts.events.ContextPreExitEvent
import cn.chuanwise.contexts.events.ContextPreRemoveEvent

interface ContextModule {
    fun onContextPreAdd(event: ContextPreAddEvent): Unit = Unit
    fun onContextPostAdd(event: ContextPostAddEvent): Unit = Unit

    fun onContextPreRemove(event: ContextPreRemoveEvent): Unit = Unit
    fun onContextPostRemove(event: ContextPostRemoveEvent): Unit = Unit

    fun onContextPreEnter(event: ContextPreEnterEvent): Unit = Unit
    fun onContextPostEnter(event: ContextPostEnterEvent): Unit = Unit

    fun onContextPreExit(event: ContextPreExitEvent): Unit = Unit
    fun onContextPostExit(event: ContextPostExitEvent): Unit = Unit
}