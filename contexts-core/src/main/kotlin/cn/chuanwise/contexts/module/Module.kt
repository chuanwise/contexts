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

import cn.chuanwise.contexts.context.ContextBeanPostAddEvent
import cn.chuanwise.contexts.context.ContextBeanPostRemoveEvent
import cn.chuanwise.contexts.context.ContextBeanPreAddEvent
import cn.chuanwise.contexts.context.ContextBeanPreRemoveEvent
import cn.chuanwise.contexts.context.ContextInitEvent
import cn.chuanwise.contexts.context.ContextPostEdgeAddEvent
import cn.chuanwise.contexts.context.ContextPostEnterEvent
import cn.chuanwise.contexts.context.ContextPostExitEvent
import cn.chuanwise.contexts.context.ContextPostEdgeRemoveEvent
import cn.chuanwise.contexts.context.ContextPreEdgeAddEvent
import cn.chuanwise.contexts.context.ContextPreEnterEvent
import cn.chuanwise.contexts.context.ContextPreExitEvent
import cn.chuanwise.contexts.context.ContextPreEdgeRemoveEvent

interface Module {
    fun onModulePreEnable(event: ModulePreEnableEvent): Unit = Unit
    fun onModulePostEnable(event: ModulePostEnableEvent): Unit = Unit

    fun onModulePreDisable(event: ModulePreDisableEvent): Unit = Unit
    fun onModulePostDisable(event: ModulePostDisableEvent): Unit = Unit

    fun onContextEdgePreAdd(event: ContextPreEdgeAddEvent): Unit = Unit
    fun onContextEdgePostAdd(event: ContextPostEdgeAddEvent): Unit = Unit

    fun onContextEdgePreRemove(event: ContextPreEdgeRemoveEvent): Unit = Unit
    fun onContextEdgePostRemove(event: ContextPostEdgeRemoveEvent): Unit = Unit

    fun onContextInit(event: ContextInitEvent): Unit = Unit

    fun onContextPreEnter(event: ContextPreEnterEvent): Unit = Unit
    fun onContextPostEnter(event: ContextPostEnterEvent): Unit = Unit

    fun onContextPreExit(event: ContextPreExitEvent): Unit = Unit
    fun onContextPostExit(event: ContextPostExitEvent): Unit = Unit

    fun onContextBeanPreAdd(event: ContextBeanPreAddEvent<*>): Unit = Unit
    fun onContextBeanPostAdd(event: ContextBeanPostAddEvent<*>): Unit = Unit

    fun onContextBeanPreRemove(event: ContextBeanPreRemoveEvent<*>): Unit = Unit
    fun onContextBeanPostRemove(event: ContextBeanPostRemoveEvent<*>): Unit = Unit
}