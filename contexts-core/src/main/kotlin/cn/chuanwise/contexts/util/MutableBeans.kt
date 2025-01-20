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

import java.lang.reflect.Type
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import kotlin.concurrent.withLock

/**
 * 表示一个在 [Beans] 内的对象。
 *
 * @param T 对象类型
 * @author Chuanwise
 */
@NotStableForInheritance
interface MutableBean<T : Any> : Bean<T>, AutoCloseable {
    override val value: T
    override var keys: MutableSet<Any>?

    override var isPrimary: Boolean
    override val isRemoved: Boolean

    /**
     * 移除对象。
     */
    fun remove()
    override fun close() = remove()
}

/**
 * 可变的对象容器。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface MutableBeans : Beans {
    override fun getBean(type: Type, primary: Boolean?, key: Any?): Bean<*>?
    override fun getBeanOrFail(type: Type, primary: Boolean?, key: Any?): Bean<*>

    override fun <T : Any> getBean(beanClass: Class<T>, primary: Boolean?, key: Any?): Bean<T>?
    override fun <T : Any> getBeanOrFail(beanClass: Class<T>, primary: Boolean?, key: Any?): Bean<T>

    override fun getBeans(type: Type): List<Bean<*>>
    override fun <T : Any> getBeans(beanClass: Class<T>): List<Bean<T>>

    fun <T : Any> registerBean(value: T, keys: MutableSet<Any>, primary: Boolean = false): MutableBean<T>
    fun <T : Any> registerBean(value: T, key: Any, primary: Boolean = false): MutableBean<T>
    fun <T : Any> registerBean(value: T, primary: Boolean = false): MutableBean<T>

    fun <T : Any> registerBeans(values: Iterable<T>): List<Bean<T>> {
        return values.map { registerBean(it) }
    }
}

inline fun <reified T : Any> MutableBeans.getBean(): Bean<T>? {
    return getBean(T::class.java)
}
inline fun <reified T : Any> MutableBeans.getBeanOrFail(): Bean<T> {
    return getBeanOrFail(T::class.java)
}

inline fun <reified T : Any> MutableBeans.getBeanValue(): T? {
    return getBeanValue(T::class.java)
}
inline fun <reified T : Any> MutableBeans.getBeanValueOrFail(): T {
    return getBeanValueOrFail(T::class.java)
}

@ContextsInternalApi
abstract class AbstractMutableBeans : AbstractBeans(), MutableBeans {
    private abstract inner class AbstractMutableBean<T : Any>(
        override var keys: MutableSet<Any>?,
        override var isPrimary: Boolean,
    ) : MutableBean<T> {
        private var mutableIsRemoved = AtomicBoolean(false)
        override val isRemoved: Boolean get() = mutableIsRemoved.get()

        override fun remove() {
            if (mutableIsRemoved.compareAndSet(false, true)) {
                removeBean(this)
            }
        }
    }

    private inner class LiteralMutableBeanImpl<T : Any>(
        override val value: T,
        override var keys: MutableSet<Any>?,
        override var isPrimary: Boolean
    ) : AbstractMutableBean<T>(keys, isPrimary) {
        init {
            onBeanValueSpawned(value)
        }
    }

    override fun <T : Any> registerBean(value: T, primary: Boolean): MutableBean<T> {
        return LiteralMutableBeanImpl(value, null, primary).apply { registerBean(this) }
    }

    override fun <T : Any> registerBean(value: T, key: Any, primary: Boolean): MutableBean<T> {
        return LiteralMutableBeanImpl(value, mutableSetOf(key), primary).apply { registerBean(this) }
    }

    override fun <T : Any> registerBean(value: T, keys: MutableSet<Any>, primary: Boolean): MutableBean<T> {
        return LiteralMutableBeanImpl(value, keys, primary).apply { registerBean(this) }
    }

    abstract fun registerBean(bean: MutableBean<*>)

    abstract fun removeBean(bean: MutableBean<*>)

    private fun onBeanValueSpawned(value: Any) {
        val joints = value::class.annotations.filterIsInstance<Joint>()
        for (joint in joints) {
            val bean = joint.beanClass.java.getInstanceOrFail()
            val keys = joint.keys.takeIf { it.isNotEmpty() }
            if (keys == null) {
                registerBean(bean, primary = joint.primary)
            } else {
                registerBean(bean, keys = keys.toMutableSet(), primary = joint.primary)
            }
        }
    }
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class MutableBeanImpl: AbstractMutableBeans() {
    private val beans = ConcurrentLinkedDeque<Bean<*>>()

    override fun registerBean(bean: MutableBean<*>) {
        beans.add(bean)
    }

    override fun removeBean(bean: MutableBean<*>) {
        beans.remove(bean)
    }

    override fun <T : Any> getBean(beanClass: Class<T>, primary: Boolean?, key: Any?): MutableBean<T>? {
        return beans.firstOrNull {
            beanClass.isAssignableFrom(it.value::class.java)
                    && (primary == null || it.isPrimary == primary)
                    && (key == null || run {
                val beanKeys = it.keys
                beanKeys == null || beanKeys.contains(key)
            })
        } as MutableBean<T>?
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getBeans(beanClass: Class<T>): List<MutableBean<T>> {
        return beans.filter { beanClass.isAssignableFrom(it.value::class.java) } as List<MutableBean<T>>
    }

    override fun <T : Any> getBeanValues(beanClass: Class<T>): List<T> {
        return getBeans(beanClass).map { it.value }
    }
}