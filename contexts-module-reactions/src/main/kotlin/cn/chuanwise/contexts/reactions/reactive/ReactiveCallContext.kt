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

package cn.chuanwise.contexts.reactions.reactive

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.reactions.ReactionManager
import cn.chuanwise.contexts.util.ContextsInternalApi
import java.lang.reflect.Method

/**
 * 响应式值函数调用上下文。
 *
 * 注意，此函数调用不一定是对响应式值的直接函数调用，也可能包含间接调用。
 *
 * 例如，对于下面的响应式值：
 *
 * ```kt
 * interface Foo {
 *     fun bar(): Bar
 * }
 *
 * interface Bar {
 *     fun baz(): Baz
 * }
 *
 * var foo by createMutableReactive<Foo>(/* initialize */)
 * ```
 *
 * 当调用 `foo.bar().baz()` 时，`bar` 函数的调用上下文中的 `source` 将会是 `foo` 的调用上下文。
 *
 * @param T 响应式值的类型
 * @author Chuanwise
 */
interface ReactiveCallContext<T> {
    val reactive: Reactive<T>

    /**
     * 如果该函数调用是为了构建某个视图产生的，则该值为视图上下文。
     */
    val context: Context?

    /**
     * 如果该函数调用是为了构建某个视图产生的，则该值为视图上下文管理器。
     */
    val reactionManager: ReactionManager?

    val raw: Any
    val rawProxy: Any

    val method: Method
    val arguments: Array<Any?>

    val result: Any?
    val resultProxy: Any?

    /**
     * 本次函数调用的主体从哪次函数调用的返回值中来。
     */
    val sourceResult: ReactiveCallContext<T>?

    /**
     * 本次函数调用的主体从哪次函数调用后的原始值中来。
     */
    val sourceRaw: ReactiveCallContext<T>?
}

@ContextsInternalApi
class ReactiveCallContextImpl<T>(
    override val reactive: Reactive<T>,
    override val context: Context?,
    override val reactionManager: ReactionManager?,
    override val rawProxy: Any,
    override val raw: Any,
    override val method: Method,
    override val arguments: Array<Any?>,
    override val result: Any?,
    override val sourceResult: ReactiveCallContext<T>?,
    override val sourceRaw: ReactiveCallContext<T>?
) : ReactiveCallContext<T> {
    override var resultProxy: Any? = null
}