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

package cn.chuanwise.contexts.bukkit

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.context.ContextManager
import cn.chuanwise.contexts.annotation.createAnnotationModule
import cn.chuanwise.contexts.bukkit.event.createBukkitEventModule
import cn.chuanwise.contexts.bukkit.task.createBukkitTimerAnnotationModule
import cn.chuanwise.contexts.bukkit.task.createBukkitTimerModule
import cn.chuanwise.contexts.bukkit.ui.HotBarTestMenuCommand
import cn.chuanwise.contexts.context.createContextManager
import cn.chuanwise.contexts.context.registerModules
import cn.chuanwise.contexts.events.annotations.createEventAnnotationModule
import cn.chuanwise.contexts.events.createContextEventModule
import cn.chuanwise.contexts.events.createEventModule
import cn.chuanwise.contexts.filters.annotations.createFilterAnnotationModule
import cn.chuanwise.contexts.filters.createFilterModule
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.addBean
import cn.chuanwise.contexts.util.createJavaLogger
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class ContextsPlugin : JavaPlugin() {
    lateinit var contextManager: ContextManager
    lateinit var pluginContext: Context

    @OptIn(ContextsInternalApi::class)
    override fun onEnable() {
        val logger = createJavaLogger(logger)
        logger.logger.level = Level.FINE

        contextManager = createContextManager(logger).apply {
            addBean(this@ContextsPlugin)
            registerModules(
                createAnnotationModule(),

                createFilterModule(),
                createFilterAnnotationModule(),

                createEventModule(),
                createEventAnnotationModule(),

                createContextEventModule(),

                createBukkitEventModule(),

                createBukkitTimerModule(),
                createBukkitTimerAnnotationModule()
            )
        }
        pluginContext = contextManager.enterRoot(this, id = "Plugin")

        getCommand("contexts")!!.setExecutor(HotBarTestMenuCommand)
    }
}

val Plugin: ContextsPlugin = JavaPlugin.getPlugin(ContextsPlugin::class.java)
