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

interface InheritedMutableBeanManager : MutableBeanManager {
    val parentBeanManagers: Iterable<BeanManager>
    val beanManager: MutableBeanManager
}

@ContextsInternalApi
abstract class AbstractInheritedMutableBeanManager(
    override val beanManager: AbstractMutableBeanManager = MutableBeanManagerImpl()
): AbstractMutableBeanManager(), InheritedMutableBeanManager {
    override fun registerBean(bean: MutableBeanEntry<*>) {
        beanManager.registerBean(bean)
    }

    override fun removeBean(bean: MutableBeanEntry<*>) {
        beanManager.removeBean(bean)
    }

    override fun <T> getBeanEntry(beanType: ResolvableType<T>, id: String?, primary: Boolean?): BeanEntry<T>? {
        beanManager.getBeanEntry(beanType, id, primary)?.let { return it }
        parentBeanManagers.forEach { parent ->
            parent.getBeanEntry(beanType, id, primary)?.let { return it }
        }
        return null
    }

    override fun <T> getBeanEntryOrFail(beanType: ResolvableType<T>, id: String?, primary: Boolean?): BeanEntry<T> {
        return getBeanEntry(beanType, id, primary) ?: throw NoSuchElementException("No such bean entry for type $beanType")
    }

    override fun <T> getBean(beanType: ResolvableType<T>, id: String?, primary: Boolean?): T? {
        beanManager.getBean(beanType, id, primary)?.let { return it }
        parentBeanManagers.forEach { parent ->
            parent.getBean(beanType, id, primary)?.let { return it }
        }
        return null
    }

    override fun <T> getBeanOrFail(beanType: ResolvableType<T>, id: String?, primary: Boolean?): T {
        return getBean(beanType, id, primary) ?: throw NoSuchElementException("No such bean for type $beanType")
    }

    override fun <T> getBeanEntries(beanType: ResolvableType<T>, id: String?, primary: Boolean?): List<BeanEntry<T>> {
        val result = beanManager.getBeanEntries(beanType, id, primary).toMutableList()
        parentBeanManagers.forEach { parent ->
            result += parent.getBeanEntries(beanType, id, primary)
        }
        return result
    }

    override fun <T> getBeans(beanType: ResolvableType<T>, id: String?, primary: Boolean?): List<T> {
        val result = beanManager.getBeans(beanType, id, primary).toMutableList()
        parentBeanManagers.forEach { parent ->
            result += parent.getBeans(beanType, id, primary)
        }
        return result
    }
}

@ContextsInternalApi
class InheritedMutableBeanManagerImpl(
    override val parentBeanManagers: Iterable<BeanManager>,
    override val beanManager: AbstractMutableBeanManager = MutableBeanManagerImpl()
): AbstractInheritedMutableBeanManager(beanManager) {
    constructor(parentBeanManager: BeanManager) : this(listOf(parentBeanManager))
    constructor(vararg parentBeanManagers: BeanManager) : this(parentBeanManagers.toList())
}