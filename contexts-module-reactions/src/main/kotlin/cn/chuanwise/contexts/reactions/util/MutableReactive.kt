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

package cn.chuanwise.contexts.reactions.util

import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.ResolvableType

interface MutableReactive<T> : Reactive<T> {
    override var value: T

    fun addWriteObserver(observer: ReactiveWriteObserver<T>): MutableEntry<ReactiveWriteObserver<T>>
}

@ContextsInternalApi
class MutableReactiveImpl<T>(initialValue: T, type: ResolvableType<T>) : AbstractMutableReactive<T>(type) {
    override var value: T = initialValue
        get() = onValueRead(field)
        set(value) {
            field = value
            onValueWrite(value)
        }

    override fun toString(): String {
        return "MutableReactive(value=$value)"
    }
}