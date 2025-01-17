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

/**
 * 表示一个上下文模块的实例。
 *
 * @author Chuanwise
 */
interface ContextModuleEntry {
    /**
     * 是否已经被删除。
     */
    val isRemoved: Boolean

    /**
     * 此模块的实例。
     */
    val module: ContextModule

    /**
     * 管理此模块的上下文管理器。
     */
    val contextManager: ContextManager

    /**
     * 删除此模块。
     */
    fun remove()
}