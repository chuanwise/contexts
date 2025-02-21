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
import cn.chuanwise.contexts.reactions.model.Model
import cn.chuanwise.contexts.reactions.util.createMutableReactive
import cn.chuanwise.contexts.reactions.util.getValue
import cn.chuanwise.contexts.reactions.util.setValue
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

    data class GameConfiguration(
        val name: String
    )

    @Model
    interface Configuration {
        var checkUpdate: Boolean
        val games: MutableMap<String, GameConfiguration>
    }

    class ConfigurationImpl : Configuration {
        override var checkUpdate by createMutableReactive(true)
        override val games by createMutableReactive(
            mutableMapOf(
                "1" to GameConfiguration("404E")
            )
        )
    }

    private var configuration by createMutableReactive<Configuration>(ConfigurationImpl())

    private inner class CheckUpdateMonitor {
        @View
        fun displayConfigCheckUpdate() {
            println("A : Check update: ${configuration.checkUpdate}")
        }
    }

    private inner class GameOneMonitor {
        @View
        fun displayGameOne() {
            val games = configuration.games
            println("B : Game 1: ${games["1"]!!.name}")
        }
    }

    @Test
    fun testComplexValue() {
        val checkUpdateMonitor = CheckUpdateMonitor()
        val gameOneMonitor = GameOneMonitor()

        contextManager.enterRoot(checkUpdateMonitor)
        contextManager.enterRoot(gameOneMonitor)

        println("Contexts entered!")

        configuration.checkUpdate = false
        println("Check update changed to false!")

        configuration.games["1"] = GameConfiguration("Chuanwise")
        println("Game 1 name changed to Chuanwise!")
    }
}