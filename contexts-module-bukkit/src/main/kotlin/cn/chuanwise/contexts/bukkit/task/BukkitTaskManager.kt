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
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer

interface BukkitTaskManager {
    val context : Context

    fun runTaskTimer(
        delay: Long = DEFAULT_DELAY_TICKS,
        period: Long = DEFAULT_PERIOD_TICKS,
        action: Consumer<BukkitTask>
    ): BukkitTask

    fun runTaskTimerAsynchronously(
        delay: Long = DEFAULT_DELAY_TICKS,
        period: Long = DEFAULT_PERIOD_TICKS,
        action: Consumer<BukkitTask>
    ): BukkitTask

    fun runTaskLater(
        delay: Long = DEFAULT_DELAY_TICKS,
        action: Consumer<BukkitTask>
    ): BukkitTask

    fun cancelTaskOnExit(task: BukkitTask)
}