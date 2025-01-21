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
import cn.chuanwise.contexts.ContextPostEnterEvent
import cn.chuanwise.contexts.ContextPostExitEvent
import cn.chuanwise.contexts.bukkit.Plugin
import cn.chuanwise.contexts.bukkit.player.createPlayerSession
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import kotlin.random.Random

@ContextsInternalApi
object HotBarTestMenuCommand : CommandExecutor {
    class DebugHotBarItem(
        override val itemStack: ItemStack
    ) : HotBarItem {
        @Listener
        fun onFocusOn(event: OnlineHotBarSurfaceFocusStatusChangedEvent) {
            println("Focus on: ${event.focusStatus} for $itemStack")
        }
    }

    class LaserPenHotBarItem(
        override val itemStack: ItemStack
    ) : HotBarItem {
        private object LaserPen {
            fun onHold(player: Player) {
                val direction = player.location.direction
                val blockData = DustOptions(Color.GREEN, 1.0f)
                for (i in 0 until 10) {
                    player.spawnParticle(Particle.REDSTONE, player.location, 1,
                        direction.x * i, direction.y * i, direction.z * i, 0.0, blockData)
                }
            }

            lateinit var task: BukkitTask

            @Listener
            fun ContextPostEnterEvent.onEnter(player: Player) {
                task = Bukkit.getScheduler().runTaskTimerAsynchronously(Plugin, Runnable { onHold(player) }, 0, 3)
            }

            @Listener
            fun ContextPostExitEvent.onExit() {
                task.cancel()
            }
        }

        @Listener
        fun OnlineHotBarSurfaceFocusStatusChangedEvent.onFocusOn(context: Context) {
            if (focusStatus) {
                println("Focus on LASER PEN! $focusStatus for $itemStack")
                context.enterChild(LaserPen)
            } else {
                context.getChildByBean(LaserPen)!!.exit()
                println("Good bye, our laser pen!")
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

        val items = mutableListOf<HotBarItem>()
        for (i in 0 until 9) {
            items.add(DebugHotBarItem(ItemStack(Material.GRASS_BLOCK, i + 1)))
        }
        Random.nextInt(0, 9).let {
            items[it] = LaserPenHotBarItem(ItemStack(Material.DIAMOND_SWORD))
        }

        val playerContext = Plugin.pluginContext.getChildByBean(sender)
            ?: Plugin.pluginContext.enterChild(createPlayerSession(sender), key = "Player[${sender.name}]")

        var onlineHotBarSurfaceContext = playerContext.getChildByBeanClass(OnlineHotBarSurface::class.java)
        if (onlineHotBarSurfaceContext != null) {
            onlineHotBarSurfaceContext.exit()
            return
        }

        onlineHotBarSurfaceContext = playerContext.enterChild(createOnlineHotBarMenu(items), key = "HotBarMenu[${sender.name}]")
        sender.sendMessage("Hot bar menu opened.")
    }
}