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

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.type.CollectionType
import com.fasterxml.jackson.databind.type.TypeFactory
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * 表示一个在 [BeanFactory] 内的对象。
 *
 * @param T 对象的类型
 */
@NotStableForInheritance
interface Bean<out T : Any> {
    /**
     * 对象的值。
     */
    val value: T

    /**
     * 对象的键。
     */
    val keys: Set<Any>?

    /**
     * 是否为主要对象。
     */
    val isPrimary: Boolean

    /**
     * 是否已经被移除。
     */
    val isRemoved: Boolean
}

/**
 * 一个对象容器。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface BeanFactory {
    /**
     * 获取一个对象。
     *
     * @param type 对象的类型
     * @param primary 是否为主要对象
     * @param key 对象的键
     * @return 对象
     */
    fun getBean(type: Type, primary: Boolean? = null, key: Any? = null): Bean<*>?

    /**
     * 获取一个对象的值。
     *
     * @param type 对象的类型
     * @param primary 是否为主要对象
     * @param key 对象的键
     * @return 对象的值
     */
    fun getBeanValue(type: Type, primary: Boolean? = null, key: Any? = null): Any?

    /**
     * 获取一个对象，如果不存在则抛出异常。
     *
     * @param type 对象的类型
     * @param primary 是否为主要对象
     * @param key 对象的键
     * @return 对象
     * @throws NoSuchElementException 如果对象不存在
     */
    fun getBeanOrFail(type: Type, primary: Boolean? = null, key: Any? = null): Bean<*>

    /**
     * 获取一个对象的值，如果不存在则抛出异常。
     *
     * @param type 对象的类型
     * @param primary 是否为主要对象
     * @param key 对象的键
     * @return 对象的值
     * @throws NoSuchElementException 如果对象不存在
     */
    fun getBeanValueOrFail(type: Type, primary: Boolean? = null, key: Any? = null): Any

    /**
     * 获取一个对象。
     *
     * @param T 对象的类型
     * @param beanClass 对象的类
     * @param primary 是否为主要对象
     * @param key 对象的键
     * @return 对象
     */
    fun <T : Any> getBean(beanClass: Class<T>, primary: Boolean? = null, key: Any? = null): Bean<T>?

    /**
     * 获取一个对象的值。
     *
     * @param T 对象的类型
     * @param beanClass 对象的类
     * @param primary 是否为主要对象
     * @param key 对象的键
     * @return 对象的值。
     */
    fun <T : Any> getBeanValue(beanClass: Class<T>, primary: Boolean? = null, key: Any? = null): T?

    /**
     * 获取一个对象，如果不存在则抛出异常。
     *
     * @param T 对象的类型
     * @param beanClass 对象的类
     * @param primary 是否为主要对象
     * @param key 对象的键
     * @return 对象的值。
     * @throws NoSuchElementException 如果对象不存在
     */
    fun <T : Any> getBeanOrFail(beanClass: Class<T>, primary: Boolean? = null, key: Any? = null): Bean<T>

    /**
     * 获取一个对象的值，如果不存在则抛出异常。
     *
     * @param T 对象的类型
     * @param beanClass 对象的类
     * @param primary 是否为主要对象
     * @param key 对象的键
     * @return 对象的值。
     * @throws NoSuchElementException 如果对象不存在
     */
    fun <T : Any> getBeanValueOrFail(beanClass: Class<T>, primary: Boolean? = null, key: Any? = null): T

    /**
     * 获取所有对象。
     *
     * @param type 对象的类型
     * @return 所有对象
     */
    fun getBeans(type: Type): List<Bean<*>>

    /**
     * 获取所有对象的值。
     *
     * @param type 对象的类型
     * @return 所有对象的值
     */
    fun getBeanValues(type: Type): List<*>

    /**
     * 获取所有对象。
     *
     * @param T 对象的类型
     * @param beanClass 对象的类
     * @return 所有对象
     */
    fun <T : Any> getBeans(beanClass: Class<T>): List<Bean<T>>

    /**
     * 获取所有对象的值。
     *
     * @param T 对象的类型
     * @param beanClass 对象的类
     * @return 所有对象的值
     */
    fun <T : Any> getBeanValues(beanClass: Class<T>): List<T>
}

inline fun <reified T : Any> BeanFactory.getBean(): Bean<T>? {
    return getBean(T::class.java)
}
inline fun <reified T : Any> BeanFactory.getBeanOrFail(): Bean<T> {
    return getBeanOrFail(T::class.java)
}

@JvmOverloads
inline fun <reified T : Any> BeanFactory.getBeanValue(
    key: Any? = DEFAULT_BEAN_KEY, primary: Boolean? = DEFAULT_BEAN_PRIMARY
): T? {
    return getBeanValue(T::class.java, key = key, primary = primary)
}

inline fun <reified T : Any> BeanFactory.getBeanValueOrFail(): T {
    return getBeanValueOrFail(T::class.java)
}

private val typeFactory: TypeFactory = TypeFactory.defaultInstance()
private val types: MutableMap<String, JavaType> = ConcurrentHashMap()

private fun getJavaType(type: Type): JavaType = types.computeIfAbsent(type.typeName) {
    typeFactory.constructType(type)
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
abstract class AbstractBeanFactory : BeanFactory {
    override fun getBean(type: Type, primary: Boolean?, key: Any?): Bean<*>? {
        require(type is Class<*>) { "Type must be a class." }
        return getBean(type, primary, key)
    }

    override fun getBeanValue(type: Type, primary: Boolean?, key: Any?): Any? {
        return when (val javaType = getJavaType(type)) {
            is CollectionType -> when (type) {
                List::class.java -> getBeanValues(javaType.contentType)
                Set::class.java -> getBeanValues(javaType.contentType).toSet()
                else -> throw IllegalArgumentException("Unsupported collection type: $type.")
            }
            else -> getBean(javaType.rawClass, primary, key)?.value
        }
    }

    override fun <T : Any> getBeanValue(beanClass: Class<T>, primary: Boolean?, key: Any?): T? {
        return getBeanValue(beanClass as Type, primary, key) as T?
    }

    override fun getBeanOrFail(type: Type, primary: Boolean?, key: Any?): Bean<*> {
        return getBean(type, primary, key) ?: throw NoSuchElementException("No bean found for type ${type.typeName}.")
    }

    override fun <T : Any> getBeanOrFail(beanClass: Class<T>, primary: Boolean?, key: Any?): Bean<T> {
        return getBeanOrFail(beanClass as Type, primary, key) as Bean<T>
    }

    override fun getBeanValueOrFail(type: Type, primary: Boolean?, key: Any?): Any {
        return getBeanValue(type, primary, key) ?: throw NoSuchElementException("No bean found for type ${type.typeName}.")
    }

    override fun <T : Any> getBeanValueOrFail(beanClass: Class<T>, primary: Boolean?, key: Any?): T {
        return getBeanValueOrFail(beanClass as Type, primary, key) as T
    }

    override fun getBeans(type: Type): List<Bean<*>> {
        require(type is Class<*>) { "Type must be a class." }
        return getBeans(type)
    }

    override fun getBeanValues(type: Type): List<*> {
        require(type is Class<*>) { "Type must be a class." }
        return getBeanValues(type)
    }
}