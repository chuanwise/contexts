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

package cn.chuanwise.contexts.reactions.proxy

import cn.chuanwise.contexts.util.ContextsInternalApi

/**
 * 代理处理器，以代理函数调用。
 *
 * @param T 代理类型。
 * @author Chuanwise
 */
fun interface ProxyHandler<in T : Any> {
    fun onCall(call: ProxyCall<@UnsafeVariance T>) : Any?
}

/**
 * 不进行任何操作，只是将函数调用原样转发给原始对象的代理处理器。
 *
 * @author Chuanwise
 */
@ContextsInternalApi
object JustForwardProxyHandler : ProxyHandler<Any> {
    override fun onCall(call: ProxyCall<Any>): Any? = call.call()
}
