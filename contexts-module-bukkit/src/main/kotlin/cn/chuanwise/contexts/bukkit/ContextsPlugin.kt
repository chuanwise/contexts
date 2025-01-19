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

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.annotations.createAnnotationModule
import cn.chuanwise.contexts.bukkit.event.BukkitEventModule
import cn.chuanwise.contexts.bukkit.event.createBukkitEventModule
import cn.chuanwise.contexts.bukkit.ui.OpenMenuCommand
import cn.chuanwise.contexts.createContextManager
import cn.chuanwise.contexts.events.ContextEventModule
import cn.chuanwise.contexts.events.EventModule
import cn.chuanwise.contexts.events.annotations.EventAnnotationsModule
import cn.chuanwise.contexts.events.annotations.createEventAnnotationsModule
import cn.chuanwise.contexts.events.createContextEventModule
import cn.chuanwise.contexts.events.createEventModule
import cn.chuanwise.contexts.filters.FilterModule
import cn.chuanwise.contexts.filters.annotations.FiltersAnnotationsModuleImpl
import cn.chuanwise.contexts.filters.annotations.createFiltersAnnotationsModule
import cn.chuanwise.contexts.filters.createFilterModule
import cn.chuanwise.contexts.util.ConsoleLoggerImpl
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.plugin.java.JavaPlugin

class ContextsPlugin : JavaPlugin() {
    @OptIn(ContextsInternalApi::class)
    val contextManager = createContextManager(ConsoleLoggerImpl()).apply {
        registerModule(createAnnotationModule())

        registerModule(createEventModule())
        registerModule(createEventAnnotationsModule())

        registerModule(createContextEventModule())

        registerModule(createFilterModule())
        registerModule(createFiltersAnnotationsModule())

        registerModule(createBukkitEventModule())

        registerBean(this@ContextsPlugin)
    }

    lateinit var pluginContext: Context

    override fun onEnable() {
        pluginContext = contextManager.enter(this, key = "Global")

        getCommand("contexts")!!.setExecutor(OpenMenuCommand)
    }
}

val Plugin: ContextsPlugin = JavaPlugin.getPlugin(ContextsPlugin::class.java)
