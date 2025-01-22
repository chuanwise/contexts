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

package cn.chuanwise.contexts.filters

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.util.Beans
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.getBeanValue
import cn.chuanwise.contexts.util.getBeanValueOrFail

/**
 * 过滤器管理器。
 *
 * 过滤器是用于过滤非空数据的工具，用于决定是否要在上下文的范围内传播数据。
 *
 * @author Chuanwise
 */
interface FilterManager {
    /**
     * 当前上下文。
     */
    val context: Context

    /**
     * 过滤器解析器。
     */
    var resolver: FilterResolver?

    /**
     * 过滤数据。
     *
     * 过滤器首先尝试使用注册在当前上下文的过滤器，并在产生结果后立刻返回。
     * 若当前上下文没有产生结果，则检查父上下文。
     *
     * 若父上下文只有一个，则会直接将其结果作为过滤结果。若父上下文有多个，
     * 系统尝试获取 [FilterResolver]，并用它结算多个父上下文时，父上下文一起给出的过滤结果。
     * 若未设置则抛出异常。
     *
     * 过滤器检查的默认顺序是从当前上下文出发，按照广度优先的顺序检查各个父上下文的过滤结果，
     * 并在出现结果是立刻返回。可以在被过滤的对象上使用 `Filter` 注解指定其过滤范围。
     *
     * @param T 需要过滤的数据类型
     * @param value 需要过滤的数据
     * @return 过滤上下文。
     */
    fun <T : Any> filter(value: T): FilterContext<T>

    /**
     * 再次过滤数据，但是复用以往的过滤上下文。
     *
     * @param T 需要过滤的数据类型
     * @param filterContext 过滤上下文
     * @return 过滤上下文
     */
    fun <T : Any> filter(filterContext: FilterContext<T>): FilterContext<T>

    /**
     * 注册过滤器。
     *
     * @param T 过滤器的类型
     * @param valueClass 过滤器过滤的数据类型
     * @param filter 过滤器
     * @return 过滤器的可变条目
     */
    fun <T : Any> registerFilter(valueClass: Class<T>, cache: Boolean = false, filter: Filter<T>) : MutableEntry<Filter<T>>

    /**
     * 注册过滤器。
     *
     * @param filter 过滤器
     * @return 过滤器的可变条目
     */
    fun registerFilter(cache: Boolean = false, filter: Filter<Any>) : MutableEntry<Filter<Any>>
}

val Beans.filterManagerOrNull: FilterManager? get() = getBeanValue()
val Beans.filterManager: FilterManager get() = getBeanValueOrFail()