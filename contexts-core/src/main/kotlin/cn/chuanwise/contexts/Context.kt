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

import cn.chuanwise.contexts.util.Bean
import cn.chuanwise.contexts.util.Beans
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeans
import cn.chuanwise.contexts.util.MutableBean
import cn.chuanwise.contexts.util.MutableBeans
import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.contexts.util.ReadWriteLockBasedReadAddRemoveLock
import cn.chuanwise.contexts.util.add
import cn.chuanwise.contexts.util.createAllChildrenBreadthFirstSearchIterator
import cn.chuanwise.contexts.util.createAllParentsBreadthFirstSearchIterator
import cn.chuanwise.contexts.util.createChildToParentTopologicalSortingIterator
import cn.chuanwise.contexts.util.read
import cn.chuanwise.contexts.util.remove
import cn.chuanwise.contexts.util.withLocks
import java.lang.reflect.Type
import java.util.NoSuchElementException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

@NotStableForInheritance
interface Context : MutableBeans, AutoCloseable {
    val key: Any?
    val contextManager: ContextManager

    /**
     * 若当前上下文只有一个父上下文时，可以用此获取其值。
     */
    val parent: Context
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

    fun getChildByKey(key: Any): Context?
    fun getChildByKeyOrFail(key: Any): Context {
        return getChildByKey(key) ?: throw NoSuchElementException("Cannot find child with key $key.")
    }

    fun getParentByKey(key: Any): Context?
    fun getParentByKeyOrFail(key: Any): Context {
        return getParentByKey(key) ?: throw NoSuchElementException("Cannot find parent with key $key.")
    }


    /**
     * 尝试获取一个带有指定对象的子上下文。
     *
     * @param bean 对象
     * @param key 键
     * @return 子上下文
     */
    fun getChildByBean(bean: Any, key: Any? = null): Context?
    fun getChildByBeanOrFail(bean: Any, key: Any? = null): Context {
        return getChildByBean(bean, key) ?: throw NoSuchElementException("Cannot find child with bean $bean and key $key.")
    }

    fun getParentByBean(bean: Any, key: Any? = null): Context?
    fun getParentByBeanOrFail(bean: Any, key: Any? = null): Context {
        return getParentByBean(bean, key) ?: throw NoSuchElementException("Cannot find parent with bean $bean and key $key.")
    }

    /**
     * 进入一个子上下文。
     *
     * @param key 上下文键。
     * @param child 子上下文对象。
     * @param replace 是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(child: Any, key: Any, replace: Boolean = false): Context?
    fun enterChild(child: Any, key: Any): Context
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
    fun enterChild(vararg child: Any, key: Any): Context
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
    fun enterChild(child: Collection<Any>, key: Any): Context
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

    /**
     * 检查是否已经退出。
     */
    val isExited: Boolean

    override fun close() = exit()
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

    override fun enterChild(child: Any, key: Any): Context {
        return enterChild(listOf(child), key, replace = false)!!
    }

    override fun enterChild(vararg child: Any, key: Any): Context {
        return enterChild(child.toList(), key, replace = false)!!
    }
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class ContextImpl(
    override val contextManager: ContextManagerImpl,
    override val key: Any?,
) : AbstractContext() {
    private inner class AllParentIterable : Iterable<Beans> {
        override fun iterator(): Iterator<Beans> {
            return sequence {
                yieldAll(allParents.createChildToParentTopologicalSortingIterator())
                yield(contextManager)
            }.iterator()
        }
    }
    private val beans: InheritedMutableBeans = InheritedMutableBeans(AllParentIterable())
    private val lock = ReadWriteLockBasedReadAddRemoveLock()

    private val mutableParents: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableParentsByKey: MutableMap<Any, ContextImpl> = mutableMapOf()

    override val parent: Context get() = lock.read { mutableParents.single() }
    override val parents: List<Context> get() = lock.read { mutableParents.toList() }
    override val allParents: List<Context> get() = createAllParentsBreadthFirstSearchIterator().asSequence().toList()
    override val parentCount: Int get() = lock.read { mutableParents.size }

    private val mutableChildren: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableChildrenByKey: MutableMap<Any, ContextImpl> = mutableMapOf()

    override val children: List<Context> get() = lock.read { mutableChildren.toList() }
    override val allChildren: List<Context> get() = createAllChildrenBreadthFirstSearchIterator().asSequence().toList()
    override val childCount: Int get() = lock.read { mutableChildren.size }

    override val components: List<Context> get() = allParents + allChildren + this

    override val contextBeanValues: List<*> get() = beans.beans.getBeanValues(Any::class.java)
    override val contextBeans: List<MutableBean<*>> get() = beans.beans.getBeans(Any::class.java) as List<MutableBean<Any>>

    private val exitLock = AtomicBoolean(false)
    override val isExited: Boolean get() = exitLock.get()

    private val contextBean = registerBean(this)

    override fun getChildByKey(key: Any): Context? = lock.read {
        mutableChildrenByKey[key]
    }

    override fun getParentByKey(key: Any): Context? = lock.read {
        mutableParentsByKey[key]
    }

    override fun getParentByBean(bean: Any, key: Any?): Context? {
        for (parent in createAllParentsBreadthFirstSearchIterator()) {
            val beanValue = parent.getBean(bean::class.java, key = key)?.value ?: continue
            if (beanValue == bean) {
                return parent
            }
        }
        return null
    }

    override fun getChildByBean(bean: Any, key: Any?): Context? = lock.read {
        for (child in createAllChildrenBreadthFirstSearchIterator()) {
            val beanValue = child.getBean(bean::class.java, key = key)?.value ?: continue
            if (beanValue == bean) {
                return child
            }
        }
        null
    }

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

            // 它可能为 null 的原因是 alsoExitChildIfItWillBeRoot，
            // 使得 PreRemove 事件发出后可能子已经退出。
            val removedChild = parent.mutableChildrenByKey.remove(key)
            check(removedChild === child || removedChild == null)

            val removedParent = child.mutableParentsByKey.remove(key)
            check(removedParent === parent || removedParent == null)
        } else {
            val contextPreRemoveEvent = ContextPreRemoveEventImpl(parent, child, null, exit, contextManager)
            contextManager.onContextPreRemove(contextPreRemoveEvent)
        }

        parent.mutableChildren.remove(child) && child.mutableParents.remove(parent)

        val contextPostRemoveEvent = ContextPostRemoveEventImpl(parent, child, null, exit, contextManager)
        contextManager.onContextPostRemove(contextPostRemoveEvent)

        return true
    }

    private fun doEnterChild(child: Collection<Any>, key: Any?, replace: Boolean): Context? {
        checkNotExited()
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

    override fun enterChild(child: Collection<Any>, key: Any): Context {
        return doEnterChild(child, key, replace = false)!!
    }

    @Suppress("UNCHECKED_CAST")
    override fun addChild(child: Context, replace: Boolean): Boolean {
        require(child is ContextImpl) { "Only ContextImpl is supported." }
        checkNotExited()
        child.checkNotExited()

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
        checkNotExited()

        return listOf(lock, child.lock).remove {
            removeNoLock(this, child, exit = false)
        }
    }

    override fun addParent(parent: Context, replace: Boolean): Boolean = parent.addChild(this, replace)

    override fun removeParent(parent: Context): Boolean = parent.removeChild(this)

    override fun exit() {
        if (exitLock.compareAndSet(false, true)) {
            // 不加 addLock 的原因是在 exitLock = true 的时候其他线程不可能对他 add。

            val contextPreExitEvent = ContextPreExitEventImpl(this, contextManager)
            contextManager.onContextPreExit(contextPreExitEvent)

            val parents = parents
            val children = children

            for (parent in parents) {
                parent as ContextImpl

                listOf(lock, parent.lock).remove {
                    removeNoLock(parent, this, exit = true)
                }
            }
            for (child in children) {
                child as ContextImpl

                listOf(lock, child.lock).remove {
                    removeNoLock(this, child, exit = true)
                }
            }

            val contextPostExitEvent = ContextPostExitEventImpl(this, contextManager)
            contextManager.onContextPostExit(contextPostExitEvent)
        }
    }

    override fun getBean(type: Type, primary: Boolean?, key: Any?): Bean<*>? {
        return beans.getBean(type, primary, key)
    }

    override fun <T : Any> getBean(beanClass: Class<T>, primary: Boolean?, key: Any?): Bean<T>? {
        return beans.getBean(beanClass, primary, key)
    }

    override fun getBeanOrFail(type: Type, primary: Boolean?, key: Any?): Bean<*> {
        return beans.getBeanOrFail(type, primary, key)
    }

    override fun <T : Any> getBeanOrFail(beanClass: Class<T>, primary: Boolean?, key: Any?): Bean<T> {
        return beans.getBeanOrFail(beanClass, primary, key)
    }

    override fun getBeans(type: Type): List<Bean<*>> {
        return beans.getBeans(type)
    }

    override fun <T : Any> getBeans(beanClass: Class<T>): List<Bean<T>> {
        return beans.getBeans(beanClass)
    }

    override fun <T : Any> registerBean(value: T, keys: MutableSet<Any>, primary: Boolean): MutableBean<T> {
        return beans.registerBean(value, keys, primary)
    }

    override fun <T : Any> registerBean(value: T, key: Any, primary: Boolean): MutableBean<T> {
        return beans.registerBean(value, key, primary)
    }

    override fun <T : Any> registerBean(value: T, primary: Boolean): MutableBean<T> {
        return beans.registerBean(value, primary)
    }

    override fun <T : Any> registerBeanGetter(
        keys: MutableSet<Any>?,
        primary: Boolean,
        factory: Supplier<T>
    ): MutableBean<T> {
        return beans.registerBeanGetter(keys, primary, factory)
    }

    override fun <T : Any> registerLazyBean(
        keys: MutableSet<Any>?,
        primary: Boolean,
        factory: Supplier<T>
    ): MutableBean<T> {
        return beans.registerLazyBean(keys, primary, factory)
    }

    override fun getBeanValue(type: Type, primary: Boolean?, key: Any?): Any? {
        return beans.getBeanValue(type, primary, key)
    }

    override fun <T : Any> getBeanValue(beanClass: Class<T>, primary: Boolean?, key: Any?): T? {
        return beans.getBeanValue(beanClass, primary, key)
    }

    override fun getBeanValueOrFail(type: Type, primary: Boolean?, key: Any?): Any {
        return beans.getBeanValueOrFail(type, primary, key)
    }

    override fun <T : Any> getBeanValueOrFail(beanClass: Class<T>, primary: Boolean?, key: Any?): T {
        return beans.getBeanValueOrFail(beanClass, primary, key)
    }

    override fun getBeanValues(type: Type): List<*> {
        return beans.getBeanValues(type)
    }

    override fun <T : Any> getBeanValues(beanClass: Class<T>): List<T> {
        return beans.getBeanValues(beanClass)
    }

    override fun toString(): String {
        return "Context(key=$key, hash=${hashCode()}, exit=$isExited)"
    }

    private fun checkNotExited() {
        check(!isExited) { "Context is exited." }
    }
}