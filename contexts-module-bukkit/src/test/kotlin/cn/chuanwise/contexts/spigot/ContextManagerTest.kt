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

package cn.chuanwise.contexts.spigot

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextPostExitEvent
import cn.chuanwise.contexts.ContextPostRemoveEvent
import cn.chuanwise.contexts.ContextPreExitEvent
import cn.chuanwise.contexts.checkAllRegisteredModuleEnabled
import cn.chuanwise.contexts.createContextManager
import cn.chuanwise.contexts.events.annotations.Event
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.events.annotations.createEventAnnotationsModule
import cn.chuanwise.contexts.events.annotations.listenerManager
import cn.chuanwise.contexts.events.createContextEventModule
import cn.chuanwise.contexts.events.createEventModule
import cn.chuanwise.contexts.events.eventPublisher
import cn.chuanwise.contexts.filters.annotations.Filter
import cn.chuanwise.contexts.filters.annotations.createFiltersAnnotationsModule
import cn.chuanwise.contexts.filters.createFilterModule
import cn.chuanwise.contexts.findAndRegisterModules
import cn.chuanwise.contexts.util.ConsoleLoggerImpl
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.Joint
import org.junit.jupiter.api.Test

@OptIn(ContextsInternalApi::class)
class ContextManagerTest {
    interface PlayerEvent {
        val playerName: String
    }

    data class PlayerJoinEvent(override val playerName: String) : PlayerEvent
    data class PlayerJumpEvent(override val playerName: String) : PlayerEvent

    private object ExitLoggerContext {
        @Listener
        fun ContextPreExitEvent.onPreExit(context: Context) {
            println("Pre Exit: $context")
        }

        @Listener
        fun ContextPostExitEvent.onPostExit(context: Context) {
            println("Post Exit: $context")
        }
    }

    @Joint(ExitLoggerContext::class)
    private object GlobalContext {
        @Filter
        fun PlayerEvent.filterPlayerEvent() : Boolean? {
            println("Global context filtering: $this")
            return null
        }
    }

    @Joint(ExitLoggerContext::class)
    private class PlayerContext(val playerName: String) {
        @Filter
        fun filterPlayerEvent(event: PlayerEvent) : Boolean {
            return (playerName == event.playerName).apply {
                println("Player $playerName filtering: $event: $this FROM $playerName's context")
            }
        }

        @Listener
        fun onPlayerJoin(event: PlayerJoinEvent) {
            println("Player ${event.playerName} joined! FROM $playerName's context")
        }
    }

    @Joint(ExitLoggerContext::class)
    private object JumpToolContext {
        @Listener(intercept = true)
        fun onPlayerJump(@Event event: PlayerJumpEvent, playerContext: PlayerContext) {
            println("Player ${event.playerName} jumped! FROM JumpTool's context (${playerContext.playerName})")
        }
    }

    @Test
    fun testContextManager() {
        val logger = ConsoleLoggerImpl()
        val contextManager = createContextManager(logger).apply {
            findAndRegisterModules()

//            registerModule(createAnnotationModule())            // 启动注解扫描管理。
//
//            registerModule(createFilterModule())                // 启动过滤器机制。
//            registerModule(createFiltersAnnotationsModule())    // 把 @Filter 注解的函数注册为过滤器。
//
//            registerModule(createEventModule())                 // 启动事件机制。
//            registerModule(createEventAnnotationsModule())      // 把 @Listener 注解的函数注册为事件监听器。
//
//            registerModule(createContextEventModule())          // 启动上下文生命周期事件。
        }

        val globalContext = contextManager.enterRoot(GlobalContext, key = "Global")

        val chuanwiseContext = globalContext.enterChild(PlayerContext("Chuanwise"), key = "Chuanwise")
        val fourZeroFourEContext = globalContext.enterChild(PlayerContext("404E"), key = "404E")
        fourZeroFourEContext.enterChild(JumpToolContext, key = "Jump")

        val listenerManager = chuanwiseContext.listenerManager
        globalContext.eventPublisher.publish(PlayerJoinEvent("Chuanwise"))

        globalContext.eventPublisher.publish(PlayerJumpEvent("404E"))
        globalContext.eventPublisher.publish(PlayerJumpEvent("Chuanwise"))

        contextManager.close()
    }
}