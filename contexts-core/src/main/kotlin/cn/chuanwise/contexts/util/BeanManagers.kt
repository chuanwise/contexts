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

@file:JvmName("BeanManagers")
package cn.chuanwise.contexts.util

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@JvmOverloads
fun BeanManager.getBeanEntry(type: KType, id: String? = null, primary: Boolean? = null): BeanEntry<*>? {
    return getBeanEntry(createResolvableType(type), id, primary)
}

@JvmOverloads
fun BeanManager.getBeanEntryOrFail(type: KType, id: String? = null, primary: Boolean? = null): BeanEntry<*> {
    return getBeanEntryOrFail(createResolvableType(type), id, primary)
}

@JvmOverloads
fun <T> BeanManager.getBeanEntry(beanClass: KClass<T & Any>, id: String? = null, primary: Boolean? = null): BeanEntry<T>? {
    return getBeanEntry(createResolvableType(beanClass), id, primary)
}

@JvmOverloads
fun <T> BeanManager.getBeanEntryOrFail(beanClass: KClass<T & Any>, id: String? = null, primary: Boolean? = null): BeanEntry<T> {
    return getBeanEntryOrFail(createResolvableType(beanClass), id, primary)
}

@JvmOverloads
@JavaFriendlyApi
fun <T> BeanManager.getBeanEntry(beanClass: Class<T & Any>, id: String? = null, primary: Boolean? = null): BeanEntry<T>? {
    return getBeanEntry(createResolvableType(beanClass.kotlin), id, primary)
}

@JvmOverloads
@JavaFriendlyApi
fun <T> BeanManager.getBeanEntryOrFail(beanClass: Class<T & Any>, id: String? = null, primary: Boolean? = null): BeanEntry<T> {
    return getBeanEntryOrFail(createResolvableType(beanClass.kotlin), id, primary)
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> BeanManager.getBeanEntry(id: String? = null, primary: Boolean? = null): BeanEntry<T>? {
    return getBeanEntry(typeOf<T>(), id, primary) as BeanEntry<T>?
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> BeanManager.getBeanEntryOrFail(id: String? = null, primary: Boolean? = null): BeanEntry<T> {
    return getBeanEntryOrFail(typeOf<T>(), id, primary) as BeanEntry<T>
}

@JvmOverloads
fun BeanManager.getBean(type: KType, id: String? = null, primary: Boolean? = null): Any? {
    return getBean(createResolvableType(type), id, primary)
}

@JvmOverloads
@Suppress("UNCHECKED_CAST")
fun <T> BeanManager.getBeanOrFail(type: KType, id: String? = null, primary: Boolean? = null): T {
    return getBeanOrFail(createResolvableType(type), id, primary) as T
}

@JvmOverloads
fun <T> BeanManager.getBean(beanClass: KClass<T & Any>, id: String? = null, primary: Boolean? = null): T? {
    return getBean(createResolvableType(beanClass), id, primary)
}

@JvmOverloads
fun <T> BeanManager.getBeanOrFail(beanClass: KClass<T & Any>, id: String? = null, primary: Boolean? = null): T {
    return getBeanOrFail(createResolvableType(beanClass), id, primary)
}

@JvmOverloads
@JavaFriendlyApi
fun <T> BeanManager.getBean(beanClass: Class<T & Any>, id: String? = null, primary: Boolean? = null): T? {
    return getBean(createResolvableType(beanClass.kotlin), id, primary)
}

@JvmOverloads
@JavaFriendlyApi
fun <T> BeanManager.getBeanOrFail(beanClass: Class<T & Any>, id: String? = null, primary: Boolean? = null): T {
    return getBeanOrFail(createResolvableType(beanClass.kotlin), id, primary)
}

inline fun <reified T> BeanManager.getBean(id: String? = null, primary: Boolean? = null): T? {
    return getBean(typeOf<T>(), id, primary) as T?
}

inline fun <reified T> BeanManager.getBeanOrFail(id: String? = null, primary: Boolean? = null): T {
    return getBeanOrFail(typeOf<T>(), id, primary) as T
}

@JvmOverloads
fun BeanManager.getBeans(type: KType, id: String? = null, primary: Boolean? = null): List<*> {
    return getBeans(createResolvableType(type), id, primary)
}

@JvmOverloads
fun <T> BeanManager.getBeans(beanClass: KClass<T & Any>, id: String? = null, primary: Boolean? = null): List<T> {
    return getBeans(createResolvableType(beanClass), id, primary)
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> BeanManager.getBeans(id: String? = null, primary: Boolean? = null): List<T> {
    return getBeans(typeOf<T>(), id, primary) as List<T>
}