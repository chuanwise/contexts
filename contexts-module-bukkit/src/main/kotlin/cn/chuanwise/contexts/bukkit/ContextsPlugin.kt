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
import cn.chuanwise.contexts.ContextManager
import cn.chuanwise.contexts.bukkit.ui.HotBarTestMenuCommand
import cn.chuanwise.contexts.createContextManager
import cn.chuanwise.contexts.findAndRegisterModules
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.createJavaLogger
import org.bukkit.plugin.java.JavaPlugin

class ContextsPlugin : JavaPlugin() {
    lateinit var contextManager: ContextManager
    lateinit var pluginContext: Context

    @OptIn(ContextsInternalApi::class)
    override fun onEnable() {
        contextManager = createContextManager(createJavaLogger(logger)).apply {
            findAndRegisterModules()
            registerBean(this@ContextsPlugin)
        }
        pluginContext = contextManager.enterRoot(this, key = "Plugin")

        getCommand("contexts")!!.setExecutor(HotBarTestMenuCommand)
    }
}

val Plugin: ContextsPlugin = JavaPlugin.getPlugin(ContextsPlugin::class.java)
