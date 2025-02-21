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

package cn.chuanwise.contexts.reactions

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.context.ContextInitEvent
import cn.chuanwise.contexts.context.ContextPostEnterEvent
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.reactions.model.Model
import cn.chuanwise.contexts.reactions.model.ModelHandler
import cn.chuanwise.contexts.reactions.model.ModelHandlerImpl
import cn.chuanwise.contexts.reactions.util.Reactive
import cn.chuanwise.contexts.reactions.view.ViewContext
import cn.chuanwise.contexts.reactions.view.ViewContextImpl
import cn.chuanwise.contexts.reactions.view.ViewFunction
import cn.chuanwise.contexts.reactions.view.bindUsed
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeanManagerImpl
import cn.chuanwise.contexts.util.MutableEntries
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.MutableEntryMap
import cn.chuanwise.contexts.util.addBeanByCompilationType
import java.util.concurrent.ConcurrentHashMap

interface ReactionModule : Module {
    fun <T> getModelHandler(dataClass: Class<T>): MutableEntry<ModelHandler<T>>?

    fun <T> registerModelHandler(dataClass: Class<T>, modelHandler: ModelHandler<T>): MutableEntry<ModelHandler<T>>
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class ReactionModuleImpl @JvmOverloads constructor(
    private val defaultModelHandler: ModelHandler<Any?>? = ModelHandlerImpl(ReactionModuleImpl::class.java.classLoader)
) : ReactionModule {
    private val modelHandlers = MutableEntryMap<Class<Any>, ModelHandler<Any>>()

    override fun <T> getModelHandler(dataClass: Class<T>): MutableEntry<ModelHandler<T>>? {
        var result = modelHandlers[dataClass as Class<Any>] as MutableEntry<ModelHandler<T>>?
        if (result == null) {
            val annotationPresent = dataClass.isAnnotationPresent(Model::class.java)
            if (annotationPresent) {
                val modelHandler = defaultModelHandler as ModelHandler<T>?
                if (modelHandler != null) {
                    result = registerModelHandler(dataClass as Class<T>, modelHandler)
                }
            }
        }
        return result
    }

    override fun <T> registerModelHandler(dataClass: Class<T>, modelHandler: ModelHandler<T>): MutableEntry<ModelHandler<T>> {
        return modelHandlers.put(dataClass as Class<Any>, modelHandler as ModelHandler<Any>) as MutableEntry<ModelHandler<T>>
    }

    override fun onModulePostEnable(event: ModulePostEnableEvent) {
        registerDefaultModelHandlers()
    }

    private fun registerDefaultModelHandlers() {
        val modelHandler = defaultModelHandler ?: return

        registerModelHandler<Collection<*>>(modelHandler as ModelHandler<Collection<*>>)
        registerModelHandler<Map<*, *>>(modelHandler as ModelHandler<Map<*, *>>)
    }

    private class ViewFunctionImpl(
        private val autoBind: Boolean,
        private val function: ViewFunction
    ) : ViewFunction {
        override fun buildView(context: ViewContext) {
            if (autoBind) {
                context.bindUsed {
                    function.buildView(context)
                }
            } else {
                function.buildView(context)
            }
        }

        fun safeBuildView(context: ViewContext) {
            try {
                buildView(context)
            } catch (t: Throwable) {
                context.context.contextManager.logger.error(t) {
                    "Error occurred while building view for function: $function."
                }
            }
        }
    }

    private data class ReactiveCache(
        val value: Any?,
        val modelHandler: ModelHandler<Any?>?
    ) {
        companion object {
            private val NULL = ReactiveCache(null, null)

            fun of(value: Any?, modelHandler: ModelHandler<Any?>?) : ReactiveCache {
                return if (value == null && modelHandler == null) {
                    NULL
                } else {
                    ReactiveCache(value, modelHandler)
                }
            }
        }
    }

    inner class ReactionManagerImpl(
        override val context: Context,
    ) : ReactionManager {
        override var viewContext: Context? = null

        private val viewFunctions = MutableEntries<ViewFunctionImpl>()

        private val reactiveCache = ConcurrentHashMap<Reactive<Any?>, ReactiveCache>()

        override fun registerViewFunction(autoBind: Boolean, function: ViewFunction): MutableEntry<ViewFunction> {
            val finalFunction = ViewFunctionImpl(autoBind, function)
            return viewFunctions.add(finalFunction)
        }

        fun clearReactiveOldValue() {
            reactiveCache.clear()
        }

        fun <T> cacheReactiveValue(reactive: Reactive<T>, value: T) {
            val modelHandler = getModelHandler(reactive.type.rawClass.java)?.value as ModelHandler<Any?>?

            val newValue = value ?: reactiveCache
            val oldValue = reactiveCache.put(reactive, ReactiveCache.of(value, modelHandler))

            check(oldValue != newValue) {
                "Reactive value $reactive has been cached with value $oldValue previously, but tried to cache with value $newValue."
            }
        }

        override fun <T> tryFlush(reactive: Reactive<T>, value: T): Boolean {
            val cache = reactiveCache[reactive]
            requireNotNull(cache) { "Reactive value $reactive has not been cached previously." }

            val modelHandler = cache.modelHandler
            return if (modelHandler == null) {
                if (cache.value != value) {
                    flush()
                    true
                } else {
                    false
                }
            } else {
                modelHandler.tryFlush(context, cache.value, value)
            }
        }

        override fun tryFlush(): Boolean {
            if (viewFunctions.isEmpty) {
                return false
            }
            flush()
            return true
        }

        override fun flush() {
            // 退出此前的视图上下文，进入新的视图上下文。
            viewContext?.exit()
            clearReactiveOldValue()

            if (viewFunctions.isEmpty) {
                return
            }

            val viewContextLocal = context.enterChild(id = "View")
            viewContext = viewContextLocal

            val beanManager = InheritedMutableBeanManagerImpl(viewContextLocal)
            val context = ViewContextImpl(context, beanManager, this, this@ReactionModuleImpl)
            beanManager.addBeanByCompilationType(context)

            viewFunctions.forEach {
                it.value.safeBuildView(context)
            }
        }
    }

    override fun onContextInit(event: ContextInitEvent) {
        val reactionManager = ReactionManagerImpl(event.context)
        event.context.addBeanByCompilationType(reactionManager)
    }

    override fun onContextPostEnter(event: ContextPostEnterEvent) {
        val reactionManager = event.context.reactionManagerOrNull as? ReactionManagerImpl ?: return
        reactionManager.tryFlush()
    }
}