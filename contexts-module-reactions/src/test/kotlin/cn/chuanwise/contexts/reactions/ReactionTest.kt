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

package cn.chuanwise.contexts.reactions

import cn.chuanwise.contexts.context.createContextManager
import cn.chuanwise.contexts.context.findAndRegisterModules
import cn.chuanwise.contexts.reactions.reactive.createMutableReactive
import cn.chuanwise.contexts.reactions.reactive.getValue
import cn.chuanwise.contexts.reactions.reactive.setValue
import cn.chuanwise.contexts.reactions.view.View
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReactionTest {
    private var debug by createMutableReactive(false)

    private inner class DebugMonitor {
        var count = 0

        @View
        fun onDebugChanged() {
            println("MONITOR: Debug status: $debug")
            count++
        }
    }

    private val contextManager = createContextManager().apply { findAndRegisterModules() }

    @Test
    fun testSimpleValue() {
        val monitor = DebugMonitor()
        contextManager.enterRoot(monitor)

        println("Root context entered!")
        assertEquals(1, monitor.count)

        debug = false
        println("Debug status reset to false!")
        assertEquals(1, monitor.count)

        debug = true
        println("Debug status changed to true")
        assertEquals(2, monitor.count)
    }

    data class Game(
        val name: String
    )

    interface Config {
        var checkUpdate: Boolean
        val games: MutableMap<String, Game>
    }

    class ConfigImpl : Config {
        override var checkUpdate = true
        override val games = mutableMapOf(
            "1" to Game("404E")
        )
    }

    private var config by createMutableReactive<Config>(ConfigImpl())

    private inner class CheckUpdateMonitor {
        @View
        fun displayConfigCheckUpdate() {
            println("A : Check update: ${config.checkUpdate}")
        }
    }

    private inner class GameOneMonitor {
        @View
        fun displayGameOne() {
            val games = config.games
            val first: Any = games["1"]!!
            println("B : Game 1: $first")
        }
    }

    @Test
    fun testComplexValue() {
        val checkUpdateMonitor = CheckUpdateMonitor()
        val gameOneMonitor = GameOneMonitor()

        contextManager.enterRoot(checkUpdateMonitor, id = "CheckUpdate")
        contextManager.enterRoot(gameOneMonitor, id = "GameOne")

        println("---- Contexts entered! ----")

        val oldValue = config.checkUpdate
        println("BEFORE: configuration.checkUpdate = $oldValue")

        println("BEFORE SET Check update changed to ${!oldValue}!")
        config.checkUpdate = !oldValue
        println("AFTER SET Check update changed to ${!oldValue}!")

        config.games["1"] = Game("Chuanwise")
        println("Game 1 name changed to Chuanwise!")

        config.games["2"] = Game("404E")
        println("Game 2 name changed to 404E!")
    }

    @Test
    fun testApis() {
        val config = createMutableReactive(false)
        config.addWriteObserver { reactive, value ->
            println("Value in $reactive changed to $value")
        }
    }
}