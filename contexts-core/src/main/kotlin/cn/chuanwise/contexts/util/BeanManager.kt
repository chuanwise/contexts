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

package cn.chuanwise.contexts.util

import java.util.Queue
import java.util.Stack

/**
 * 一个对象容器。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface BeanManager {
    /**
     * 获取给定类型的唯一对象。
     *
     * @param T 对象类型。
     * @param beanType 对象的类型。
     * @param id 对象的 ID，`null` 表示不关心 ID。
     * @param primary 是否为主要对象，`null` 表示不关心是否为主要对象。
     * @return 对象。当无此类型的唯一对象时，返回 `null`。
     * @throws IllegalArgumentException 当类型为 [Nothing] 时，或类型尚未完全解析。
     */
    fun <T> getBeanEntry(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): BeanEntry<T>?

    /**
     * 获取给定类型的唯一对象。
     *
     * @param T 对象类型。
     * @param beanType 对象的类型。
     * @param id 对象的 ID，`null` 表示不关心 ID。
     * @param primary 是否为主要对象，`null` 表示不关心是否为主要对象。
     * @return 对象。
     * @throws IllegalArgumentException 当类型为 [Nothing] 时，或类型尚未完全解析。
     * @throws NoSuchElementException 当无此类型的唯一对象时。
     */
    fun <T> getBeanEntryOrFail(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): BeanEntry<T>

    /**
     * 获取给定类型的唯一对象。
     * 
     * 当不关心 ID 时，支持使用集合和数组类型，即 [List]、[Set]、[Queue]、[Stack] 和 [Array]。
     * 此外也支持使用 [Map]，键为 `String?` 或 `String`。
     * 
     * 若无给定类型的对象，则收集集合元素类型并组装为集合返回。
     *
     * @param T 对象类型。 
     * @param beanType 对象的类型。
     * @param id 对象的 ID，`null` 表示不关心 ID。
     * @param primary 是否为主要对象，`null` 表示不关心是否为主要对象。
     * @return 对象。当无此类型的唯一对象时，返回 `null`。
     * @throws IllegalArgumentException 当类型为 [Nothing] 时，或类型尚未完全解析。
     */
    fun <T> getBean(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): T?
    
    /**
     * 获取给定类型的唯一对象。
     * 
     * 当不关心 ID 时，支持使用集合和数组类型，即 [List]、[Set]、[Queue]、[Stack] 和 [Array]。
     * 此外也支持使用 [Map]，键为 `String?` 或 `String`。
     * 
     * 若无给定类型的对象，则收集集合元素类型并组装为集合返回。
     *
     * @param T 对象类型。 
     * @param beanType 对象的类型。
     * @param id 对象的 ID，`null` 表示不关心 ID。
     * @param primary 是否为主要对象，`null` 表示不关心是否为主要对象。
     * @return 对象。
     * @throws NoSuchElementException 当无此类型的唯一对象时。
     * @throws IllegalArgumentException 当类型为 [Nothing] 时，或类型尚未完全解析。
     */
    fun <T> getBeanOrFail(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): T

    /**
     * 获取给定类型的所有对象。
     *
     * @param T 对象类型。
     * @param beanType 对象的类型。
     * @param id 对象的 ID，`null` 表示不关心 ID。
     * @param primary 是否为主要对象，`null` 表示不关心是否为主要对象。
     * @return 对象列表。
     * @throws IllegalArgumentException 当类型为 [Nothing] 时，或类型尚未完全解析。
     */
    fun <T> getBeanEntries(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): List<BeanEntry<T>>

    /**
     * 获取给定类型的所有对象。
     *
     * @param T 对象类型。
     * @param beanType 对象的类型。
     * @param id 对象的 ID，`null` 表示不关心 ID。
     * @param primary 是否为主要对象，`null` 表示不关心是否为主要对象。
     * @return 对象列表。
     * @throws IllegalArgumentException 当类型为 [Nothing] 时，或类型尚未完全解析。
     */
    fun <T> getBeans(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): List<T>
}