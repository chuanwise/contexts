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

package cn.chuanwise.contexts.bukkit.player

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextPreEnterEvent
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.filters.annotations.Filter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

interface PlayerContext {
    val playerName: String
    val playerUuid: UUID

    val player: Player
}

class PlayerContextImpl(
    override val player: Player,
    override val playerName: String = player.name,
    override val playerUuid: UUID = player.uniqueId
) : PlayerContext {
    @Listener
    fun onEnter(event: ContextPreEnterEvent) {
        event.context.registerBean(player)
    }

    @Filter
    fun PlayerEvent.onPlayerEvent(): Boolean {
        return player.name == playerName
    }

    @EventHandler
    fun PlayerQuitEvent.onPlayerQuit(context: Context) {
        println("Player ${player.name} quit.")
        context.exit()
    }
}