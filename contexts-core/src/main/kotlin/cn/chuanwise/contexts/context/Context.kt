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

import cn.chuanwise.contexts.util.AbstractInheritedMutableBeanManager
import cn.chuanwise.contexts.util.BeanManager
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableBeanEntry
import cn.chuanwise.contexts.util.MutableBeanManager
import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.contexts.util.ReadWriteLockBasedReadAddRemoveLock
import cn.chuanwise.contexts.util.ResolvableType
import cn.chuanwise.contexts.util.add
import cn.chuanwise.contexts.util.addBean
import cn.chuanwise.contexts.util.addBeans
import cn.chuanwise.contexts.util.createAllChildrenBreadthFirstSearchIterator
import cn.chuanwise.contexts.util.createAllParentsBreadthFirstSearchIterator
import cn.chuanwise.contexts.util.read
import cn.chuanwise.contexts.util.remove
import cn.chuanwise.contexts.util.withLocks
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
interface Context : MutableBeanManager, AutoCloseable {
    /**
     * 上下文的 ID。
     *
     * 此 ID 并不一定唯一，只能确保在它的直接邻居中唯一。
     */
    val id: Any?

    /**
     * 该上下文对应的上下文管理器。
     */
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

    fun isInAllParents(context: Context): Boolean
    fun isInAllChildren(context: Context): Boolean

    fun isParent(context: Context): Boolean
    fun isChild(context: Context): Boolean

    fun getChildById(id: Any): Context?
    fun getChildByIdOrFail(id: Any): Context {
        return getChildById(id) ?: throw NoSuchElementException("Cannot find child with key $id.")
    }

    fun getParentById(id: Any): Context?
    fun getParentByIdOrFail(id: Any): Context {
        return getParentById(id) ?: throw NoSuchElementException("Cannot find parent with key $id.")
    }

    /**
     * 尝试获取一个带有指定对象的子上下文。
     *
     * @param bean 对象
     * @param id 对象 ID
     * @return 子上下文
     */
    fun <T> getChildByBean(bean: T, beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): Context?
    fun <T> getChildByBeanOrFail(bean: T, beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): Context

    fun <T> getChildByBeanType(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): Context?
    fun <T> getChildByBeanTypeOrFail(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): Context

    fun <T> getParentByBean(bean: T, beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): Context?
    fun <T> getParentByBeanOrFail(bean: T, beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): Context

    fun <T> getParentByBeanType(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): Context?
    fun <T> getParentByBeanTypeOrFail(beanType: ResolvableType<T>, id: String? = null, primary: Boolean? = null): Context

    /**
     * 进入一个子上下文。
     *
     * @param id 上下文 ID。
     * @param child 子上下文对象。
     * @param replace 是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(child: Any, id: String, replace: Boolean = false): Context?
    fun enterChild(child: Any, id: String): Context
    fun enterChild(child: Any): Context

    /**
     * 进入一个子上下文。
     *
     * @param id 上下文 ID。
     * @param child 子上下文对象。
     * @param replace 是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(vararg child: Any, id: String, replace: Boolean = false): Context?
    fun enterChild(vararg child: Any, id: String): Context
    fun enterChild(vararg child: Any): Context

    /**
     * 进入一个子上下文。
     *
     * @param id 上下文 ID。
     * @param child 子上下文对象。
     * @param replace 是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(child: Iterable<Any>, id: String, replace: Boolean = false): Context?
    fun enterChild(child: Iterable<Any>, id: String): Context
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
     * 尝试退出上下文。
     *
     * @return 是否成功推出
     */
    fun tryExit(): Boolean

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
    override fun enterChild(child: Any, id: String, replace: Boolean): Context? {
        return enterChild(listOf(child), id, replace)
    }

    override fun enterChild(vararg child: Any, id: String, replace: Boolean): Context? {
        return enterChild(child.toList(), id, replace)
    }

    override fun enterChild(child: Any): Context {
        return enterChild(listOf(child))
    }

    override fun enterChild(vararg child: Any): Context {
        return enterChild(child.toList())
    }

    override fun enterChild(child: Any, id: String): Context {
        return enterChild(listOf(child), id)
    }

    override fun enterChild(vararg child: Any, id: String): Context {
        return enterChild(child.toList(), id, replace = false)!!
    }
}

@ContextsInternalApi
private class ContextBeanManagerImpl : AbstractInheritedMutableBeanManager() {
    lateinit var context: ContextImpl

    override fun <T> addBean(value: T, type: ResolvableType<T>, id: String?, primary: Boolean): MutableBeanEntry<T> {
        context.checkNotExited()

        val preAddEvent = ContextBeanPreAddEventImpl(context, value, context.contextManager, id, primary, type)
        context.contextManager.onContextBeanPreAdd(preAddEvent)

        val bean = super.addBean(value, type, id, primary)

        val postAddEvent = ContextBeanPostAddEventImpl(context, value, bean, context.contextManager)
        context.contextManager.onContextBeanPostAdd(postAddEvent)
        return bean
    }

    override fun removeBean(bean: MutableBeanEntry<*>) {
        context.checkNotExited()

        val preRemoveEvent = ContextBeanPreRemoveEventImpl(context, bean, context.contextManager)
        context.contextManager.onContextBeanPreRemove(preRemoveEvent)

        super.removeBean(bean)

        val postRemoveEvent = ContextBeanPostRemoveEventImpl(context, bean, context.contextManager)
        context.contextManager.onContextBeanPostRemove(postRemoveEvent)
    }

    private inner class AllParentIterable : Iterable<BeanManager> {
        override fun iterator(): Iterator<BeanManager> {
            return sequence {
                yieldAll(context.createChildToParentTopologicalIteratorInAllParents())
                yield(context.contextManager)
            }.iterator()
        }
    }

    override val parentBeanManagers: Iterable<BeanManager> get() = AllParentIterable()
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class ContextImpl private constructor(
    override val contextManager: ContextManagerImpl,
    override val id: Any?,
    private val beanManager: ContextBeanManagerImpl
) : AbstractContext(), MutableBeanManager by beanManager {
    private val lock = ReadWriteLockBasedReadAddRemoveLock()

    private val mutableParents: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableParentsById: MutableMap<Any, ContextImpl> = mutableMapOf()

    override val parents: List<Context> get() = lock.read { mutableParents.toList() }
    override val parentCount: Int get() = lock.read { mutableParents.size }

    override val singleParent: Context get() = lock.read { mutableParents.single() }
    override val singleParentOrNull: Context? get() = lock.read { mutableParents.singleOrNull() }

    override fun isParent(context: Context): Boolean {
        return lock.read { context in mutableParents }
    }

    override val allParents: List<Context> get() = createAllParentsBreadthFirstSearchIterator().asSequence().toList()
    override val allParentCount: Int get() = lock.read { mutableParents.size }

    override val singleAllParent: Context get() = lock.read { allParents.single() }
    override val singleAllParentOrNull: Context? get() = lock.read { allParents.singleOrNull() }

    override fun isInAllParents(context: Context): Boolean {
        return context in allParents
    }

    private val mutableChildren: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableChildrenById: MutableMap<Any, ContextImpl> = mutableMapOf()

    override val children: List<Context> get() = lock.read { mutableChildren.toList() }
    override val childCount: Int get() = lock.read { mutableChildren.size }

    override val singleChild: Context get() = lock.read { mutableChildren.single() }
    override val singleChildOrNull: Context? get() = lock.read { mutableChildren.singleOrNull() }

    override fun isChild(context: Context): Boolean {
        return lock.read { context in mutableChildren }
    }

    override val allChildren: List<Context> get() = createAllChildrenBreadthFirstSearchIterator().asSequence().toList()
    override val allChildCount: Int get() = lock.read { mutableChildren.size }

    override val singleAllChild: Context get() = lock.read { allChildren.single() }
    override val singleAllChildOrNull: Context? get() = lock.read { allChildren.singleOrNull() }

    override fun isInAllChildren(context: Context): Boolean {
        return context in allChildren
    }

    private enum class State {
        ALLOCATED, INITIALIZED, ENTERED, EXITED
    }

    private val state = AtomicReference(State.ALLOCATED)
    override val isExited: Boolean get() = state.get() == State.EXITED
    override val isInitialized: Boolean get() = state.get() != State.ALLOCATED
    override val isEntered: Boolean get() = state.get() == State.ENTERED

    constructor(contextManager: ContextManagerImpl, id: Any?) : this(contextManager, id, ContextBeanManagerImpl())

    init {
        beanManager.context = this
        addBean(this)
    }

    override fun getChildById(id: Any): Context? = lock.read {
        mutableChildrenById[id]
    }

    override fun getParentById(id: Any): Context? = lock.read {
        mutableParentsById[id]
    }

    override fun <T> getParentByBeanType(beanType: ResolvableType<T>, id: String?, primary: Boolean?): Context? {
        for (parent in createAllParentsBreadthFirstSearchIterator()) {
            parent.getBeanEntry(beanType, id = id, primary = primary) ?: continue
            return parent
        }
        return null
    }

    override fun <T> getParentByBeanTypeOrFail(beanType: ResolvableType<T>, id: String?, primary: Boolean?): Context {
        return getParentByBeanType(beanType, id, primary) ?: throw NoSuchElementException("Cannot find parent with bean type $beanType.")
    }

    override fun <T> getParentByBean(bean: T, beanType: ResolvableType<T>, id: String?, primary: Boolean?): Context? {
        for (parent in createAllParentsBreadthFirstSearchIterator()) {
            val beanValue = parent.getBeanEntry(beanType, id = id, primary = primary)?.value ?: continue
            if (beanValue == bean) {
                return parent
            }
        }
        return null
    }

    override fun <T> getParentByBeanOrFail(
        bean: T,
        beanType: ResolvableType<T>,
        id: String?,
        primary: Boolean?
    ): Context {
        return getParentByBean(bean, beanType, id, primary) ?: throw NoSuchElementException("Cannot find parent with bean $bean.")
    }

    override fun <T> getChildByBeanType(beanType: ResolvableType<T>, id: String?, primary: Boolean?): Context? {
        for (child in createAllChildrenBreadthFirstSearchIterator()) {
            child.getBeanEntry(beanType, id = id, primary = primary) ?: continue
            return child
        }
        return null
    }

    override fun <T> getChildByBeanTypeOrFail(beanType: ResolvableType<T>, id: String?, primary: Boolean?): Context {
        return getChildByBeanType(beanType, id, primary) ?: throw NoSuchElementException("Cannot find child with bean type $beanType.")
    }

    override fun <T> getChildByBean(bean: T, beanType: ResolvableType<T>, id: String?, primary: Boolean?): Context? {
        for (child in createAllChildrenBreadthFirstSearchIterator()) {
            val beanValue = child.getBeanEntry(beanType, id = id, primary = primary)?.value ?: continue
            if (beanValue == bean) {
                return child
            }
        }
        return null
    }

    override fun <T> getChildByBeanOrFail(
        bean: T,
        beanType: ResolvableType<T>,
        id: String?,
        primary: Boolean?
    ): Context {
        return getChildByBean(bean, beanType, id, primary) ?: throw NoSuchElementException("Cannot find child with bean $bean.")
    }

    // 连接两个上下文，不检查是否会成环，不加锁，会通知 context modules: PreAdd, PostAdd。
    // 如果 check = true，先确保不是父子关系。否则信任调用者。
    private fun addNoLock(parent: ContextImpl, child: ContextImpl, replace: Boolean, enter: Boolean, check: Boolean = true): Boolean {
        val childKey = child.id
        val parentKey = parent.id

        if (check && parent.isChild(child)) {
            return false
        }

        val contextPreAddEvent = ContextPreEdgeAddEventImpl(parent, child, enter, contextManager)
        if (childKey != null || parentKey != null) {
            if (replace) {
                val oldChild = childKey?.let { parent.mutableChildrenById[it] }
                if (oldChild != null) {
                    check(removeNoLock(parent, oldChild, exit = false, check = false))
                }

                val oldParent = parentKey?.let { child.mutableParentsById[it] }
                if (oldParent != null) {
                    check(removeNoLock(oldParent, child, exit = false, check = false))
                }
            } else {
                val oldChild = parent.mutableChildrenById[childKey]
                if (oldChild != null) {
                    return false
                }

                val oldParent = child.mutableParentsById[childKey]
                if (oldParent != null) {
                    return false
                }
            }

            contextManager.onContextPreAdd(contextPreAddEvent)

            childKey?.let { parent.mutableChildrenById[childKey] = child }
            parentKey?.let { child.mutableParentsById[parentKey] = parent }
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
        val childKey = child.id
        val parentKey = parent.id

        if (check && !parent.isChild(child)) {
            return false
        }

        if (childKey != null || parentKey != null) {
            if (childKey != null) {
                val contextPreRemoveEvent = ContextPreEdgeRemoveEventImpl(parent, child, null, exit, contextManager)
                contextManager.onContextPreRemove(contextPreRemoveEvent)

                // 它可能为 null 的原因是 alsoExitChildIfItWillBeRoot，使得 PreRemove 事件发出后可能子已经退出。
                val removedChild = parent.mutableChildrenById.remove(childKey)
                check(removedChild === child || removedChild == null)
            }
            if (parentKey != null) {
                val contextPreRemoveEvent = ContextPreEdgeRemoveEventImpl(parent, child, null, exit, contextManager)
                contextManager.onContextPreRemove(contextPreRemoveEvent)

                // 它可能为 null 的原因是 alsoExitChildIfItWillBeRoot，使得 PreRemove 事件发出后可能子已经退出。
                val removedParent = child.mutableParentsById.remove(parentKey)
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

    fun trySetInitialized() : Boolean {
        return state.compareAndSet(State.ALLOCATED, State.INITIALIZED)
    }

    fun trySetEntered() : Boolean {
        return state.compareAndSet(State.INITIALIZED, State.ENTERED)
    }

    private fun doEnterChild(child: Iterable<Any>, id: String?, replace: Boolean): Context? {
        checkNotExited()

        val context = ContextImpl(contextManager, id)
        val contextInitEvent = ContextInitEventImpl(context, contextManager)
        contextManager.onContextInit(contextInitEvent)

        if (!context.trySetInitialized()) {
            return null
        }
        context.addBeans(child)

        if (!context.trySetEntered()) {
            return null
        }

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
        val result = doEnterChild(child, null, replace = false)
        requireNotNull(result) { "Child context was exited by a module." }
        return result
    }

    override fun enterChild(child: Iterable<Any>, id: String, replace: Boolean): Context? {
        return doEnterChild(child, id, replace)
    }

    override fun enterChild(child: Iterable<Any>, id: String): Context {
        val result = doEnterChild(child, id, replace = false)
        requireNotNull(result) {
            "Child context with same id $id already exists or it was exited by a module. " +
                    "For first situation, to replace it, use context.enterChild(child, id, replace = true)."
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

    override fun tryExit(): Boolean {
        val result = state.compareAndSet(State.ENTERED, State.EXITED)
        if (result) {
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
        return result
    }

    override fun exit() {
        check(tryExit()) { "Unexpected context state $state while exiting." }
    }

    override fun toString(): String {
        return "Context(key=$id, hash=${hashCode()}, exit=$isExited)"
    }

    fun checkNotExited() {
        check(!isExited) { "Context is exited." }
    }

    fun checkInitialized() {
        check(isInitialized) { "Context is not initialized." }
    }
}