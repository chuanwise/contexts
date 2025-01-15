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

package cn.chuanwise.contexts.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.type.TypeFactory
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

@NotStableForInheritance
interface Beans {
    operator fun <T> get(beanClass: Class<T>): T?
    operator fun <T> get(beanType: TypeReference<T>): T?
    operator fun get(type: Type): Any?

    fun <T> getOrFail(beanClass: Class<T>): T?
    fun <T> getOrFail(beanType: TypeReference<T>): T?
    fun getOrFail(type: Type): Any?

    fun <T> getAll(beanClass: Class<T>): List<T>
    fun <T> getAll(beanType: TypeReference<T>): List<T>
    fun getAll(type: Type): List<Any>
}

inline fun <reified T> Beans.get(): T? {
    return get(T::class.java)
}
inline fun <reified T> Beans.getOrFail(): T? {
    return getOrFail(T::class.java)
}

@NotStableForInheritance
interface MutableBeans : Beans {
    fun <T> put(bean: T)
    fun <T> putAll(beans: Collection<T>)
}

@OptIn(ContextsInternalApi::class)
fun MutableBeans(): MutableBeans = MutableBeansImpl()

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
abstract class AbstractBeans : Beans {
    override fun <T> get(beanClass: Class<T>): T? {
        return get(beanClass as Type) as T?
    }

    override fun <T> get(beanType: TypeReference<T>): T? {
        return get(beanType.type) as T?
    }

    override fun <T> getOrFail(beanClass: Class<T>): T? {
        return get(beanClass) ?: throw NoSuchElementException("Cannot find the bean of class $beanClass")
    }

    override fun <T> getOrFail(beanType: TypeReference<T>): T? {
        return get(beanType) ?: throw NoSuchElementException("Cannot find the bean of type ${beanType.type}")
    }

    override fun getOrFail(type: Type): Any? {
        return get(type) ?: throw NoSuchElementException("Cannot find the bean of type $type")
    }

    override fun <T> getAll(beanClass: Class<T>): List<T> {
        return getAll(beanClass as Type) as List<T>
    }

    override fun <T> getAll(beanType: TypeReference<T>): List<T> {
        return getAll(beanType.type) as List<T>
    }
}

@ContextsInternalApi
abstract class AbstractMutableBeans : AbstractBeans(), MutableBeans

private val typeFactory: TypeFactory = TypeFactory.defaultInstance()
private val types: MutableMap<String, JavaType> = ConcurrentHashMap()

private fun getJavaType(type: Type): JavaType = types.computeIfAbsent(type.typeName) {
    typeFactory.constructType(type)
}

@ContextsInternalApi
class MutableBeansImpl : AbstractMutableBeans() {
//    private class BeanEntry(
//        val type: JavaType,
//        val value: Any
//    )
//    private val entries: MutableCollection<BeanEntry> = ConcurrentLinkedDeque()

    private val beans = ConcurrentLinkedDeque<Any>()

    override fun get(type: Type): Any? {
//        val javaType = getJavaType(type)
        return getAll(type).singleOrNull()
    }

    override fun getAll(type: Type): List<Any> {
//        return when (val javaType = getJavaType(type)) {
//            is CollectionLikeType -> getAll(javaType.contentType)
//        }
        return beans.filterIsInstance(type as Class<*>)
    }

    override fun <T> put(bean: T) {
        beans.add(bean)
    }

    override fun <T> putAll(beans: Collection<T>) {
        this.beans.addAll(beans)
    }
}