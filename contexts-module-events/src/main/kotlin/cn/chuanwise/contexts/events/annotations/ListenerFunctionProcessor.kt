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

package cn.chuanwise.contexts.events.annotations

import cn.chuanwise.contexts.annotations.ArgumentResolver
import cn.chuanwise.contexts.annotations.FunctionProcessContext
import cn.chuanwise.contexts.util.ContextsInternalApi
import kotlin.reflect.KParameter

interface ListenerFunctionProcessContext<T> : FunctionProcessContext<Listener> {
    val eventClass: Class<T>
    val argumentResolvers: Map<KParameter, ArgumentResolver>
}

fun interface ListenerFunctionProcessor<T> {
    fun process(context: ListenerFunctionProcessContext<T>)
}

@ContextsInternalApi
class ListenerFunctionProcessContextImpl<T>(
    override val eventClass: Class<T>,
    override val argumentResolvers: Map<KParameter, ArgumentResolver>,
    private val functionProcessContext: FunctionProcessContext<Listener>
) : ListenerFunctionProcessContext<T>, FunctionProcessContext<Listener> by functionProcessContext