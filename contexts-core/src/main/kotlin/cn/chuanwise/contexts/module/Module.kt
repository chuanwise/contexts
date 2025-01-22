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

package cn.chuanwise.contexts.module

import cn.chuanwise.contexts.ContextPostAddEvent
import cn.chuanwise.contexts.ContextPostEnterEvent
import cn.chuanwise.contexts.ContextPostExitEvent
import cn.chuanwise.contexts.ContextPostRemoveEvent
import cn.chuanwise.contexts.ContextPreAddEvent
import cn.chuanwise.contexts.ContextPreEnterEvent
import cn.chuanwise.contexts.ContextPreExitEvent
import cn.chuanwise.contexts.ContextPreRemoveEvent

interface Module {
    fun onModulePreEnable(event: ModulePreEnableEvent): Unit = Unit
    fun onModulePostEnable(event: ModulePostEnableEvent): Unit = Unit

    fun onModulePreDisable(event: ModulePreDisableEvent): Unit = Unit
    fun onModulePostDisable(event: ModulePostDisableEvent): Unit = Unit

    fun onContextPreAdd(event: ContextPreAddEvent): Unit = Unit
    fun onContextPostAdd(event: ContextPostAddEvent): Unit = Unit

    fun onContextPreRemove(event: ContextPreRemoveEvent): Unit = Unit
    fun onContextPostRemove(event: ContextPostRemoveEvent): Unit = Unit

    fun onContextPreEnter(event: ContextPreEnterEvent): Unit = Unit
    fun onContextPostEnter(event: ContextPostEnterEvent): Unit = Unit

    fun onContextPreExit(event: ContextPreExitEvent): Unit = Unit
    fun onContextPostExit(event: ContextPostExitEvent): Unit = Unit
}