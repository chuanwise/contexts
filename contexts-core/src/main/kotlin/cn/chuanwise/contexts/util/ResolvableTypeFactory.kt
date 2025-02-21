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

@file:JvmName("ResolvableTypeFactory")
package cn.chuanwise.contexts.util

import com.fasterxml.jackson.databind.util.LRUMap
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

@Suppress("UNCHECKED_CAST")
fun <T : Any> createResolvableType(type: KClass<T>): ResolvableType<T> = createResolvableType(type.createType()) as ResolvableType<T>

private val INSTANCES = LRUMap<KType, ResolvableType<*>>(
    System.getProperty("contexts.resolvableType.cache.initialEntries", "512").toInt(),
    System.getProperty("contexts.resolvableType.cache.maxEntries", "1024").toInt()
)

private fun cacheIfResolved(type: ResolvableType<*>?) {
    if (type != null && type.isResolved) {
        INSTANCES.put(type.rawType, type)
    }
}
private fun cacheIfResolvedRecursively(type: ResolvableType<*>?) {
    type ?: return

    if (type.isResolved) {
        cacheIfResolved(type)
    }
    for (interfaceResolvableType in type.parentTypes) {
        cacheIfResolvedRecursively(interfaceResolvableType)
    }
}

@OptIn(ContextsInternalApi::class)
fun createResolvableType(type: KType): ResolvableType<*> {
    var result = INSTANCES[type]
    if (result == null) {
        val instances = mutableMapOf<KType, ResolvableType<*>>()
        result = ResolvableTypeImpl(type, instances)
        instances.values.forEach { cacheIfResolvedRecursively(it) }
    }
    return result
}

@ContextsInternalApi
fun getResolvableTypeCache(type: KType): ResolvableType<*>? {
    return INSTANCES[type]
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> createResolvableType(): ResolvableType<T> {
    return createResolvableType(typeOf<T>()) as ResolvableType<T>
}

fun <T : Any> KClass<T>.toResolvableType(): ResolvableType<T> {
    return createResolvableType(this)
}