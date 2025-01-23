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

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.bukkit.Plugin
import cn.chuanwise.contexts.bukkit.player.createPlayerSession
import cn.chuanwise.contexts.bukkit.task.Timer
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

@ContextsInternalApi
object HotBarTestMenuCommand : CommandExecutor {
    fun generateButtons(): List<HotBarItem> {
        val items = mutableListOf<HotBarItem>()
        val material = Material.values().random()
        for (i in 0 until 9) {
            items.add(createFocusingHotBarItem(ItemStack(material, i + 1)) { it.enterChild(DeepHotBarItem) })
        }
        Random.nextInt(0, 9).let {
            items[it] = createFocusingHotBarItem(ItemStack(Material.DIAMOND_SWORD)) {
                parent -> parent.enterChild(LaserPen)
            }
        }
        Random.nextInt(0, 9).let {
            items[it] = QuitHotBarItem(ItemStack(Material.BARRIER))
        }
        return items
    }

    class QuitHotBarItem(
        override val itemStack: ItemStack = ItemStack(Material.BARRIER)
    ) : HotBarItem {
        @Listener(intercept = true)
        @EventHandler(ignoreCancelled = true)
        fun PlayerInteractEvent.onInteract(context: Context) {
            println("QUIT clicked, goodbye!")
            context.getParentByBeanClass(OnlineHotBarSurface::class.java)!!.exit()
        }
    }

    object DeepHotBarItem {
        @Listener(intercept = true)
        @EventHandler(ignoreCancelled = true)
        fun PlayerInteractEvent.onInteract(context: Context) {
            context.enterChild(createOnlineHotBarMenu(generateButtons()))
        }
    }

    object LaserPen {
        @Timer
        fun onSpawnParticles(player: Player) {
            val direction = player.location.direction
            val data = DustOptions(Color.WHITE, 1.0f)
            val eyeLocation = player.eyeLocation.clone()

            for (i in 0 until 30) {
                player.world.spawnParticle(
                    Particle.REDSTONE, eyeLocation.clone().add(direction.multiply(i.toDouble())),
                    1, 0.0, 0.0, 0.0, 0.0, data
                )
            }
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

        val playerContext = Plugin.pluginContext.getChildByBean(sender)
            ?: Plugin.pluginContext.enterChild(createPlayerSession(sender), key = "Player[${sender.name}]")

        var onlineHotBarSurfaceContext = playerContext.getChildByBeanClass(OnlineHotBarSurface::class.java)
        if (onlineHotBarSurfaceContext != null) {
            onlineHotBarSurfaceContext.exit()
            return
        }

        onlineHotBarSurfaceContext = playerContext.enterChild(createOnlineHotBarMenu(
            generateButtons()
        ), key = "HotBarMenu[${sender.name}]")
        sender.sendMessage("Hot bar menu opened.")
    }
}