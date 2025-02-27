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

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.reactions.reactive.Reactive
import cn.chuanwise.contexts.reactions.view.ViewContext
import cn.chuanwise.contexts.reactions.view.ViewFunction
import cn.chuanwise.contexts.util.MutableEntry

/**
 * 交互式响应上下文。
 *
 * @author Chuanwise
 */
interface ReactionManager {
    /**
     * 对应的上下文。
     */
    val context: Context

    /**
     * 响应上下文。
     *
     * 若为 `null` 表示上下文尚未进入，或非响应式上下文。
     */
    val viewContext: ViewContext?

    /**
     * 自动刷新，若开启，一旦响应式变量的值发生变化，将会自动刷新当前上下文。
     */
    var autoFlush: Boolean

    /**
     * 是否正在刷新。
     */
    val isFlushing: Boolean

    /**
     * 注册一个视图函数。
     *
     * @param autoBind 是否需要自动绑定使用的交互式变量
     * @param function 视图函数
     * @return 视图函数的可变条目
     */
    fun registerViewFunction(autoBind: Boolean, function: ViewFunction) : MutableEntry<ViewFunction>

    /**
     * 在响应式变量的值发生变化时，尝试刷新当前上下文。
     *
     * @param T 响应式变量的类型
     * @param reactive 响应式变量
     * @param value 值
     * @return 是否执行了一次刷新
     * @throws IllegalArgumentException 该上下文尚未使用该响应式变量
     */
    fun <T> tryFlush(reactive: Reactive<T>, value: T) : Boolean

    /**
     * 如果当前上下文里有视图函数，则刷新当前上下文。
     *
     * @return 是否成功刷新
     */
    fun tryFlush() : Boolean

    /**
     * 强制刷新当前上下文。
     */
    fun flush()
}
