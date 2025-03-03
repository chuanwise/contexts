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

@file:JvmName("ViewContexts")

package cn.chuanwise.contexts.reactions.view

import cn.chuanwise.contexts.reactions.reactive.ReactiveCallContext
import cn.chuanwise.contexts.reactions.reactive.ReactiveCallObserver
import cn.chuanwise.contexts.reactions.reactive.ReactiveReadContext
import cn.chuanwise.contexts.reactions.reactive.ReactiveReadObserver
import cn.chuanwise.contexts.reactions.reactive.withPrimaryReadObserver
import cn.chuanwise.contexts.reactions.withCallObserver
import cn.chuanwise.contexts.util.ContextsInternalApi

// 将对 Reactive 的读写请求转换为代理对象，同时在读的时候自动绑定。
@ContextsInternalApi
class ViewContextBinder(
    private val viewContext: ViewContextImpl
) : ReactiveReadObserver<Any?>, ReactiveCallObserver<Any?> {
    override fun onRead(context: ReactiveReadContext<Any?>) {
        viewContext.bind(context.reactive, context.value)
    }

    override fun onCall(context: ReactiveCallContext<Any?>) {
        viewContext.bind(context.reactive, context.raw)
    }
}

@OptIn(ContextsInternalApi::class)
inline fun <T> ViewContext.bind(block: () -> T): T {
    require(this is ViewContextImpl) { "The ViewContext must be an instance of ViewContextImpl." }

    val binder = ViewContextBinder(this)
    return binder.withPrimaryReadObserver {
        binder.withCallObserver {
            block()
        }
    }
}