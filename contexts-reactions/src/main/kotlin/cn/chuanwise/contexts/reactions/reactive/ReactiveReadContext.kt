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

import cn.chuanwise.contexts.util.ContextsInternalApi

interface ReactiveReadContext<T> : ReactiveContext<T> {
    /**
     * 此处的值是原始值，后续可能会被代理。
     * 修改将会影响后续读到的值。
     */
    var value: T
}

@ContextsInternalApi
class ReactiveReadContextImpl<T>(
    override val reactive: Reactive<T>,
    override var value: T
) : AbstractReactiveContext<T>(), ReactiveReadContext<T> {
    override fun toString(): String {
        return "ReactiveReadContext(value=$value, reactive=$reactive)"
    }
}