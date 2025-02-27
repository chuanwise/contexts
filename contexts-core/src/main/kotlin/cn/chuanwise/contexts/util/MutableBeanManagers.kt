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

@file:JvmName("MutableBeanManagers")
package cn.chuanwise.contexts.util

import cn.chuanwise.typeresolver.ResolvableType
import cn.chuanwise.typeresolver.createResolvableType
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

@JvmOverloads
fun <T : Any> MutableBeanManager.addBean(
    value: T, beanClass: KClass<T>,
    id: String? = DEFAULT_BEAN_ID,
    primary: Boolean = DEFAULT_BEAN_PRIMARY
): MutableBeanEntry<T> {
    return addBean(value, createResolvableType(beanClass), id, primary)
}

@JvmOverloads
fun <T: Any> MutableBeanManager.addBeans(
    values: Iterable<T>, beanClass: KClass<T>,
    id: String? = DEFAULT_BEAN_ID,
    primary: Boolean = DEFAULT_BEAN_PRIMARY
): List<BeanEntry<T>> {
    return addBeans(values, createResolvableType(beanClass), id, primary)
}

@JvmOverloads
@JavaFriendlyApi
fun <T : Any> MutableBeanManager.addBean(
    value: T, beanClass: Class<T>,
    id: String? = DEFAULT_BEAN_ID,
    primary: Boolean = DEFAULT_BEAN_PRIMARY
): MutableBeanEntry<T> {
    return addBean(value, createResolvableType(beanClass.kotlin), id, primary)
}

@JvmOverloads
@JavaFriendlyApi
fun <T: Any> MutableBeanManager.addBeans(
    values: Iterable<T>, beanClass: Class<T>,
    id: String? = DEFAULT_BEAN_ID,
    primary: Boolean = DEFAULT_BEAN_PRIMARY
): List<BeanEntry<T>> {
    return addBeans(values, createResolvableType(beanClass.kotlin), id, primary)
}

inline fun <reified T> MutableBeanManager.addBeanByCompilationType(
    value: T,
    id: String? = DEFAULT_BEAN_ID,
    primary: Boolean = DEFAULT_BEAN_PRIMARY
): MutableBeanEntry<T> {
    return addBean(value, createResolvableType<T>(), id, primary)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> MutableBeanManager.addBeanByRuntimeType(
    value: T,
    id: String? = DEFAULT_BEAN_ID,
    primary: Boolean = DEFAULT_BEAN_PRIMARY
): MutableBeanEntry<T> {
    val valueType = createResolvableType(value::class.createType()) as ResolvableType<T>
    return addBean(value, valueType, id, primary)
}
