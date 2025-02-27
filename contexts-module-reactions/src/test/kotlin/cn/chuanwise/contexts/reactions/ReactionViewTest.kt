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
import cn.chuanwise.contexts.reactions.reactive.pane
import cn.chuanwise.contexts.reactions.reactive.view
import org.junit.jupiter.api.Test

@Suppress("TestFunctionName", "MemberVisibilityCanBePrivate")
class ReactionViewTest {
    interface Config {
        var debug: Boolean
        var games: MutableMap<String, Game>
    }

    interface Game {
        var name: String
        var playerNames: MutableList<String>
    }

    data class ConfigImpl(
        override var debug: Boolean,
        override var games: MutableMap<String, Game> = mutableMapOf()
    ) : Config

    data class GameImpl(
        override var name: String,
        override var playerNames: MutableList<String> = mutableListOf()
    ) : Game

    val configDelegate = createMutableReactive<Config>(
        ConfigImpl(
            debug = true,
            games = mutableMapOf(
                "Minecraft" to GameImpl(
                    name = "Minecraft",
                    playerNames = mutableListOf("Chuanwise", "Jacky")
                ),
                "Terraria" to GameImpl(
                    name = "Terraria",
                    playerNames = mutableListOf("Chuanwise", "404E")
                )
            )
        )
    )
    val config by configDelegate

    val contextManager = createContextManager().apply {
        findAndRegisterModules()
    }

    @Test
    fun testFlushGameUI() {
        val root = contextManager.enterRoot(id = "Root")

        val ui = root.view("ConfigUI") {
            println(">>> Config UI Rendering <<<")
            pane("DebugUI") {
                println("Debug status: ${config.debug}")
            }
            pane("GameUI") {
                println("=== GameUI Rendering ===")
                config.games.values.forEach {
                    pane("Game[${it.name}]") {
                        println("   - GAME RENDERING -")
                        println("Game: ${it.name}, player names: ${it.playerNames}")
                    }
                }
            }
        }

        println("--- INITIAL RENDER DONE")

        config.debug = false
        println("!!! DEBUG STATUS CHANGED !!!")

        config.games["3"] = GameImpl(
            name = "Genshin Impact",
            playerNames = mutableListOf("Chuanwise", "404E")
        )
        println("!!! GAME ADDED !!!")

        val playerNames = config.games["Minecraft"]?.playerNames
        println("!!! GET PLAYER NAMES !!!")

        playerNames?.add("404E")
        println("!!! PLAYER ADDED !!!")
    }
}