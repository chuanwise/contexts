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

import cn.chuanwise.contexts.reactions.util.Reactive
import cn.chuanwise.contexts.reactions.util.ReactiveReadObserver
import cn.chuanwise.contexts.reactions.util.withReactiveReadObserver
import cn.chuanwise.contexts.reactions.util.withReactiveWriteObserver
import cn.chuanwise.contexts.util.ContextsInternalApi

// 将对 Reactive 的读写请求转换为代理对象，同时在读的时候自动绑定。
@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class ViewContextBindReadObserver(
    private val viewContext: ViewContextImpl
) : ReactiveReadObserver<Any?> {
    override fun onValueRead(reactive: Reactive<Any?>, value: Any?): Any? {
        viewContext.bind(reactive, value)

        val expectClass = reactive.type.rawClass.java as Class<Any?>
        val modelHandler = viewContext.reactionModule.getModelHandler(expectClass) ?: return value
        return modelHandler.value.toProxy(viewContext.context, expectClass, value)
    }
}

@OptIn(ContextsInternalApi::class)
inline fun <T> ViewContext.bindUsed(block: () -> T): T {
    val bindObservers = ViewContextBindReadObserver(this as ViewContextImpl)
    bindObservers.withReactiveReadObserver {
        return block()
    }
}