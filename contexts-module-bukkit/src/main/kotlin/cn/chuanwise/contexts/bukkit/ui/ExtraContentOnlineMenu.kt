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

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.bukkit.Plugin
import cn.chuanwise.contexts.bukkit.player.PlayerContextImpl
import cn.chuanwise.contexts.events.ContextPostEnterEvent
import cn.chuanwise.contexts.events.ContextPreExitEvent
import cn.chuanwise.contexts.events.annotations.Event
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.filters.annotations.Filter
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

interface ExtraContentOnlineMenu {
    val buttons: List<ItemStackButton>
}

@OptIn(ContextsInternalApi::class)
object OpenMenuCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only player can execute this command!")
            return true
        }

        val context = Plugin.pluginContext.getChild(sender)
            ?: Plugin.pluginContext.enterChild(PlayerContextImpl(sender), key = sender.name)

        context.enterChild(ExtraContentOnlineMenuImpl(listOf(
            ItemStackButtonImpl("GOLD", ItemStack(Material.GOLD_BLOCK)),
            ItemStackButtonImpl("DIAMOND", ItemStack(Material.DIAMOND_BLOCK))
        )), key = "Menu")

        sender.sendMessage("You opened the menu!")
        return true
    }
}

@ContextsInternalApi
class ExtraContentOnlineMenuImpl(
    override val buttons: List<ItemStackButton>,
    private val menuLength: Int = 9
) : ExtraContentOnlineMenu {
    private lateinit var contentBackups: List<ItemStack>

    @Listener
    fun onContextPostEnter(@Event event: ContextPostEnterEvent, player: Player) {
        contentBackups = player.inventory.contents.copyOfRange(0, menuLength).mapNotNull { it?.clone() }

        for (button in buttons) {
            event.context.enterChild(button, key = "Button ${button.itemStack.type}")
        }

        for (i in 0 until menuLength) {
            player.inventory.setItem(i, buttons.getOrNull(i)?.itemStack ?: ItemStack(Material.AIR))
        }
    }

    @Listener(eventClass = ContextPreExitEvent::class)
    fun onContextPreExit(context: Context, player: Player) {
        for (i in 0 until menuLength) {
            player.inventory.setItem(i, contentBackups.getOrNull(i) ?: ItemStack(Material.AIR))
        }
    }

    @EventHandler
    fun PlayerDropItemEvent.onPlayerDrop() {
        isCancelled = true
    }
}

class ItemStackButtonImpl(
    private val name: String,
    override val itemStack: ItemStack
) : ItemStackButton {
    @Listener
    fun onEnter(event: ContextPostEnterEvent) {
        println("Button $name context entered!")
    }

    @Filter
    fun filterItemStackInHand(event: PlayerInteractEvent) : Boolean {
        return event.player.inventory.itemInMainHand == itemStack
    }

    @EventHandler
    @Listener(intercept = true)
    @OptIn(ContextsInternalApi::class)
    fun PlayerInteractEvent.onClick(context: Context) {
        println("Player ${player.name} clicking button $name")
        when (itemStack.type) {
            Material.GOLD_BLOCK -> {
                player.sendMessage("$name: You clicked the gold block, so your menu closed!")
                context.parent.exit()
            }
            Material.DIAMOND_BLOCK -> {
                player.sendMessage("$name: You clicked the diamond block, so you open DEEPER menu!")
                context.enterChild(ExtraContentOnlineMenuImpl(listOf(
                    ItemStackButtonImpl("$name -> GOLD", ItemStack(Material.GOLD_BLOCK)),
                    ItemStackButtonImpl("$name -> DIAMOND", ItemStack(Material.DIAMOND_BLOCK))
                )))
            }
            else -> {
                player.sendMessage("$name: You clicked the ${itemStack.type}, so nothing happened!")
            }
        }
    }

    @EventHandler
    fun PlayerDeathEvent.onDeath() {
        println("Player ${entity.name} DEATH (FROM button $name)")
    }
}