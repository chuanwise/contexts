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

package cn.chuanwise.contexts.reactions.model

import cn.chuanwise.contexts.reactions.util.Reactive

/**
 * 标注一个类型是数据模型类型。
 *
 * 当一个 [Reactive] 对象是数据模型类型时，对它的修改会被自动生成代理类型并更细致地比对其差异，
 * 以便最小化更新的范围。
 *
 * 例如，我们有一个配置类型：
 *
 * ```kt
 * @Model
 * data class Configuration(
 *     var debug: Boolean = false,
 *     var checkUpdate: Boolean = true,
 *     var games: Map<String, GameConfiguration> = emptyMap()
 * )
 *
 * data class GameConfiguration(
 *     var name: String = "GameName"
 * )
 * ```
 *
 * 随后我们有一个响应式的 `Configuration` 对象，并在视图函数 `displayGameName` 里使用它：
 *
 * ```kt
 * var configuration by lateInitialReactive<Configuration>()
 *
 * object DebugInfoDisplay {
 *     @Model
 *     fun displayGameName() {
 *         if (configuration.debug) {
 *             sendBroadcast("Now we are debugging!")
 *         }
 *     }
 * }
 * ```
 *
 * 我们实际上只需要在 `configuration.debug` 发生变化时重建 `DebugInfoDisplay`，而不需要每次
 * `configuration` 发生变化时都重建。`@Model` 注解就是为了解决这个问题。如果不使用 `@Model` 注解，
 * 每次 `configuration` 发生变化时都会重建 `DebugInfoDisplay`。
 *
 * @author Chuanwise
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Model
