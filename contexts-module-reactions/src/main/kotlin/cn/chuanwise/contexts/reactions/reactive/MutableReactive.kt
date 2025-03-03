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
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.typeresolver.ResolvableType

interface MutableReactive<T> : Reactive<T> {
    override var value: T

    fun addWriteObserver(observer: ReactiveWriteObserver<T>): MutableEntry<ReactiveWriteObserver<T>>
    fun addCallObserver(observer: ReactiveCallObserver<T>): MutableEntry<ReactiveCallObserver<T>>
}

@ContextsInternalApi
class MutableReactiveImpl<T>(
    initialValue: T, type: ResolvableType<T>, proxyClassLoader: ClassLoader?
) : AbstractMutableReactive<T>(type, proxyClassLoader) {
    override var rawValue: T = initialValue

    override var value: T
        get() = onValueRead(toProxyOrNull(rawValue))
        set(value) {
            val result = tryToRawOrNull(value)
            rawValue = result
            onValueWrite(result)
        }

    override fun toString(): String {
        return "MutableReactive(value=$rawValue)"
    }
}