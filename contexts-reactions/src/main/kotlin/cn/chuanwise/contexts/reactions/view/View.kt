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

package cn.chuanwise.contexts.reactions.view

import cn.chuanwise.contexts.context.ContextPostEnterEvent
import cn.chuanwise.contexts.reactions.DEFAULT_AUTO_BIND
import cn.chuanwise.contexts.reactions.reactive.Reactive

/**
 * 标注一个上下文函数为视图函数。
 *
 * 视图函数是一种特殊的 [ContextPostEnterEvent] 的监听器，它会在上下文进入后立即执行。
 * 执行时可以绑定到 [Reactive] 对象，当 [Reactive] 对象的值发生变化时，将会重新执行视图函数。
 *
 * 例如，下面的代码在进入上下文后显示 `debug` 配置项的值：
 *
 * ```kt
 * var debug by reactive(false)
 *
 * class DebugMonitor {
 *     @View
 *     fun displayDebugInfo() {
 *         println("Debug status: $debug")
 *     }
 * }
 * ```
 *
 * 实际构造的上下文图如下所示：
 *
 * ```
 *    +-------------+
 *    |  DebugInfo  |
 *    +------+------+
 *           |
 *  +--------+--------+
 *  | DebugInfo  View |
 *  +-----------------+
 * ```
 *
 * 当 `debug` 的值发生变化时，`DebugInfo View` 上下文将会被退出，并重新进入，随后执行 `displayDebugInfo` 函数。
 *
 * 当自动绑定 [autoBind] 启动时，将会在函数执行时自动检测使用到的 [Reactive] 对象，
 * 并自动将当前上下文与之绑定到一起。我们无需显式绑定 `debug` 对象，因为我们在函数中使用了它。
 *
 * 标注了该注解的函数可以使用 [ViewContext] 参数，并手动通过内部的 API 来绑定依赖关系。
 *
 * @author Chuanwise
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class View(
    val autoBind: Boolean = DEFAULT_AUTO_BIND
)
