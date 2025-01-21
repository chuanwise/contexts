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

import cn.chuanwise.contexts.bukkit.Plugin
import cn.chuanwise.contexts.bukkit.player.createPlayerSession
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

@ContextsInternalApi
object HotBarTestMenuCommand : CommandExecutor {
    class DebugHotBarMenuButton(
        override val itemStack: ItemStack
    ) : HotBarMenuButton {
        @Listener
        private fun onFocusOn(event: HotBarMenuFocusStatusChangedEvent) {
            println("Focus on: ${event.focusStatus} for $itemStack")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        onCommand(sender, (args as? Array<String>) ?: emptyArray())
        return true
    }

    private fun onCommand(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return
        }

        val buttonCount = args.getOrNull(0)?.toIntOrNull() ?: 9
        val buttons = mutableListOf<HotBarMenuButton>()
        for (i in 0 until buttonCount) {
            buttons.add(DebugHotBarMenuButton(ItemStack(Material.GRASS_BLOCK, i)))
        }

        val menu = createHotBarMenu(buttons)
        val playerContext = Plugin.pluginContext.getChildByBean(sender)
            ?: Plugin.pluginContext.enterChild(createPlayerSession(sender), key = "Player[${sender.name}]")

        var onlineHotBarMenuContext = playerContext.getChildByBeanClass(OnlineHotBarMenu::class.java)
        if (onlineHotBarMenuContext != null) {
            onlineHotBarMenuContext.exit()
            return
        }

        onlineHotBarMenuContext = playerContext.enterChild(createOnlineHotBarMenu(menu), key = "HotBarMenu[${sender.name}]")
        sender.sendMessage("Hot bar menu opened.")
    }
}