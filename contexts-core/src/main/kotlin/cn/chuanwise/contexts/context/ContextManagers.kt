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

@file:JvmName("ContextManagers")
package cn.chuanwise.contexts.context

import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModuleEntry
import java.util.ServiceLoader

fun List<ModuleEntry>.checkAllEnabled() {
    val notEnabledEntries = filterNot { it.isEnabled }
    check(notEnabledEntries.isEmpty()) {
        "Not all modules enabled, not yet enabled modules: $notEnabledEntries"
    }
}

@JvmOverloads
fun ContextManager.registerModules(
    modules: Iterable<Module>,
    checkAllEnabled: Boolean = true
) : List<ModuleEntry> {
    return modules.mapNotNull {
        try {
            registerModule(it)
        } catch (e: Throwable) {
            logger.error(e) { "Failed to register module: $it." }
            null
        }
    }.apply {
        if (checkAllEnabled) {
            checkAllEnabled()
        }
    }
}

@JvmOverloads
fun ContextManager.registerModules(
    vararg modules: Module,
    checkAllEnabled: Boolean = true
) : List<ModuleEntry> {
    return registerModules(modules.asIterable(), checkAllEnabled)
}

@JvmOverloads
fun ContextManager.findAndRegisterModules(
    checkAllEnabled: Boolean = true
) : List<ModuleEntry> {
    return registerModules(ServiceLoader.load(Module::class.java), checkAllEnabled)
}