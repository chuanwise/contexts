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

import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.typeresolver.ResolvableType

/**
 * 和代理对象一一对应，存储相关上下文信息。
 *
 * @param T 代理对象类型
 * @author Chuanwise
 */
@NotStableForInheritance
interface Proxy<T : Any> {
    /**
     * 代理对象的类型。
     */
    val type: ResolvableType<T>

    /**
     * 被代理对象的原始值。
     */
    val value: T

    /**
     * 代理对象。
     */
    val valueProxy: T

    /**
     * 代理处理器。
     */
    var proxyHandler: ProxyHandler<T>
}