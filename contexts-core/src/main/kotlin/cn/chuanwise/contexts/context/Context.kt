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

package cn.chuanwise.contexts.context

import cn.chuanwise.contexts.util.Bean
import cn.chuanwise.contexts.util.BeanFactory
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeanFactory
import cn.chuanwise.contexts.util.MutableBean
import cn.chuanwise.contexts.util.MutableBeanFactory
import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.contexts.util.ReadWriteLockBasedReadAddRemoveLock
import cn.chuanwise.contexts.util.add
import cn.chuanwise.contexts.util.createAllChildrenBreadthFirstSearchIterator
import cn.chuanwise.contexts.util.createAllParentsBreadthFirstSearchIterator
import cn.chuanwise.contexts.util.read
import cn.chuanwise.contexts.util.remove
import cn.chuanwise.contexts.util.withLocks
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicReference

/**
 * 上下文，是一个存储数据和函数的、具有生命周期的对象。
 *
 * 上下文通常和具体的实体关联在一起，并绑定和这些实体相关的对象（如监听器），
 * 以实现各种对象生命周期的自动管理。例如：
 *
 * 1. 表示一个系统用户的上下文，其将在用户上线时进入，下线时退出。
 * 2. 表示一个协作编辑会话，其将在协作开始时进入，结束时退出。每一个协作用户的上下文都是其子上下文。
 *
 * 通过在进入上下文时创建资源，在退出上下文时销毁资源，可以实现资源的自动管理。
 *
 * ## 生命周期
 *
 * 上下文的生命周期有三个阶段：
 *
 * 1. 初始化：对应的事件是 [ContextInitEvent]，**只有**模块可以监听此事件。只能注册上下文对象，
 *    其他操作将会抛出异常。
 * 2. 进入：对应的事件是 [ContextPreEnterEvent] 和 [ContextPostEnterEvent]。上下文对象可以收到这些事件。
 * 3. 退出：对应的事件是 [ContextPreExitEvent] 和 [ContextPostExitEvent]。上下文对象可以收到这些事件。
 *
 * **注意**：如果通过 [enterChild] 进入子上下文，那么在进入其之前，上下文还没有将其和父上下文连接，
 * 故如果监听 [ContextPreEnterEvent]，不能获取父上下文中的对象，否则会执行时出现异常。类似地，如果在
 * [ContextPostExitEvent] 中获取父上下文中的对象，也会出现异常，因为那时上下文的父子关系全部被解除。
 * 因此一般建议监听 [ContextPostEnterEvent] 和 [ContextPreExitEvent]。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface Context : MutableBeanFactory, AutoCloseable {
    val key: Any?
    val contextManager: ContextManager

    val isInitialized: Boolean
    val isEntered: Boolean
    val isExited: Boolean

    val parents: List<Context>
    val parentCount: Int

    val singleParent: Context
    val singleParentOrNull: Context?

    val allParents: List<Context>
    val allParentCount: Int

    val singleAllParent: Context
    val singleAllParentOrNull: Context?

    val children: List<Context>
    val childCount: Int

    val singleChild: Context
    val singleChildOrNull: Context?

    val allChildren: List<Context>
    val allChildCount: Int

    val singleAllChild: Context
    val singleAllChildOrNull: Context?

    val contextBeans: List<MutableBean<*>>
    val contextBeanValues: List<*>

    fun inAllParents(context: Context): Boolean
    fun inAllChildren(context: Context): Boolean

    fun inParents(context: Context): Boolean
    fun inChildren(context: Context): Boolean

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
    fun getChildByBean(bean: Any, key: Any? = null, primary: Boolean? = null): Context?
    fun getChildByBeanOrFail(bean: Any, key: Any? = null, primary: Boolean? = null): Context {
        return getChildByBean(bean, key, primary) ?: throw NoSuchElementException("Cannot find child with bean $bean and key $key.")
    }

    fun getChildByBeanClass(beanClass: Class<*>, key: Any? = null, primary: Boolean? = null): Context?
    fun getChildByBeanClassOrFail(beanClass: Class<*>, key: Any? = null, primary: Boolean? = null): Context {
        return getChildByBeanClass(beanClass, key, primary) ?: throw NoSuchElementException("Cannot find child with bean class $beanClass and key $key.")
    }

    fun getParentByBean(bean: Any, key: Any? = null, primary: Boolean? = null): Context?
    fun getParentByBeanOrFail(bean: Any, key: Any? = null, primary: Boolean? = null): Context {
        return getParentByBean(bean, key, primary) ?: throw NoSuchElementException("Cannot find parent with bean $bean and key $key.")
    }

    fun getParentByBeanClass(beanClass: Class<*>, key: Any? = null, primary: Boolean? = null): Context?
    fun getParentByBeanClassOrFail(beanClass: Class<*>, key: Any? = null, primary: Boolean? = null): Context {
        return getParentByBeanClass(beanClass, key, primary) ?: throw NoSuchElementException("Cannot find parent with bean class $beanClass and key $key.")
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
    fun enterChild(child: Iterable<Any>, key: Any, replace: Boolean = false): Context?
    fun enterChild(child: Iterable<Any>, key: Any): Context
    fun enterChild(child: Iterable<Any>): Context

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
        return enterChild(listOf(child), key)
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
    private inner class AllParentIterable : Iterable<BeanFactory> {
        override fun iterator(): Iterator<BeanFactory> {
            return sequence {
                yieldAll(createChildToParentTopologicalIteratorInAllParents())
                yield(contextManager)
            }.iterator()
        }
    }
    private val beans: InheritedMutableBeanFactory = InheritedMutableBeanFactory(AllParentIterable())
    private val lock = ReadWriteLockBasedReadAddRemoveLock()

    private val mutableParents: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableParentsByKey: MutableMap<Any, ContextImpl> = mutableMapOf()

    override val parents: List<Context> get() = lock.read { mutableParents.toList() }
    override val parentCount: Int get() = lock.read { mutableParents.size }

    override val singleParent: Context get() = lock.read { mutableParents.single() }
    override val singleParentOrNull: Context? get() = lock.read { mutableParents.singleOrNull() }

    override fun inParents(context: Context): Boolean {
        return lock.read { context in mutableParents }
    }

    override val allParents: List<Context> get() = createAllParentsBreadthFirstSearchIterator().asSequence().toList()
    override val allParentCount: Int get() = lock.read { mutableParents.size }

    override val singleAllParent: Context get() = lock.read { allParents.single() }
    override val singleAllParentOrNull: Context? get() = lock.read { allParents.singleOrNull() }

    override fun inAllParents(context: Context): Boolean {
        return context in allParents
    }

    private val mutableChildren: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableChildrenByKey: MutableMap<Any, ContextImpl> = mutableMapOf()

    override val children: List<Context> get() = lock.read { mutableChildren.toList() }
    override val childCount: Int get() = lock.read { mutableChildren.size }

    override val singleChild: Context get() = lock.read { mutableChildren.single() }
    override val singleChildOrNull: Context? get() = lock.read { mutableChildren.singleOrNull() }

    override fun inChildren(context: Context): Boolean {
        return lock.read { context in mutableChildren }
    }

    override val allChildren: List<Context> get() = createAllChildrenBreadthFirstSearchIterator().asSequence().toList()
    override val allChildCount: Int get() = lock.read { mutableChildren.size }

    override val singleAllChild: Context get() = lock.read { allChildren.single() }
    override val singleAllChildOrNull: Context? get() = lock.read { allChildren.singleOrNull() }

    override fun inAllChildren(context: Context): Boolean {
        return context in allChildren
    }

    override val components: List<Context> get() = allParents + allChildren + this

    override val contextBeanValues: List<*> get() = beans.beans.getBeanValues(Any::class.java)
    override val contextBeans: List<MutableBean<*>> get() = beans.beans.getBeans(Any::class.java) as List<MutableBean<Any>>

    private enum class State {
        INITIALIZED, ENTERED, EXITED
    }

    private val state = AtomicReference(State.INITIALIZED)
    override val isExited: Boolean get() = state.get() == State.EXITED
    override val isInitialized: Boolean get() = state.get() == State.INITIALIZED
    override val isEntered: Boolean get() = state.get() == State.ENTERED

    private val contextBean = addBean(this)

    override fun getChildByKey(key: Any): Context? = lock.read {
        mutableChildrenByKey[key]
    }

    override fun getParentByKey(key: Any): Context? = lock.read {
        mutableParentsByKey[key]
    }

    override fun getParentByBean(bean: Any, key: Any?, primary: Boolean?): Context? {
        for (parent in createAllParentsBreadthFirstSearchIterator()) {
            val beanValue = parent.getBean(bean::class.java, key = key)?.value ?: continue
            if (beanValue == bean) {
                return parent
            }
        }
        return null
    }

    override fun getChildByBean(bean: Any, key: Any?, primary: Boolean?): Context? = lock.read {
        for (child in createAllChildrenBreadthFirstSearchIterator()) {
            val beanValue = child.getBean(bean::class.java, key = key, primary = primary)?.value ?: continue
            if (beanValue == bean) {
                return child
            }
        }
        null
    }

    override fun getParentByBeanClass(beanClass: Class<*>, key: Any?, primary: Boolean?): Context? {
        for (parent in createAllParentsBreadthFirstSearchIterator()) {
            parent.getBean(beanClass, key = key, primary = primary) ?: continue
            return parent
        }
        return null
    }

    override fun getChildByBeanClass(beanClass: Class<*>, key: Any?, primary: Boolean?): Context? = lock.read {
        for (child in createAllChildrenBreadthFirstSearchIterator()) {
            child.getBean(beanClass, key = key, primary = primary) ?: continue
            return child
        }
        null
    }

    // 连接两个上下文，不检查是否会成环，不加锁，会通知 context modules: PreAdd, PostAdd。
    // 如果 check = true，先确保不是父子关系。否则信任调用者。
    private fun addNoLock(parent: ContextImpl, child: ContextImpl, replace: Boolean, enter: Boolean, check: Boolean = true): Boolean {
        val childKey = child.key
        val parentKey = parent.key

        if (check && parent.inChildren(child)) {
            return false
        }

        val contextPreAddEvent = ContextPreEdgeAddEventImpl(parent, child, enter, contextManager)
        if (childKey != null || parentKey != null) {
            if (replace) {
                val oldChild = childKey?.let { parent.mutableChildrenByKey[it] }
                if (oldChild != null) {
                    check(removeNoLock(parent, oldChild, exit = false, check = false))
                }

                val oldParent = parentKey?.let { child.mutableParentsByKey[it] }
                if (oldParent != null) {
                    check(removeNoLock(oldParent, child, exit = false, check = false))
                }
            } else {
                val oldChild = parent.mutableChildrenByKey[childKey]
                if (oldChild != null) {
                    return false
                }

                val oldParent = child.mutableParentsByKey[childKey]
                if (oldParent != null) {
                    return false
                }
            }

            contextManager.onContextPreAdd(contextPreAddEvent)

            childKey?.let { parent.mutableChildrenByKey[childKey] = child }
            parentKey?.let { child.mutableParentsByKey[parentKey] = parent }
        } else {
            contextManager.onContextPreAdd(contextPreAddEvent)
        }

        parent.mutableChildren.add(child)
        child.mutableParents.add(parent)

        val contextPostAddEvent = ContextPostEdgeAddEventImpl(parent, child, enter, contextManager)
        contextManager.onContextPostAdd(contextPostAddEvent)
        return true
    }

    // 退出一个子上下文，不加锁，会通知 context modules: PreRemove, PostRemove。
    // 如果 check = true，先确保是父子关系。否则信任调用者。
    private fun removeNoLock(parent: ContextImpl, child: ContextImpl, exit: Boolean, check: Boolean = true): Boolean {
        val childKey = child.key
        val parentKey = parent.key

        if (check && !parent.inChildren(child)) {
            return false
        }

        if (childKey != null || parentKey != null) {
            if (childKey != null) {
                val contextPreRemoveEvent = ContextPreEdgeRemoveEventImpl(parent, child, null, exit, contextManager)
                contextManager.onContextPreRemove(contextPreRemoveEvent)

                // 它可能为 null 的原因是 alsoExitChildIfItWillBeRoot，使得 PreRemove 事件发出后可能子已经退出。
                val removedChild = parent.mutableChildrenByKey.remove(childKey)
                check(removedChild === child || removedChild == null)
            }
            if (parentKey != null) {
                val contextPreRemoveEvent = ContextPreEdgeRemoveEventImpl(parent, child, null, exit, contextManager)
                contextManager.onContextPreRemove(contextPreRemoveEvent)

                // 它可能为 null 的原因是 alsoExitChildIfItWillBeRoot，使得 PreRemove 事件发出后可能子已经退出。
                val removedParent = child.mutableParentsByKey.remove(parentKey)
                check(removedParent === parent || removedParent == null)
            }
        } else {
            val contextPreRemoveEvent = ContextPreEdgeRemoveEventImpl(parent, child, null, exit, contextManager)
            contextManager.onContextPreRemove(contextPreRemoveEvent)
        }

        // 前面虽然检查了状态，但是因为可能 alsoExitChildIfItWillBeRoot，使得此处不一定能 remove 掉。
        // 如果能的话再发事件，否则事件已经在此之前发布过了，就不再发送了。
        if (parent.mutableChildren.remove(child) && child.mutableParents.remove(parent)) {
            val contextPostRemoveEvent = ContextPostEdgeRemoveEventImpl(parent, child, null, exit, contextManager)
            contextManager.onContextPostRemove(contextPostRemoveEvent)
        }
        return true
    }

    private fun doEnterChild(child: Iterable<Any>, key: Any?, replace: Boolean): Context? {
        checkNotExited()

        val context = ContextImpl(contextManager, key).apply {
            addBeans(child)
        }
        val contextInitEvent = ContextInitEventImpl(context, contextManager)
        contextManager.onContextInit(contextInitEvent)

        listOf(lock, context.lock).add {
            val contextPreEnterEvent = ContextPreEnterEventImpl(context, contextManager)
            contextManager.onContextPreEnter(contextPreEnterEvent)

            val result = addNoLock(this, context, replace, enter = true, check = false)
            if (result) {
                val contextPostEnterEvent = ContextPostEnterEventImpl(context, contextManager)
                contextManager.onContextPostEnter(contextPostEnterEvent)
            } else {
                return null
            }
        }

        return context
    }

    override fun enterChild(child: Iterable<Any>): Context {
        return doEnterChild(child, null, replace = false)!!
    }

    override fun enterChild(child: Iterable<Any>, key: Any, replace: Boolean): Context? {
        return doEnterChild(child, key, replace)
    }

    override fun enterChild(child: Iterable<Any>, key: Any): Context {
        val result = doEnterChild(child, key, replace = false)
        requireNotNull(result) {
            "Child with same key $key already exists. To replace it, use context.enterChild(child, key, replace = true)."
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    override fun addChild(child: Context, replace: Boolean): Boolean {
        require(child is ContextImpl) { "Only ContextImpl is supported." }

        checkNotExited()
        child.checkNotExited()

        val allParents = allParents as List<ContextImpl>
        val locks = allParents.map { it.lock.readLock } + lock.addLock

        return locks.withLocks {
            require(child !in allParents) { "Cannot connect child to parent, because it will form a cycle." }
            addNoLock(this, child, enter = false, replace = replace)
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
        if (state.compareAndSet(State.ENTERED, State.EXITED)) {
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

    override fun <T : Any> addBean(value: T, keys: MutableSet<Any>, primary: Boolean): MutableBean<T> {
        return beans.addBean(value, keys, primary)
    }

    override fun <T : Any> addBean(value: T, key: Any, primary: Boolean): MutableBean<T> {
        return beans.addBean(value, key, primary)
    }

    override fun <T : Any> addBean(value: T, primary: Boolean): MutableBean<T> {
        return beans.addBean(value, primary)
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