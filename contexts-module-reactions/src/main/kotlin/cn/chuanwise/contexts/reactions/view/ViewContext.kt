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

package cn.chuanwise.contexts.reactions.view

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.reactions.ReactionManager
import cn.chuanwise.contexts.reactions.ReactionModule
import cn.chuanwise.contexts.reactions.ReactionModuleImpl
import cn.chuanwise.contexts.reactions.reactive.MutableReactive
import cn.chuanwise.contexts.reactions.reactive.Reactive
import cn.chuanwise.contexts.reactions.reactive.ReactiveCallContext
import cn.chuanwise.contexts.reactions.reactive.ReactiveCallObserver
import cn.chuanwise.contexts.reactions.reactive.ReactiveWriteObserver
import cn.chuanwise.contexts.util.BeanManager
import cn.chuanwise.contexts.util.ContextsInternalApi
import java.util.concurrent.ConcurrentHashMap

interface ViewContext {
    val context: Context
    val beanManager: BeanManager

    val reactionManager: ReactionManager
    val reactionModule: ReactionModule

    fun <T> bind(reactive: Reactive<T>, cache: T)
}

@ContextsInternalApi
class ViewContextImpl(
    override val context: Context,
    override val beanManager: BeanManager,
    override val reactionManager: ReactionModuleImpl.ReactionManagerImpl,
    override val reactionModule: ReactionModule
) : ViewContext {
    private inner class Bind(reactive: MutableReactive<Any?>) {
        private val writeObserverEntry = reactive.addWriteObserver(viewFlusher)
        private val callObserverEntry = reactive.addCallObserver(viewFlusher)

        fun remove() {
            writeObserverEntry.remove()
            callObserverEntry.remove()
        }
    }

    private val reactiveBinds = ConcurrentHashMap<Reactive<Any?>, Bind>()

    private inner class ViewFlusher : ReactiveWriteObserver<Any?>, ReactiveCallObserver<Any?> {
        override fun onValueWrite(reactive: Reactive<Any?>, value: Any?) {
            reactionManager.tryFlush(reactive, value)
        }

        override fun onFunctionCall(context: ReactiveCallContext<Any?>) {
            reactionManager.onFunctionCall(context)
        }
    }

    private val viewFlusher = ViewFlusher()

    @Suppress("UNCHECKED_CAST")
    override fun <T> bind(reactive: Reactive<T>, cache: T) {
        reactionManager.setReactiveCache(reactive, cache)
        reactiveBinds.computeIfAbsent(reactive as Reactive<Any?>) {
            when (reactive) {
                is MutableReactive -> Bind(reactive as MutableReactive<Any?>)
                else -> error("Unsupported reactive type: ${reactive::class.java}")
            }
        }
    }

    fun exit() {
        reactiveBinds.values.forEach {
            it.remove()
        }
        context.exit()
    }
}