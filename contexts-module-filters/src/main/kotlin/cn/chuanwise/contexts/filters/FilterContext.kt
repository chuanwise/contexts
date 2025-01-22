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
import cn.chuanwise.contexts.util.MutableBeans

/**
 * 过滤器上下文。
 *
 * @param T 需要检查的对象类型。
 * @author Chuanwise
 */
interface FilterContext<out T : Any> {
    /**
     * 需要检查的对象。
     */
    val value: T

    /**
     * 通过哪个上下文开始检查对象。
     */
    val context: Context

    /**
     * 当前结果。
     */
    var result: Boolean?

    /**
     * 事件发布相关的上下文，用于存储一些和事件发布相关的数据。
     */
    val beans: MutableBeans

    /**
     * 过滤器缓存结果。
     */
    val caches: Map<Filter<@UnsafeVariance T>, Boolean?>

    val isTrue: Boolean get() = result == true
    val isFalse: Boolean get() = result == false
    val isNull: Boolean get() = result == null

    val isNotFalse: Boolean get() = result != false
    val isNotNull: Boolean get() = result != null
    val isNotTrue: Boolean get() = result != true
}

