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

import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModuleEntry
import cn.chuanwise.contexts.module.ModulePostDisableEvent
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.module.ModulePreDisableEvent
import cn.chuanwise.contexts.module.ModulePreEnableEvent
import cn.chuanwise.contexts.util.ContextPostActionEventException
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.Logger
import cn.chuanwise.contexts.util.MutableBeanEntry
import cn.chuanwise.contexts.util.MutableBeanManagerImpl
import cn.chuanwise.contexts.util.MutableBeanManager
import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.contexts.util.ResolvableType
import cn.chuanwise.contexts.util.addBean
import cn.chuanwise.contexts.util.addBeans
import cn.chuanwise.contexts.util.createResolvableType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 上下文管理器。
 *
 * @author Chuanwise
 * @see createContextManager
 */
@NotStableForInheritance
interface ContextManager : MutableBeanManager, AutoCloseable {
    /**
     * 日志记录器。
     */
    val logger: Logger

    /**
     * 内部上下文。
     */
    val contexts: Set<Context>

    /**
     * 内部所有根上下文，即那些没有内部父节点的上下文。
     */
    val rootContexts: Set<Context>

    /**
     * 所有已经注册的模块。
     */
    val modules: List<Module>

    /**
     * 所有已经注册的模块的条目。
     */
    val moduleEntries: List<ModuleEntry>

    /**
     * 是否已经关闭。
     */
    val isClosed: Boolean

    /**
     * 根据 ID 获取一个模块。
     *
     * @param id 模块的 ID
     * @return 模块
     */
    fun getModule(id: String): Module?
    fun getModuleOrFail(id: String): Module = getModule(id) ?: throw IllegalArgumentException("Module with id $id not found")
    
    fun getModuleEntry(id: String): ModuleEntry?
    fun getModuleEntryOrFail(id: String): ModuleEntry = getModuleEntry(id) ?: throw IllegalArgumentException("Module with id $id not found")

    /**
     * 注册一个模块。
     *
     * @param module 模块
     * @param id 模块的 ID
     * @return 模块的条目
     */
    fun registerModule(module: Module, id: String?): ModuleEntry
    fun registerModule(module: Module): ModuleEntry = registerModule(module, id = null)

    /**
     * 进入一个根上下文。
     *
     * @param context 上下文
     * @param id 上下文的 ID
     * @return 进入的上下文
     */
    fun enterRoot(context: Any, id: String? = null): Context

    /**
     * 进入一个根上下文。
     *
     * @param context 上下文
     * @param id 上下文的 ID
     * @return 进入的上下文
     */
    fun enterRoot(vararg context: Any, id: String? = null): Context

    /**
     * 进入一个根上下文。
     *
     * @param context 上下文
     * @param id 上下文的键
     * @return 进入的上下文
     */
    fun enterRoot(context: Iterable<Any> = emptyList(), id: String? = null): Context

    override fun close()
}

@ContextsInternalApi
class ContextManagerImpl(
    override val logger: Logger,
    private val beans: MutableBeanManager = MutableBeanManagerImpl()
) : ContextManager, MutableBeanManager by beans {
    private val mutableContexts: MutableSet<Context> = ConcurrentHashMap.newKeySet()
    override val contexts: Set<Context> get() = mutableContexts
    override val rootContexts: Set<Context> get() = mutableContexts.filter { it.allParentCount <= 0 }.toSet()

    private val mutableModuleEntries: MutableList<ModuleEntry> = CopyOnWriteArrayList()
    override val modules: List<Module> get() = mutableModuleEntries.map { it.value }
    override val moduleEntries: List<ModuleEntry> get() = mutableModuleEntries
    private val mutableModuleEntriesByKey = ConcurrentHashMap<String, ModuleEntry>()

    private val closeLock = AtomicBoolean(false)
    override val isClosed: Boolean get() = closeLock.get()

    private inner class ModulePostEnableEventImpl(
        override val id: String?,
        override val module: Module,
        override val contextManager: ContextManager
    ) : ModulePostEnableEvent

    private inner class ModulePostDisableEventImpl(
        override val id: String?,
        override val module: Module,
        override val contextManager: ContextManager
    ) : ModulePostDisableEvent

    private inner class ModulePreDisableEventImpl(
        override val id: String?,
        override val module: Module,
        override val contextManager: ContextManager
    ) : ModulePreDisableEvent

    private inner class ModuleEntryImpl(
        override val id: String?,
        override val value: Module,
        val preEnableEvent: ModulePreEnableEventImpl,
    ) : ModuleEntry {
        private var mutableIsRemoved = AtomicBoolean(false)
        override val isRemoved: Boolean get() = mutableIsRemoved.get()

        val enableLock = AtomicBoolean(false)
        override val isEnabled: Boolean get() = enableLock.get()

        private var bean: MutableBeanEntry<Module>? = null

        fun enableNoCheck() {
            require(!isRemoved) { "Cannot enable a removed module." }
            try {
                val event = ModulePostEnableEventImpl(id, value, this@ContextManagerImpl)
                value.onModulePostEnable(event)

                @Suppress("UNCHECKED_CAST")
                val beanType = createResolvableType(value::class) as ResolvableType<Module>
                bean = beans.addBean(value, beanType)
            } catch (e: Throwable) {
                enableLock.set(false)
                throw e
            }
        }

        // PreRemove 是安全的，PostRemove 不安全。
        override fun tryRemove(): Boolean {
            val result = mutableIsRemoved.compareAndSet(false, true)
            if (result) {
                try {
                    onPreRemove()
                } catch (e: Throwable) {
                    logger.error(e) {
                        "Error occurred while removing module: ${value::class.simpleName}. " +
                                "Details: " +
                                "module class: ${value::class.simpleName}"
                    }
                } finally {
                    bean?.remove()
                    mutableModuleEntries.remove(this)
                    id?.let { mutableModuleEntriesByKey.remove(it) }
                    onPostRemove()
                }
            }
            return result
        }

        private fun onPreRemove() {
            val event = ModulePreDisableEventImpl(id, value, this@ContextManagerImpl)
            value.onModulePreDisable(event)
        }

        private fun onPostRemove() {
            val event = ModulePostDisableEventImpl(id, value, this@ContextManagerImpl)
            value.onModulePostDisable(event)
        }

        override fun toString(): String {
            return buildString {
                append("ModuleEntry(")
                append("id=$id, ")
                append("value=${value::class.simpleName}, ")
                append("isEnabled=$isEnabled")
                if (!isEnabled) {
                    append(", ")
                    append("dependencyModuleClasses=${preEnableEvent.dependencyModuleClasses}")
                    append(", ")
                    append("dependencyModuleIds=${preEnableEvent.dependencyModuleIds}")
                }
                append(")")
            }
        }
    }

    override fun getModule(id: String): Module? {
        return mutableModuleEntriesByKey[id]?.value
    }

    override fun getModuleEntry(id: String): ModuleEntry? {
        return mutableModuleEntriesByKey[id]
    }

    private inner class ModulePreEnableEventImpl(
        override val module: Module,
        override val contextManager: ContextManager,
        override var id: String?
    ) : ModulePreEnableEvent {
        val dependencyModuleIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val dependencyModuleClasses: MutableSet<Class<out Module>> = ConcurrentHashMap.newKeySet()

        override fun addDependencyModuleClass(moduleClass: Class<out Module>) {
            require(moduleClass != module::class.java) { "Module cannot depend on itself." }

            // 检查对方是否依赖于自己
            val selfDependency = dependencyModuleNotEnabledEntries
                .filter { moduleClass.isInstance(it.value) }
                .any { it.preEnableEvent.isDependencyModuleClass(moduleClass) }
            require(!selfDependency) { "Module cannot depend on a module that depends on itself." }

            dependencyModuleClasses.add(moduleClass)
        }

        override fun addDependencyModuleId(id: String) {
            require(id != this.id) { "Module cannot depend on itself." }

            val selfDependency = dependencyModuleNotEnabledEntries
                .filter { it.id == id }
                .any { it.preEnableEvent.isDependencyModuleId(id) }
            require(!selfDependency) { "Module cannot depend on a module that depends on itself." }

            dependencyModuleIds.add(id)
        }

        override fun isDependencyModuleEnabled(): Boolean {
            return dependencyModuleIds.all { getModule(it) != null } &&
                    dependencyModuleClasses.all {
                        mutableModuleEntries.any { entry -> it.isAssignableFrom(entry.value::class.java) }
                    }
        }

        override fun isDependencyModuleClass(moduleClass: Class<out Module>): Boolean {
            return dependencyModuleClasses.any { it.isAssignableFrom(moduleClass) }
        }

        override fun isDependencyModuleId(id: String): Boolean {
            return dependencyModuleIds.contains(id)
        }
    }

    // 依赖关系还不满足的那些模块的启动事件。
    private val dependencyModuleNotEnabledEntries = ConcurrentLinkedDeque<ModuleEntryImpl>()

//    override fun isAllRegisteredModuleEnabled(): Boolean {
//        return dependencyModuleNotEnabledEntries.isEmpty()
//    }

    override fun registerModule(module: Module, id: String?): ModuleEntry {
        val preEnableEvent = ModulePreEnableEventImpl(module, this, id)
        val entry = ModuleEntryImpl(id, module, preEnableEvent)

        // 如果 PreEnable 失败则原样把异常抛给注册者。
        module.onModulePreEnable(preEnableEvent)

        // 如果当前模块的依赖关系可以满足，则调用 PostEnable。
        if (preEnableEvent.isDependencyModuleEnabled()) {
            // 如果 PostEnable 失败则原样把异常抛给注册者。
            if (entry.enableLock.compareAndSet(false, true)) {
                entry.enableNoCheck()
            }

            // 记录模块已启动。
            mutableModuleEntries.add(entry)
            id?.let { mutableModuleEntriesByKey[it] = entry }

            // 尝试启动其他依赖关系从此满足的模块。
            var newModuleEnabled: Boolean
            do {
                newModuleEnabled = false

                val iterator = dependencyModuleNotEnabledEntries.iterator()
                while (iterator.hasNext()) {
                    val otherModuleEntry = iterator.next()
                    if (otherModuleEntry != null &&
                        otherModuleEntry.preEnableEvent.isDependencyModuleEnabled() &&
                        otherModuleEntry.enableLock.compareAndSet(false, true)) {

                        iterator.remove()

                        val otherModule = otherModuleEntry.value
                        val otherModuleId = otherModuleEntry.id

                        try {
                            otherModuleEntry.enableNoCheck()

                            mutableModuleEntries.add(otherModuleEntry)
                            otherModuleId?.let { mutableModuleEntriesByKey[it] = otherModuleEntry }

                            newModuleEnabled = true
                        } catch (e: Throwable) {
                            logger.error(e) {
                                "Error occurred while enabling module: ${otherModule::class.simpleName} " +
                                        "after all its dependency modules enabled (when $module enabled successfully with id: $id). " +
                                        "Details: " +
                                        "module class: ${otherModule::class.simpleName}, " +
                                        "module id: ${otherModuleEntry.id}"
                            }
                        }
                    }
                }
            } while (newModuleEnabled)
        } else {
            // 如果依赖关系不满足则记录，等待可能满足的时候再启动。
            dependencyModuleNotEnabledEntries.addLast(entry)
        }

        return entry
    }

    override fun enterRoot(context: Any, id: String?): Context = enterRoot(listOf(context), id)

    override fun enterRoot(vararg context: Any, id: String?): Context = enterRoot(context.toList(), id)

    override fun enterRoot(context: Iterable<Any>, id: String?): Context {
        val newContext = ContextImpl(this, id)
        val contextInitEvent = ContextInitEventImpl(newContext, this)
        onContextInit(contextInitEvent)

        check(newContext.trySetInitialized()) {
            "Context $newContext was exited by a module."
        }
        newContext.addBeans(context)

        val contextPreEnterEvent = ContextPreEnterEventImpl(newContext, this)
        onContextPreEnter(contextPreEnterEvent)

        check(newContext.trySetEntered()) {
            "Context $newContext was exited by a module."
        }

        // 下面的操作会在 onContextPostEnter 里自动完成，所以无需添加两次。
        // mutableContexts.add(newContext)

        val contextPostEnterEvent = ContextPostEnterEventImpl(newContext, this)
        onContextPostEnter(contextPostEnterEvent)

        return newContext
    }

    private inline fun onPreEvent(action: (Module) -> Unit) {
        for (entry in mutableModuleEntries) {
            action(entry.value)
        }
    }

    private inline fun onPostEvent(event: ContextEvent, action: (Module) -> Unit) {
        val exceptions = mutableListOf<Throwable>()
        for (entry in mutableModuleEntries) {
            try {
                action(entry.value)
            } catch (e: Throwable) {
                exceptions.add(e)
            }
        }
        if (exceptions.isNotEmpty()) {
            throw ContextPostActionEventException(event, exceptions)
        }
    }

    override fun close() {
        if (closeLock.compareAndSet(false, true)) {
            // 退出所有上下文。
            while (true) {
                val root = rootContexts.firstOrNull() ?: break
                root.exit()
            }

            // 因为启动的顺序是拓扑顺序，所以关闭的顺序是反拓扑顺序。
            for (entry in mutableModuleEntries.reversed()) {
                try {
                    entry.remove()
                } catch (e: Throwable) {
                    logger.error(e) { "Error occurred while removing module: ${entry.value::class.simpleName} " +
                            "with id: ${entry.id}." }
                }
            }
            mutableModuleEntries.clear()
            mutableModuleEntriesByKey.clear()
        }
    }

    fun onContextPreAdd(event: ContextPreEdgeAddEvent) {
        onPreEvent {
            it.onContextEdgePreAdd(event)
        }
    }
    fun onContextPostAdd(event: ContextPostEdgeAddEvent) {
        onPostEvent(event) {
            it.onContextEdgePostAdd(event)
        }
    }

    fun onContextPostRemove(event: ContextPostEdgeRemoveEvent) {
        onPostEvent(event) {
            it.onContextEdgePostRemove(event)
        }
    }
    fun onContextPreRemove(event: ContextPreEdgeRemoveEvent) {
        try {
            onPreEvent {
                it.onContextEdgePreRemove(event)
            }
        } finally {
            // event.child.parentCount == 1 的原因是此时还没有真正移除，所以 parentCount 还是 1。
            // 使用 tryExit 而不是 exit 的原因是如果正在删除的恰好是当前节点，则此时再执行 exit() 会出错。
            if (event.child.allParentCount == 1 && event.exitChildIfItWillBeRoot) {
                event.child.tryExit()
            }
        }
    }

    fun onContextPreEnter(event: ContextPreEnterEvent) {
        onPreEvent {
            it.onContextPreEnter(event)
        }
    }
    fun onContextPostEnter(event: ContextPostEnterEvent) {
        mutableContexts.add(event.context)
        onPostEvent(event) { it.onContextPostEnter(event) }
    }

    fun onContextPreExit(event: ContextPreExitEvent) {
        onPreEvent {
            it.onContextPreExit(event)
        }
    }
    fun onContextPostExit(event: ContextPostExitEvent) {
        mutableContexts.remove(event.context)
        onPostEvent(event) {
            it.onContextPostExit(event)
        }
    }

    fun onContextInit(event: ContextInitEvent) {
        onPreEvent {
            it.onContextInit(event)
        }
    }

    fun onContextBeanPreAdd(event: ContextBeanPreAddEvent<*>) {
        onPreEvent {
            it.onContextBeanPreAdd(event)
        }
    }
    fun onContextBeanPostAdd(event: ContextBeanPostAddEvent<*>) {
        onPostEvent(event) {
            it.onContextBeanPostAdd(event)
        }
    }

    fun onContextBeanPreRemove(event: ContextBeanPreRemoveEvent<*>) {
        onPreEvent {
            it.onContextBeanPreRemove(event)
        }
    }
    fun onContextBeanPostRemove(event: ContextBeanPostRemoveEvent<*>) {
        onPostEvent(event) {
            it.onContextBeanPostRemove(event)
        }
    }
}