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

import cn.chuanwise.contexts.util.BreadthFirstSearch
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableBeans
import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.contexts.util.ReadAddRemoveLock
import cn.chuanwise.contexts.util.add
import cn.chuanwise.contexts.util.read
import cn.chuanwise.contexts.util.remove
import cn.chuanwise.contexts.util.withWriteLocks
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@NotStableForInheritance
interface Context : MutableBeans {
    val key: String?
    val manager: ContextManager

    /**
     *
     */
    val parents: List<Context>
    val allParents: List<Context>

    val children: List<Context>
    val allChildren: List<Context>

    /**
     * 联通分量里的所有上下文，即包括自己和所有父子上下文。
     */
    val components: List<Context>

    /**
     * 进入一个子上下文。
     *
     * @param key 上下文键。
     * @param child 子上下文对象。
     * @param replace 若设置了 [key]，是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(
        child: Any,
        key: String? = null,
        replace: Boolean = false
    ): Context?

    /**
     * 进入一个子上下文。
     *
     * @param key 上下文键。
     * @param child 子上下文对象。
     * @param replace 若设置了 [key]，是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(
        vararg child: Any,
        key: String? = null,
        replace: Boolean = false
    ): Context?

    /**
     * 进入一个子上下文。
     *
     * @param key 上下文键。
     * @param child 子上下文对象。
     * @param replace 是否替换已有的同键子上下文。
     * @return 添加后的上下文。
     */
    fun enterChild(
        child: Collection<Any>,
        key: String? = null,
        replace: Boolean = false
    ): Context?

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

//    fun removeChild(key: String): Context
//    fun removeChildren(children: Any): Map<String, Context>
//
//    fun removeChildren()
//
//    fun replaceChild(oldContext: Context, newContext: Context)
//
//    fun addParent(context: Context)
//    fun removeParent(context: Context)
//    fun replaceParent(oldContext: Context, newContext: Context)
}

@ContextsInternalApi
abstract class AbstractContext : Context {
    override fun enterChild(child: Any, key: String?, replace: Boolean): Context? {
        return enterChild(listOf(child), key, replace)
    }

    override fun enterChild(vararg child: Any, key: String?, replace: Boolean): Context? {
        return enterChild(child.toList(), key, replace)
    }
}

@ContextsInternalApi
class ContextImpl(
    override val manager: ContextManager,
    override val key: String?,
    private val beans: MutableBeans = MutableBeans(),
) : AbstractContext(), MutableBeans by beans {
    private val lock = ReadAddRemoveLock()

    private val mutableParents: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableParentsByKey: MutableMap<String, ContextImpl> = mutableMapOf()

    override val parents: List<Context> get() = lock.read { mutableParents.toList() }
    override val allParents: List<Context> get() = BreadthFirstSearch(ArrayDeque(parents)).asSequence().toList()

    private val mutableChildren: MutableCollection<ContextImpl> = mutableListOf()
    private val mutableChildrenByKey: MutableMap<String, ContextImpl> = mutableMapOf()

    override val children: List<Context> get() = lock.read { mutableChildren.toList() }
    override val allChildren: List<Context> get() = BreadthFirstSearch(ArrayDeque(children)).asSequence().toList()

    override val components: List<Context> get() = allParents + allChildren + this

    init {
        put(this)
    }

    private fun onContextPreEnter(context: Context) {
        for (module in manager.modules) {
            module.onContextPreEnter(context)
        }
    }

    private fun onContextPreAdd(parent: Context, child: Context) {
        for (module in manager.modules) {
            module.onContextPreAdd(parent, child)
        }
    }

    private fun onContextPreRemove(parent: Context, child: Context) {
        for (module in manager.modules) {
            module.onContextPreRemove(parent, child)
        }
    }

    private fun onContextPostEnter(context: Context) {
        for (module in manager.modules) {
            module.onContextPostEnter(context)
        }
    }

    private fun onContextPostAdd(parent: Context, child: Context) {
        for (module in manager.modules) {
            module.onContextPostAdd(parent, child)
        }
    }

    private fun onContextPostRemove(parent: Context, child: Context) {
        for (module in manager.modules) {
            module.onContextPostRemove(parent, child)
        }
    }

    // 连接两个上下文，不检查是否会成环，不加锁，会通知 context modules: PreAdd, PostAdd。
    private fun addNoLock(parent: ContextImpl, child: ContextImpl, replace: Boolean): Boolean {
        val key = child.key
        if (key != null) {
            if (replace) {
                val oldChild = parent.mutableChildrenByKey[key]
                if (oldChild != null) {
                    // 如果已经是子上下文了，直接返回。
                    if (oldChild === child) {
                        return false
                    }

                    // 否则需要先退出原来的子上下文。
                    onContextPreRemove(parent, oldChild)

                    check(parent.mutableChildren.remove(oldChild))
                    check(parent.mutableChildrenByKey.remove(key) === oldChild)

                    check(oldChild.mutableParents.remove(parent))
                    check(oldChild.mutableParentsByKey.remove(key) === parent)

                    onContextPostRemove(parent, oldChild)
                }

                val oldParent = child.mutableParentsByKey[key]
                if (oldParent != null) {
                    // 如果它们之间有父子关系，在前面就已经退出了。
                    // 所以此处必定是同键的两个上下文，但是不是父子关系。
                    check(oldParent !== parent)

                    onContextPreRemove(oldParent, child)

                    check(child.mutableParents.remove(oldParent))
                    check(child.mutableParentsByKey.remove(key) === oldParent)

                    check(oldParent.mutableChildren.remove(child))
                    check(oldParent.mutableChildrenByKey.remove(key) === child)

                    onContextPostRemove(oldParent, child)
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

            onContextPreAdd(parent, child)

            parent.mutableChildrenByKey[key] = child
            child.mutableParentsByKey[key] = parent
        } else {
            onContextPreAdd(parent, child)
        }

        parent.mutableChildren.add(child)
        child.mutableParents.add(parent)

        onContextPostAdd(parent, child)
        return true
    }

    // 退出一个子上下文，不加锁，会通知 context modules: PreRemove, PostRemove。
    private fun removeNoLock(parent: ContextImpl, child: ContextImpl): Boolean {
        val key = child.key
        if (key != null) {
            val oldChild = parent.mutableChildrenByKey[key]

            // 如果它们本来就没有父子关系，则退出。
            if (oldChild !== child) {
                return false
            }

            onContextPreRemove(parent, child)

            check(parent.mutableChildrenByKey.remove(key) === child)
            check(child.mutableParentsByKey.remove(key) === parent)
        } else {
            onContextPreRemove(parent, child)
        }

        val removed = parent.mutableChildren.remove(child) && child.mutableParents.remove(parent)
        if (removed) {
            onContextPostRemove(parent, child)
        }

        return removed
    }

    override fun enterChild(
        child: Collection<Any>,
        key: String?,
        replace: Boolean
    ): Context? {
        val context = ContextImpl(manager, key, beans).apply {
            putAll(child)
        }

        listOf(lock, context.lock).add {
            onContextPreEnter(context)

            val result = addNoLock(this, context, replace)
            if (result) {
                onContextPostEnter(context)
            } else {
                return null
            }
        }

        return context
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

            componentLocks.add {
                onContextPreEnter(child)

                val result = addNoLock(this, child, replace)
                if (result) {
                    onContextPostEnter(child)
                }

                result
            }
        }
    }

    override fun removeChild(child: Context): Boolean {
        require(child is ContextImpl) { "Only ContextImpl is supported." }

        return lock.remove {
            removeNoLock(this, child)
        }
    }

    override fun addParent(parent: Context, replace: Boolean): Boolean = parent.addChild(this, replace)

    override fun removeParent(parent: Context): Boolean = parent.removeChild(this)
}