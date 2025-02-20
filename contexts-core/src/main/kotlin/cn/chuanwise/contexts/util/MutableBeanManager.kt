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

import java.util.LinkedList
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

val DEFAULT_BEAN_ID: String? = null
const val DEFAULT_BEAN_PRIMARY: Boolean = false

/**
 * 可变的对象容器。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface MutableBeanManager : BeanManager {
    fun <T> addBean(
        value: T, type: ResolvableType<T>,
        id: String? = DEFAULT_BEAN_ID,
        primary: Boolean = DEFAULT_BEAN_PRIMARY
    ): MutableBeanEntry<T>

    fun <T> addBeans(
        values: Iterable<T>, type: ResolvableType<T>,
        id: String? = DEFAULT_BEAN_ID,
        primary: Boolean = DEFAULT_BEAN_PRIMARY
    ): List<BeanEntry<T>> {
        return values.map { addBean(it, type, id, primary) }
    }
}

@ContextsInternalApi
abstract class AbstractMutableBeanManager : MutableBeanManager {
    private inner class MutableBeanEntryImpl<T>(
        override var value: T,
        override val type: ResolvableType<T>,
        override var id: String?,
        override var isPrimary: Boolean
    ) : MutableBeanEntry<T> {
        private var mutableIsRemoved = AtomicBoolean(false)
        override val isRemoved: Boolean get() = mutableIsRemoved.get()

        override fun tryRemove(): Boolean {
            val result = mutableIsRemoved.compareAndSet(false, true)
            if (result) {
                removeBean(this)
            }
            return result
        }
    }

    override fun <T> addBean(value: T, type: ResolvableType<T>, id: String?, primary: Boolean): MutableBeanEntry<T> {
        return MutableBeanEntryImpl(value, type, id, primary).also {
            registerBean(it)
        }
    }

    abstract fun registerBean(bean: MutableBeanEntry<*>)

    abstract fun removeBean(bean: MutableBeanEntry<*>)
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class MutableBeanManagerImpl: AbstractMutableBeanManager() {
    private val beanEntries = ConcurrentLinkedDeque<BeanEntry<*>>()

    override fun registerBean(bean: MutableBeanEntry<*>) {
        beanEntries.add(bean)
    }

    override fun removeBean(bean: MutableBeanEntry<*>) {
        beanEntries.remove(bean)
    }

    private fun BeanEntry<*>.isAssignableTo(type: ResolvableType<*>, id: String?, primary: Boolean?): Boolean {
        return type.isAssignableFrom(this.type) &&
                (id == null || id == this.id) &&
                (primary == null || primary == this.isPrimary)
    }

    private fun requireTypeResolved(beanType: ResolvableType<*>) {
        require(!beanType.isNothing) { "Type must not be Nothing." }
        require(beanType.isResolved) { "Type must be resolved." }
    }

    override fun <T> getBeanEntry(beanType: ResolvableType<T>, id: String?, primary: Boolean?): BeanEntry<T>? {
        requireTypeResolved(beanType)
        return beanEntries.singleOrNull { it.isAssignableTo(beanType, id, primary) } as BeanEntry<T>?
    }

    override fun <T> getBeanEntryOrFail(beanType: ResolvableType<T>, id: String?, primary: Boolean?): BeanEntry<T> {
        requireTypeResolved(beanType)
        val beanEntries = beanEntries.filter { it.isAssignableTo(beanType, id, primary) }
        if (beanEntries.isEmpty()) {
            throw NoSuchElementException("No such bean of type $beanType. (id: $id, primary: $primary)")
        } else if (beanEntries.size == 1) {
            return beanEntries.single() as BeanEntry<T>
        } else {
            throw NoSuchElementException("No unique bean of type $beanType, found ${beanEntries.size} beans. (id: $id, primary: null)")
        }
    }

    override fun <T> getBean(beanType: ResolvableType<T>, id: String?, primary: Boolean?): T? {
        requireTypeResolved(beanType)
        getBeanEntry(beanType, id, primary)?.value?.let { return it }
        if (id != null) {
            return null
        }

        if (beanType.isList) {
            val componentType = beanType.typeArguments.values.single().type ?: createResolvableType<Any?>()
            return getBeans(componentType, id, primary) as T
        }
        if (beanType.isSet) {
            val componentType = beanType.typeArguments.values.single().type ?: createResolvableType<Any?>()
            return getBeans(componentType, id, primary).toSet() as T
        }
        if (beanType.isQueue) {
            val componentType = beanType.typeArguments.values.single().type ?: createResolvableType<Any?>()
            return ArrayDeque<Any?>().apply { addAll(getBeans(componentType, id, primary)) } as T
        }
        if (beanType.isStack) {
            val componentType = beanType.typeArguments.values.single().type ?: createResolvableType<Any?>()
            return LinkedList<Any?>().apply { addAll(getBeans(componentType, id, primary)) } as T
        }
        if (beanType.isArray) {
            val componentType = beanType.typeArguments.values.single().type ?: createResolvableType<Any?>()
            return getBeans(componentType, id, primary).toTypedArray() as T
        }

        if (beanType.isMap) {
            val keyType = beanType.typeArguments.values.first().type ?: createResolvableType<String?>()
            if (keyType.isString) {
                val valueType = beanType.typeArguments.values.last().type ?: createResolvableType<Any?>()
                return getBeanEntries(valueType, id, primary).associateBy { it.id } as T
            }
        }
        return null
    }

    override fun <T> getBeanOrFail(beanType: ResolvableType<T>, id: String?, primary: Boolean?): T {
        return getBean(beanType, id, primary) ?: throw NoSuchElementException("No such bean of type $beanType. (id: $id, primary: $primary)")
    }

    override fun <T> getBeanEntries(beanType: ResolvableType<T>, id: String?, primary: Boolean?): List<BeanEntry<T>> {
        requireTypeResolved(beanType)
        return beanEntries.filter { it.isAssignableTo(beanType, id, primary) } as List<BeanEntry<T>>
    }

    override fun <T> getBeans(beanType: ResolvableType<T>, id: String?, primary: Boolean?): List<T> {
        requireTypeResolved(beanType)
        return getBeanEntries(beanType, id, primary).map { it.value }
    }
}