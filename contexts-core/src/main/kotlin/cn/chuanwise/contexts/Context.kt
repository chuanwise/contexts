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

package cn.chuanwise.contexts

import cn.chuanwise.contexts.events.ContextPostAddEventImpl
import cn.chuanwise.contexts.events.ContextPostEnterEventImpl
import cn.chuanwise.contexts.events.ContextPostExitEventImpl
import cn.chuanwise.contexts.events.ContextPostRemoveEventImpl
import cn.chuanwise.contexts.events.ContextPreAddEventImpl
import cn.chuanwise.contexts.events.ContextPreEnterEventImpl
import cn.chuanwise.contexts.events.ContextPreExitEventImpl
import cn.chuanwise.contexts.events.ContextPreRemoveEventImpl
import cn.chuanwise.contexts.util.Bean
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableBean
import cn.chuanwise.contexts.util.MutableBeanImpl
import cn.chuanwise.contexts.util.MutableBeans
import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.contexts.util.ReadAddRemoveLock
import cn.chuanwise.contexts.util.ReadWriteLockBasedReadAddRemoveLock
import cn.chuanwise.contexts.util.add
import cn.chuanwise.contexts.util.createAllChildrenBreadthFirstSearchIterator
import cn.chuanwise.contexts.util.createAllParentsBreadthFirstSearchIterator
import cn.chuanwise.contexts.util.createChildToParentTopologicalSortingIterator
import cn.chuanwise.contexts.util.read
import cn.chuanwise.contexts.util.remove

@NotStableForInheritance
interface Context : MutableBeans {
    val key: Any?
    val contextManager: ContextManager

    val parents: List<Context>
    val allParents: List<Context>
    val parentCount: Int

    val children: List<Context>
    val allChildren: List<Context>
    val childCount: Int

    val contextBeans: List<MutableBean<*>>
    val contextBeanValues: List<*>

    /**
     * 联通分量里的所有上下文，即包括自己和所有父子上下文。
     */
    val components: List<Context>

    /**
     * 进入一个子上下文。
     *
     * @param key 上下文键。
     * @param child 子上下文对象。
     * @param replace 是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(child: Any, key: Any, replace: Boolean = false): Context?
    fun enterChild(child: Any): Context

    /**
     * 进入一个子上下文。
     *
     * @param key 上下文键。
     * @param child 子上下文对象。
     * @param replace 是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(vararg child: Any, key: Any, replace: Boolean = false): Context?
    fun enterChild(vararg child: Any): Context

    /**
     * 进入一个子上下文。
     *
     * @param key 上下文键。
     * @param child 子上下文对象。
     * @param replace 是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(child: Collection<Any>, key: Any, replace: Boolean = false): Context?
    fun enterChild(child: Collection<Any>): Context

    /**
     * 添加一个子上下文。
     *
     * @param child 子上下文。
     * @param replace 是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun addChild(child: Context, replace: Boolean = false): Boolean

    /**
     * 连接一个父上下文。
     *
     * @param parent 父上下文。
     * @param replace 是否替换已有的同键父上下文。
     * @return 是否成功连接。
     */
    fun addParent(parent: Context, replace: Boolean = false): Boolean

    /**
     * 移除一个子上下文。
     *
     * @param child 子上下文。
     * @return 是否成功移除。
     */
    fun removeChild(child: Context): Boolean

    /**
     * 移除一个父上下文。
     *
     * @param parent 父上下文。
     * @return 是否成功移除。
     */
    fun removeParent(parent: Context): Boolean

    /**
     * 退出并销毁上下文。
     *
     * @author Chuanwise
     */
    fun exit()
}

@ContextsInternalApi
abstract class AbstractContext : Context {
    override fun enterChild(child: Any, key: Any, replace: Boolean): Context? {
        return enterChild(listOf(child), key, replace)
    }

    override fun enterChild(vararg child: Any, key: Any, replace: Boolean): Context? {
        return enterChild(child.toList(), key, replace)
    }

    override fun enterChild(child: Any): Context {
        return enterChild(listOf(child))
    }

    override fun enterChild(vararg child: Any): Context {
        return enterChild(child.toList())
    }
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class ContextImpl(
    override val contextManager: ContextManagerImpl,
    override val key: Any?,
    private val beans: MutableBeans = MutableBeanImpl()
) : AbstractContext(), MutableBeans by beans {
    private val lock = ReadWriteLockBasedReadAddRemoveLock()

    private val mutableParents: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableParentsByKey: MutableMap<Any, ContextImpl> = mutableMapOf()

    override val parents: List<Context> get() = lock.read { mutableParents.toList() }
    override val allParents: List<Context> get() = createAllParentsBreadthFirstSearchIterator().asSequence().toList()
    override val parentCount: Int get() = lock.read { mutableParents.size }

    private val mutableChildren: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableChildrenByKey: MutableMap<Any, ContextImpl> = mutableMapOf()

    override val children: List<Context> get() = lock.read { mutableChildren.toList() }
    override val allChildren: List<Context> get() = createAllChildrenBreadthFirstSearchIterator().asSequence().toList()
    override val childCount: Int get() = lock.read { mutableChildren.size }

    override val components: List<Context> get() = allParents + allChildren + this

    override val contextBeanValues: List<*> get() = beans.getBeanValues(Any::class.java)
    override val contextBeans: List<MutableBean<*>> get() = beans.getBeans(Any::class.java) as List<MutableBean<Any>>

    private val contextBean = registerBean(this)

    // 连接两个上下文，不检查是否会成环，不加锁，会通知 context modules: PreAdd, PostAdd。
    private fun addNoLock(parent: ContextImpl, child: ContextImpl, replace: Boolean, enter: Boolean): Boolean {
        val key = child.key
        val contextPreAddEvent = ContextPreAddEventImpl(parent, child, enter, contextManager)
        if (key != null) {
            if (replace) {
                val oldChild = parent.mutableChildrenByKey[key]
                if (oldChild != null) {
                    // 如果已经是子上下文了，直接返回。
                    if (oldChild === child) {
                        return false
                    }

                    // 否则需要先退出原来的子上下文。
                    val contextPreRemoveEvent = ContextPreRemoveEventImpl(
                        parent, child, contextPreAddEvent, exit = false, contextManager
                    )
                    contextManager.onContextPreRemove(contextPreRemoveEvent)

                    check(parent.mutableChildren.remove(oldChild))
                    check(parent.mutableChildrenByKey.remove(key) === oldChild)

                    check(oldChild.mutableParents.remove(parent))
                    check(oldChild.mutableParentsByKey.remove(key) === parent)

                    val contextPostRemoveEvent = ContextPostRemoveEventImpl(
                        parent, child, contextPreAddEvent, exit = false, contextManager
                    )
                    contextManager.onContextPostRemove(contextPostRemoveEvent)
                }

                val oldParent = child.mutableParentsByKey[key]
                if (oldParent != null) {
                    // 如果它们之间有父子关系，在前面就已经退出了。
                    // 所以此处必定是同键的两个上下文，但是不是父子关系。
                    check(oldParent !== parent)

                    val contextPreRemoveEvent = ContextPreRemoveEventImpl(
                        parent, child, contextPreAddEvent, exit = false, contextManager
                    )
                    contextManager.onContextPreRemove(contextPreRemoveEvent)

                    check(child.mutableParents.remove(oldParent))
                    check(child.mutableParentsByKey.remove(key) === oldParent)

                    check(oldParent.mutableChildren.remove(child))
                    check(oldParent.mutableChildrenByKey.remove(key) === child)

                    val contextPostRemoveEvent = ContextPostRemoveEventImpl(
                        parent, child, contextPreAddEvent, exit = false, contextManager
                    )
                    contextManager.onContextPostRemove(contextPostRemoveEvent)
                }
            } else {
                val oldChild = parent.mutableChildrenByKey[key]
                if (oldChild != null) {
                    return false
                }

                val oldParent = child.mutableParentsByKey[key]
                if (oldParent != null) {
                    return false
                }
            }

            contextManager.onContextPreAdd(contextPreAddEvent)

            parent.mutableChildrenByKey[key] = child
            child.mutableParentsByKey[key] = parent
        } else {
            contextManager.onContextPreAdd(contextPreAddEvent)
        }

        parent.mutableChildren.add(child)
        child.mutableParents.add(parent)

        val contextPostAddEvent = ContextPostAddEventImpl(parent, child, enter, contextManager)
        contextManager.onContextPostAdd(contextPostAddEvent)
        return true
    }

    // 退出一个子上下文，不加锁，会通知 context modules: PreRemove, PostRemove。
    private fun removeNoLock(parent: ContextImpl, child: ContextImpl, exit: Boolean): Boolean {
        val key = child.key
        if (key != null) {
            val oldChild = parent.mutableChildrenByKey[key]

            // 如果它们本来就没有父子关系，则退出。
            if (oldChild !== child) {
                return false
            }

            val contextPreRemoveEvent = ContextPreRemoveEventImpl(parent, child, null, exit, contextManager)
            contextManager.onContextPreRemove(contextPreRemoveEvent)

            check(parent.mutableChildrenByKey.remove(key) === child)
            check(child.mutableParentsByKey.remove(key) === parent)
        } else {
            val contextPreRemoveEvent = ContextPreRemoveEventImpl(parent, child, null, exit, contextManager)
            contextManager.onContextPreRemove(contextPreRemoveEvent)
        }

        val removed = parent.mutableChildren.remove(child) && child.mutableParents.remove(parent)
        if (removed) {
            val contextPostRemoveEvent = ContextPostRemoveEventImpl(parent, child, null, exit, contextManager)
            contextManager.onContextPostRemove(contextPostRemoveEvent)
        }

        return removed
    }

    private fun doEnterChild(child: Collection<Any>, key: Any?, replace: Boolean): Context? {
        val context = ContextImpl(contextManager, key).apply {
            registerBeans(child)
        }

        listOf(lock, context.lock).add {
            val contextPreEnterEvent = ContextPreEnterEventImpl(context, contextManager)
            contextManager.onContextPreEnter(contextPreEnterEvent)

            val result = addNoLock(this, context, replace, enter = true)
            if (result) {
                val contextPostEnterEvent = ContextPostEnterEventImpl(context, contextManager)
                contextManager.onContextPostEnter(contextPostEnterEvent)
            } else {
                return null
            }
        }

        return context
    }

    override fun enterChild(child: Collection<Any>): Context {
        return doEnterChild(child, null, replace = false)!!
    }

    override fun enterChild(child: Collection<Any>, key: Any, replace: Boolean): Context? {
        return doEnterChild(child, key, replace)
    }

    @Suppress("UNCHECKED_CAST")
    override fun addChild(child: Context, replace: Boolean): Boolean {
        require(child is ContextImpl) { "Only ContextImpl is supported." }
        return lock.add {
            // 检查是否会形成环。
            val allParents = allParents as List<ContextImpl>
            require(child !in allParents) { "Cannot connect child to parent, because it will form a cycle." }

            // 获取整个连通子图的锁
            val components = (components + child.components) as List<ContextImpl>
            val componentLocks = components.map { it.lock }

            componentLocks.read {
                val contextPreAddEvent = ContextPreAddEventImpl(this, child, isEntering = false, contextManager)
                contextManager.onContextPreAdd(contextPreAddEvent)

                val result = addNoLock(this, child, enter = false, replace = replace)
                if (result) {
                    val contextPostAddEvent = ContextPostAddEventImpl(this, child, isEntering = false, contextManager)
                    contextManager.onContextPostAdd(contextPostAddEvent)
                }

                result
            }
        }
    }

    override fun removeChild(child: Context): Boolean {
        require(child is ContextImpl) { "Only ContextImpl is supported." }

        return listOf(lock, child.lock).remove {
            removeNoLock(this, child, exit = false)
        }
    }

    override fun addParent(parent: Context, replace: Boolean): Boolean = parent.addChild(this, replace)

    override fun removeParent(parent: Context): Boolean = parent.removeChild(this)

    override fun exit() {
        // 加 add lock 的原因是避免其他人对上下文进行操作。
        return lock.add {
            val contextPreExitEvent = ContextPreExitEventImpl(this, contextManager)
            contextManager.onContextPreExit(contextPreExitEvent)

            val parents = parents
            val children = children

            for (parent in parents) {
                parent as ContextImpl

                parent.lock.remove {
                    removeNoLock(parent, this, exit = true)
                }
            }
            for (child in children) {
                child as ContextImpl

                child.lock.remove {
                    removeNoLock(this, child, exit = true)
                }
            }

            val contextPostExitEvent = ContextPostExitEventImpl(this, contextManager)
            contextManager.onContextPostExit(contextPostExitEvent)
        }
    }

    override fun <T : Any> getBean(beanClass: Class<T>, primary: Boolean?, key: Any?): Bean<T>? {
        beans.getBean(beanClass, primary, key)?.let { return it }
        for (context in allParents.createChildToParentTopologicalSortingIterator()) {
            context as ContextImpl
            context.beans.getBean(beanClass, primary, key)?.let { return it }
        }
        return contextManager.beans.getBean(beanClass, primary, key)
    }

    override fun <T : Any> getBeans(beanClass: Class<T>): List<Bean<T>> {
        return beans.getBeans(beanClass) +
                allParents.flatMap { it.contextBeans } as List<Bean<T>> +
                contextManager.beans.getBeans(beanClass)
    }

    override fun <T : Any> getBeanValues(beanClass: Class<T>): List<T> {
        return beans.getBeanValues(beanClass) +
                allParents.flatMap { it.contextBeanValues } as List<T> +
                contextManager.beans.getBeanValues(beanClass)
    }

    override fun toString(): String {
        return "Context(key=$key, hash=${hashCode()})"
    }
}