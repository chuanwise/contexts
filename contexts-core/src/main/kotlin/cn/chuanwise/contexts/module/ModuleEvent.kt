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

import cn.chuanwise.contexts.ContextManager
import cn.chuanwise.contexts.util.NotStableForInheritance

/**
 * 模块事件。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface ModuleEvent {
    /**
     * 本模块的 ID。
     */
    val id: String?

    /**
     * 本模块。
     */
    val module: Module

    /**
     * 上下文管理器。
     */
    val contextManager: ContextManager
}

/**
 * 模块预启用事件。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface ModulePreEnableEvent : ModuleEvent {
    /**
     * 本模块的 ID。
     */
    override var id: String?

    /**
     * 声明一个本模块的依赖模块的类型。本模块将在所有依赖模块启用后才启用。
     *
     * @param moduleClass 依赖模块的类型
     */
    fun addDependencyModuleClass(moduleClass: Class<out Module>)

    /**
     * 声明一个本模块的依赖模块的 ID。本模块将在所有依赖模块启用后才启用。
     *
     * @param id 依赖模块的 ID
     */
    fun addDependencyModuleId(id: String)

    /**
     * 本模块的依赖模块是否启用。
     */
    fun isDependencyModuleEnabled(): Boolean

    fun isDependencyModuleClass(moduleClass: Class<out Module>): Boolean
    fun isDependencyModuleId(id: String): Boolean
}

inline fun <reified T : Module> ModulePreEnableEvent.addDependencyModuleClass() {
    addDependencyModuleClass(T::class.java)
}

@NotStableForInheritance
interface ModulePostEnableEvent : ModuleEvent

@NotStableForInheritance
interface ModulePreDisableEvent : ModuleEvent

@NotStableForInheritance
interface ModulePostDisableEvent : ModuleEvent