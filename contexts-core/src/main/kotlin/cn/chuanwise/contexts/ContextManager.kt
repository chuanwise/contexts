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
import cn.chuanwise.contexts.util.ContextPostActionEventException
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableBean
import cn.chuanwise.contexts.util.MutableBeanImpl
import cn.chuanwise.contexts.util.MutableBeans
import cn.chuanwise.contexts.util.NotStableForInheritance
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 上下文管理器。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface ContextManager {
    val contexts: List<Context>
    val beans: MutableBeans

    val modules: List<ContextModule>
    val moduleEntries: List<ContextModuleEntry>

    fun registerModule(module: ContextModule): ContextModuleEntry

    fun enter(context: Any, key: Any? = null): Context
    fun enter(vararg context: Any, key: Any? = null): Context
    fun enter(context: Collection<Any>, key: Any? = null): Context
}

@ContextsInternalApi
class ContextManagerImpl : ContextManager {
    override val beans: MutableBeans = MutableBeanImpl()

    private val mutableContexts: MutableList<Context> = CopyOnWriteArrayList()
    override val contexts: List<Context> get() = mutableContexts

    private val mutableModuleEntries: MutableList<ContextModuleEntry> = CopyOnWriteArrayList()
    override val modules: List<ContextModule> get() = mutableModuleEntries.map { it.module }
    override val moduleEntries: List<ContextModuleEntry> get() = mutableModuleEntries

    private inner class ContextModuleEntryImpl(
        override val module: ContextModule,
        private val bean: MutableBean<ContextModule>
    ) : ContextModuleEntry {
        private var mutableIsRemoved = AtomicBoolean(false)
        override val isRemoved: Boolean get() = mutableIsRemoved.get()

        override val contextManager: ContextManager get() = this@ContextManagerImpl
        override fun remove() {
            if (mutableIsRemoved.compareAndSet(false, true)) {
                bean.remove()
                mutableModuleEntries.remove(this)
            }
        }
    }

    override fun registerModule(module: ContextModule) : ContextModuleEntry {
        val bean = beans.registerBean(module)
        val entry = ContextModuleEntryImpl(module, bean)
        mutableModuleEntries.add(entry)
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

    private inline fun onPreEvent(action: (ContextModule) -> Unit) {
        for (entry in mutableModuleEntries) {
            action(entry.module)
        }
    }

    private inline fun onPostEvent(event: ContextEvent, action: (ContextModule) -> Unit) {
        val exceptions = mutableListOf<Throwable>()
        for (entry in mutableModuleEntries) {
            try {
                action(entry.module)
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

    fun onContextPostRemove(event: ContextPostRemoveEvent) = onPostEvent(event) { it.onContextPostRemove(event) }
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