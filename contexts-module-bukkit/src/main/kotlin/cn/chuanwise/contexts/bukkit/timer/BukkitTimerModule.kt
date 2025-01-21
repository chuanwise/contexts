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

package cn.chuanwise.contexts.bukkit.timer

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextPreEnterEvent
import cn.chuanwise.contexts.ContextPreExitEvent
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.getBeanValueOrFail
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Consumer

interface BukkitTimerModule : Module {
    val plugin: Plugin
}

@ContextsInternalApi
class BukkitTimerModuleImpl @JvmOverloads constructor(
    private var mutablePlugin: Plugin? = null
) : BukkitTimerModule {
    override val plugin: Plugin get() = mutablePlugin ?: error("Bukkit timer module has not been initialized yet.")

    private class BukkitTaskActionImpl(
        val action: Consumer<BukkitTask>
    ) : Runnable {
        val bukkitTaskFuture = CompletableFuture<BukkitTask>()

        override fun run() {
            action.accept(bukkitTaskFuture.get())
        }
    }

    private inner class BukkitTimerManagerImpl(
        override val context: Context
    ) : BukkitTimerManager {
        private val bukkitTasks = ConcurrentLinkedDeque<BukkitTask>()

        override fun runTaskTimer(delay: Long, period: Long, action: Consumer<BukkitTask>): BukkitTask {
            val finalAction = BukkitTaskActionImpl(action)
            val bukkitTask = plugin.server.scheduler.runTaskTimer(plugin, finalAction, delay, period)
            finalAction.bukkitTaskFuture.complete(bukkitTask)
            bukkitTasks.add(bukkitTask)
            return bukkitTask
        }

        override fun runTaskTimerAsynchronously(delay: Long, period: Long, action: Consumer<BukkitTask>): BukkitTask {
            val finalAction = BukkitTaskActionImpl(action)
            val bukkitTask = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, finalAction, delay, period)
            finalAction.bukkitTaskFuture.complete(bukkitTask)
            bukkitTasks.add(bukkitTask)
            return bukkitTask
        }

        fun cancelTasks() {
            val iterator = bukkitTasks.iterator()
            while (iterator.hasNext()) {
                iterator.next().cancel()
                iterator.remove()
            }
        }
    }

    override fun onModulePostEnable(event: ModulePostEnableEvent) {
        mutablePlugin = event.contextManager.getBeanValueOrFail()
    }

    override fun onContextPreExit(event: ContextPreExitEvent) {
        val bukkitTimerManager = event.context.bukkitTimerManagerOrNull as? BukkitTimerManagerImpl ?: return
        bukkitTimerManager.cancelTasks()
    }

    override fun onContextPreEnter(event: ContextPreEnterEvent) {
        val bukkitTimerManager = BukkitTimerManagerImpl(event.context)
        event.context.registerBean(bukkitTimerManager)
    }
}