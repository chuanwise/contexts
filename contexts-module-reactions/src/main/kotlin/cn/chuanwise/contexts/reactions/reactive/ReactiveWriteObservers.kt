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

@file:JvmName("ReactiveWriteObservers")

package cn.chuanwise.contexts.reactions.reactive

import cn.chuanwise.contexts.util.ContextsInternalApi

@ContextsInternalApi
val reactiveWriteObserver = ThreadLocal<ReactiveWriteObserver<out Any?>>()

@OptIn(ContextsInternalApi::class)
inline fun <T> ReactiveWriteObserver<out Any?>.withWriteObserver(block: () -> T): T {
    val backup = reactiveWriteObserver.get()
    reactiveWriteObserver.set(this)

    try {
        return block()
    } finally {
        if (backup == null) {
            reactiveWriteObserver.remove()
        } else {
            reactiveWriteObserver.set(backup)
        }
    }
}