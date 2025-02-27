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

import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.typeresolver.ResolvableType

/**
 * 一个响应式的值，当值发生变化时，会通知所有监听者。
 *
 * 值类型若为 `final`，无法产生代理，则只有在值发生变化时，才会通知监听者。
 * 否则一旦调用代理的任何方法，便会通知监听者检测是否需要更新。
 *
 * @param T 值类型
 * @author Chuanwise
 * @see MutableReactive
 */
@NotStableForInheritance
interface Reactive<T> {
    /**
     * 对于 `final` 类型，为原始值；对于非 `final` 类型，为代理值。
     */
    val value: T

    /**
     * 值的类型。
     */
    val type: ResolvableType<T>
}