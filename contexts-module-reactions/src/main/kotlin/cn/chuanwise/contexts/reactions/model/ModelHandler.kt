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

import cn.chuanwise.contexts.context.Context

/**
 * 数据模型处理器，主要用于数据刷新时，根据刷新策略决定是否重构状态。
 *
 * 例如，在进入一个像下面这样有 `@View` 注解且开启自动绑定的方法的上下文时：
 *
 * ```kt
 * var configuration by createMutableReactive(Configuration())
 *
 * object Foo {
 *     @View
 *     fun bar(context: Context) {
 *         val id: String = configuration.id
 *         val games: Map<String, GameConfiguration> = configuration.games
 *
 *         val game: GameConfiguration = games[id]
 *
 *         context.enterChild(buildGame(id, game))
 *     }
 *
 *     fun buildGame(id: Int, game: GameConfiguration): String {
 *         return "Game[$id]: ${game.title}"
 *     }
 * }
 * ```
 *
 * 框架会拦截 `configuration` 的读取请求并调用 `Configuration` 类型对应的
 * [ModelHandler] 的 [toProxy] 以创建一个可以检测 `bar` 函数使用了哪些
 * 配置项的代理字段，代理对象应当自行根据对象的特点维护这些信息。
 *
 * 在上面的例子中，`bar` 函数使用了 `id` 和 `games` 字段，后者是一个哈希表，且
 * 只使用了里面对应于 `id` 的值的 `title` 属性。因此，在 `configuration` 发生
 * 变化时，框架调用 [tryFlush] 方法，传入旧的代理对象和新的代理对象，以
 * 标记那些需要重构状态的上下文。
 *
 * 特别地，由于 `games` 是一个哈希表，可以构造一个新的代理对象并用它判断是否需要更新。
 *
 * @param T 数据模型类型
 * @author Chuanwise
 */
interface ModelHandler<T> {
    /**
     * 创建一个代理对象。
     *
     * 例如，可以为实际为 [ArrayList] 的对象创建一个 [List] 类型的代理对象。
     *
     * @param context 上下文
     * @param expectClass 期望的数据类型
     * @param data 数据对象
     * @return 代理对象
     */
    fun toProxy(context: Context, expectClass: Class<in T>, data: T): T

    /**
     * 将代理对象转换为数据对象。
     *
     * @param context 上下文
     * @param proxy 代理对象
     * @return 数据对象
     */
    fun toData(context: Context, proxy: T): T

    /**
     * 尝试刷新上下文。
     *
     * @param context 上下文
     * @param oldProxy 旧的代理对象
     * @param newValue 新的代理对象
     */
    fun tryFlush(context: Context, oldProxy: T, newValue: T) : Boolean
}