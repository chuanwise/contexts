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
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

/**
 * 表示对代理对象的一次函数调用。
 *
 * @param T 被代理类型
 * @author Chuanwise
 */
@NotStableForInheritance
interface ProxyCall<T : Any> {
    /**
     * 代理对象。
     */
    val proxy: Proxy<T>

    /**
     * 函数参数。
     */
    val arguments: Array<Any?>

    /**
     * 被调用的原始 JVM 函数。
     */
    val rawMethod: Method

    /**
     * 函数返回值类型。注意此处的类型是经过推导的。例如对于 [MutableMap.put]，其返回的是 `V?`。
     * 如果代理类的实际类型是 `Map<String, Int>`，会被自动推导为实际类型 `Int?`。`null` 表示无法推导。
     */
    val returnType: ResolvableType<*>?

    /**
     * 该函数对应的 Kotlin 函数。
     * 注意有些函数不一定有对应 Kotlin 函数，例如属性 Getter 和 Setter。
     */
    val rawFunction: KFunction<*>?

    /**
     * 如果该函数是属性 Getter，则为对应的属性；否则为 `null`。
     */
    val rawPropertyToGet: KProperty<*>?

    /**
     * 如果该函数是属性 Setter，则为对应的属性；否则为 `null`。
     */
    val rawPropertyToSet: KMutableProperty<*>?

    /**
     * 调用函数。
     *
     * @param value 函数调用的值
     * @return 函数调用的返回值
     */
    fun call(value: T = proxy.value) : Any?
}