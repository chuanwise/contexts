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

import cn.chuanwise.contexts.createContextManager
import cn.chuanwise.contexts.events.ContextEventContextModule
import cn.chuanwise.contexts.events.EventsContextModule
import cn.chuanwise.contexts.events.annotations.Event
import cn.chuanwise.contexts.events.annotations.EventsAnnotationsContextModule
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.events.annotations.listenerManager
import cn.chuanwise.contexts.events.eventPublisher
import cn.chuanwise.contexts.filters.FiltersContextModule
import cn.chuanwise.contexts.filters.annotations.Filter
import cn.chuanwise.contexts.filters.annotations.FiltersAnnotationsContextModule
import org.junit.jupiter.api.Test

class ContextManagerTest {
    interface PlayerEvent {
        val playerName: String
    }

    data class PlayerJoinEvent(override val playerName: String) : PlayerEvent
    data class PlayerJumpEvent(override val playerName: String) : PlayerEvent

    private object GlobalContext {
        @Filter
        fun filterPlayerEvent(event: PlayerEvent) : Boolean? {
            println("Global context filtering: $event")
            return null
        }
    }
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
    private object JumpToolContext {
        @Listener(intercept = true)
        fun onPlayerJump(@Event event: PlayerJumpEvent, playerContext: PlayerContext) {
            println("Player ${event.playerName} jumped! FROM JumpTool's context (${playerContext.playerName})")
        }
    }

    @Test
    fun testContextManager() {
        val contextManager = createContextManager().apply {
            registerModule(EventsContextModule())
            registerModule(EventsAnnotationsContextModule())

            registerModule(ContextEventContextModule())

            registerModule(FiltersContextModule())
            registerModule(FiltersAnnotationsContextModule())
        }

        val globalContext = contextManager.enter(GlobalContext, key = "Global")

        val chuanwiseContext = globalContext.enterChild(PlayerContext("Chuanwise"), key = "Chuanwise")!!
        val fourZeroFourEContext = globalContext.enterChild(PlayerContext("404E"), key = "404E")!!
        fourZeroFourEContext.enterChild(JumpToolContext)

        val listenerManager = chuanwiseContext.listenerManager
        globalContext.eventPublisher.publishToAllChildrenAndContext(PlayerJoinEvent("Chuanwise"))
        globalContext.eventPublisher.publishToAllChildrenAndContext(PlayerJumpEvent("404E"))
        globalContext.eventPublisher.publishToAllChildrenAndContext(PlayerJumpEvent("Chuanwise"))
    }
}