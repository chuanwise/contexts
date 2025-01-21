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

package cn.chuanwise.contexts.bukkit.ui

/**
 * 快捷栏菜单事件。
 *
 * @author Chuanwise
 * @see HotBarMenu
 */
interface HotBarMenuEvent {
    val menu: HotBarMenu
}

/**
 * 快捷栏菜单按钮聚焦状态改变事件。
 *
 * @author Chuanwise
 */
interface HotBarMenuFocusStatusChangedEvent {
    val focusStatus: Boolean
}