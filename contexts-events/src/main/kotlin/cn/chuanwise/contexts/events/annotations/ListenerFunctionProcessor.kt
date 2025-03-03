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

import cn.chuanwise.contexts.annotation.ArgumentResolver
import cn.chuanwise.contexts.annotation.AnnotationFunctionProcessContext
import cn.chuanwise.contexts.util.ContextsInternalApi
import kotlin.reflect.KClass
import kotlin.reflect.KParameter

interface ListenerAnnotationFunctionProcessContext<T : Any> : AnnotationFunctionProcessContext<Listener> {
    val eventClass: KClass<T>
    val argumentResolvers: Map<KParameter, ArgumentResolver>
}

fun interface ListenerFunctionProcessor<T : Any> {
    fun process(context: ListenerAnnotationFunctionProcessContext<T>)
}

@ContextsInternalApi
class ListenerAnnotationFunctionProcessContextImpl<T : Any>(
    override val eventClass: KClass<T>,
    override val argumentResolvers: Map<KParameter, ArgumentResolver>,
    private val annotationFunctionProcessContext: AnnotationFunctionProcessContext<Listener>
) : ListenerAnnotationFunctionProcessContext<T>, AnnotationFunctionProcessContext<Listener> by annotationFunctionProcessContext