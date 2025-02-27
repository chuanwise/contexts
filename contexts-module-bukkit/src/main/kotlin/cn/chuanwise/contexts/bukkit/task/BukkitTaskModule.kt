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

package cn.chuanwise.contexts.bukkit.task

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.context.ContextInitEvent
import cn.chuanwise.contexts.context.ContextPreEnterEvent
import cn.chuanwise.contexts.context.ContextPreExitEvent
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.addBeanByCompilationType
import cn.chuanwise.contexts.util.getBeanOrFail
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Consumer

interface BukkitTaskModule : Module {
    val plugin: Plugin
}

@ContextsInternalApi
class BukkitTaskModuleImpl @JvmOverloads constructor(
    private var mutablePlugin: Plugin? = null
) : BukkitTaskModule {
    override val plugin: Plugin get() = mutablePlugin ?: error("Bukkit timer module has not been initialized yet.")

    private class BukkitTaskActionImpl(
        val action: Consumer<BukkitTask>
    ) : Runnable {
        val bukkitTaskFuture = CompletableFuture<BukkitTask>()

        override fun run() {
            action.accept(bukkitTaskFuture.get())
        }
    }

    private inner class BukkitTaskManagerImpl(
        override val context: Context
    ) : BukkitTaskManager {
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

        override fun runTaskLater(delay: Long, action: Consumer<BukkitTask>): BukkitTask {
            val finalAction = BukkitTaskActionImpl(action)
            val bukkitTask = plugin.server.scheduler.runTaskLater(plugin, finalAction, delay)
            finalAction.bukkitTaskFuture.complete(bukkitTask)
            bukkitTasks.add(bukkitTask)
            return bukkitTask
        }

        override fun cancelTaskOnExit(task: BukkitTask) {
            bukkitTasks.add(task)
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
        mutablePlugin = event.contextManager.getBeanOrFail()
    }

    override fun onContextPreExit(event: ContextPreExitEvent) {
        val bukkitTimerManager = event.context.bukkitTaskManagerOrNull as? BukkitTaskManagerImpl ?: return
        bukkitTimerManager.cancelTasks()
    }

    override fun onContextInit(event: ContextInitEvent) {
        val bukkitTimerManager = BukkitTaskManagerImpl(event.context)
        event.context.addBeanByCompilationType(bukkitTimerManager)
    }
}