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

import cn.chuanwise.contexts.events.ContextEvent
import cn.chuanwise.contexts.events.ContextPostAddEvent
import cn.chuanwise.contexts.events.ContextPostEnterEvent
import cn.chuanwise.contexts.events.ContextPostEnterEventImpl
import cn.chuanwise.contexts.events.ContextPostExitEvent
import cn.chuanwise.contexts.events.ContextPostRemoveEvent
import cn.chuanwise.contexts.events.ContextPreAddEvent
import cn.chuanwise.contexts.events.ContextPreEnterEvent
import cn.chuanwise.contexts.events.ContextPreEnterEventImpl
import cn.chuanwise.contexts.events.ContextPreExitEvent
import cn.chuanwise.contexts.events.ContextPreRemoveEvent
import cn.chuanwise.contexts.util.Beans
import cn.chuanwise.contexts.util.ContextPostActionEventException
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.Logger
import cn.chuanwise.contexts.util.MutableBean
import cn.chuanwise.contexts.util.MutableBeanImpl
import cn.chuanwise.contexts.util.MutableBeans
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.NotStableForInheritance
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 上下文管理器。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface ContextManager : MutableBeans {
    val logger: Logger

    val contexts: List<Context>
    val roots: List<Context>

    val modules: List<Module>
    val moduleEntries: List<MutableEntry<Module>>

    fun registerModule(module: Module): MutableEntry<Module>

    fun enter(context: Any, key: Any? = null): Context
    fun enter(vararg context: Any, key: Any? = null): Context
    fun enter(context: Collection<Any>, key: Any? = null): Context
}

@ContextsInternalApi
class ContextManagerImpl(
    override val logger: Logger,
    private val beans: MutableBeans = MutableBeanImpl()
) : ContextManager, MutableBeans by beans {
    private val mutableContexts: MutableList<Context> = CopyOnWriteArrayList()
    override val contexts: List<Context> get() = mutableContexts
    override val roots: List<Context> get() = mutableContexts.filter { it.parentCount <= 0 }

    private val mutableModuleEntries: MutableList<MutableEntry<Module>> = CopyOnWriteArrayList()
    override val modules: List<Module> get() = mutableModuleEntries.map { it.value }
    override val moduleEntries: List<MutableEntry<Module>> get() = mutableModuleEntries

    private inner class ContextModuleEntryImpl(
        override val value: Module,
        private val bean: MutableBean<Module>
    ) : MutableEntry<Module> {
        private var mutableIsRemoved = AtomicBoolean(false)
        override val isRemoved: Boolean get() = mutableIsRemoved.get()

        override fun remove() {
            if (mutableIsRemoved.compareAndSet(false, true)) {
                bean.remove()
                mutableModuleEntries.remove(this)
            }
        }
    }

    override fun registerModule(module: Module) : MutableEntry<Module> {
        module.onEnable(this)

        val bean = beans.registerBean(module)
        val entry = ContextModuleEntryImpl(module, bean)
        mutableModuleEntries.add(entry)
        logger.debug { "Registered module: ${module::class.simpleName}" }
        return entry
    }

    override fun enter(context: Any, key: Any?): Context = enter(listOf(context), key)

    override fun enter(vararg context: Any, key: Any?): Context = enter(context.toList(), key)

    override fun enter(context: Collection<Any>, key: Any?): Context {
        val newContext = ContextImpl(this, key)

        val contextPreEnterEvent = ContextPreEnterEventImpl(newContext, this)
        onContextPreEnter(contextPreEnterEvent)

        mutableContexts.add(newContext)

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

    fun onContextPreAdd(event: ContextPreAddEvent) = onPreEvent { it.onContextPreAdd(event) }
    fun onContextPostAdd(event: ContextPostAddEvent) = onPostEvent(event) { it.onContextPostAdd(event) }

    fun onContextPostRemove(event: ContextPostRemoveEvent) {
        try {
            onPostEvent(event) { it.onContextPostRemove(event) }
        } finally {
            if (event.child.parentCount <= 0 && event.exitIfNoParent) {
                event.child.exit()
            }
        }
    }
    fun onContextPreRemove(event: ContextPreRemoveEvent) = onPreEvent { it.onContextPreRemove(event) }

    fun onContextPreEnter(event: ContextPreEnterEvent) = onPreEvent { it.onContextPreEnter(event) }
    fun onContextPostEnter(event: ContextPostEnterEvent) {
        mutableContexts.add(event.context)
        onPostEvent(event) { it.onContextPostEnter(event) }
    }

    fun onContextPreExit(event: ContextPreExitEvent) = onPreEvent { it.onContextPreExit(event) }
    fun onContextPostExit(event: ContextPostExitEvent) {
        mutableContexts.remove(event.context)
        onPostEvent(event) { it.onContextPostExit(event) }
    }
}