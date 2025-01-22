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

package cn.chuanwise.contexts.commands

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextManager
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.util.ContextsInternalApi

interface CommandModule : Module {
    /**
     * 所有指令的父上下文。
     */
    val commandContext: Context

    /**
     * 代表一个指令发送者的上下文。
     *
     * @param sender 任意对象
     * @return 代表一个指令发送者的上下文
     */
    fun <T> getCommandSenderContext(sender: T): Context
}

@ContextsInternalApi
abstract class CommandModuleImpl : CommandModule {
    private lateinit var mutableCommandContext: Context
    override val commandContext: Context get() = mutableCommandContext
}