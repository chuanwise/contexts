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

import cn.chuanwise.contexts.reactions.reactiveCallObserver
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.MutableEntrySet
import cn.chuanwise.typeresolver.ResolvableType

@ContextsInternalApi
val primaryWriteObserver = ThreadLocal<ReactiveWriteObserver<out Any?>>()

@OptIn(ContextsInternalApi::class)
inline fun <T> ReactiveWriteObserver<out Any?>.withWriteObserver(block: () -> T): T {
    val backup = primaryWriteObserver.get()
    primaryWriteObserver.set(this)

    try {
        return block()
    } finally {
        if (backup == null) {
            primaryWriteObserver.remove()
        } else {
            primaryWriteObserver.set(backup)
        }
    }
}



@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
abstract class AbstractMutableReactive<T>(
    type: ResolvableType<T>, proxyClassLoader: ClassLoader?
) : AbstractReactive<T>(type, proxyClassLoader), MutableReactive<T> {
    private val writeObservers = MutableEntrySet<ReactiveWriteObserver<T>>()
    private val callObservers = MutableEntrySet<ReactiveCallObserver<T>>()

    private inner class ReactiveCallObserverImpl : ReactiveCallObserver<T> {
        override fun onCall(context: ReactiveCallContext<T>) {
            // 环境中的 call observer 是优先级最高的。
            (reactiveCallObserver.get() as? ReactiveCallObserver<T>)?.onCall(context)

            // 随后是已经注册的 call observers。
            callObservers.forEach {
                it.value.onCall(context)
            }
        }
    }
    private val callObserver: ReactiveCallObserver<T> = ReactiveCallObserverImpl()

    protected fun onValueWrite(value: T) {
        writeObservers.forEach {
            it.value.onValueWrite(this, value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun toProxyOrNull(raw: T) : T {
        if (raw == null) {
            return null as T
        }

        return tryToProxyOrNull(
            raw,
            type,
            this as AbstractReactive<Any?>,
            callObserver as ReactiveCallObserver<Any?>,
            source = null
        )
    }

    override fun addWriteObserver(observer: ReactiveWriteObserver<T>): MutableEntry<ReactiveWriteObserver<T>> {
        return writeObservers.add(observer)
    }

    override fun addCallObserver(observer: ReactiveCallObserver<T>): MutableEntry<ReactiveCallObserver<T>> {
        return callObservers.add(observer)
    }
}