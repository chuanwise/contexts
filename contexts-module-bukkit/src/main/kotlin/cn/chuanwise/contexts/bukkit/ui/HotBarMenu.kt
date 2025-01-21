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

import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.entity.Player

/**
 * 快捷栏菜单，是玩家游戏界面下方物品栏组成的菜单。一般有 9 个物品栏。
 * 只能由玩家打开，故其上下文对象必须包含一个 [Player] 实例。
 *
 * 其可以显示非空数量的按钮。若按钮数量多于 9 个，则继续往后滚动时，
 * 将会将所有按钮前移并腾出位置显示其他按钮。可以长按 shift 键来快速滚动。
 *
 * 按钮聚焦状态改变时将会向对应按钮状态发送 [HotBarMenuFocusStatusChangedEvent]。
 * 所有事件只会被传播到当前聚焦的按钮上。
 *
 * 不可以在不同玩家之间复用 [HotBarMenu] 对象，应该针对每个玩家创建一个专门的实例。
 *
 * @author Chuanwise
 */
interface HotBarMenu {
    val buttons: List<HotBarMenuButton>
}

@ContextsInternalApi
class HotBarMenuImpl(
    override val buttons: List<HotBarMenuButton>
) : HotBarMenu