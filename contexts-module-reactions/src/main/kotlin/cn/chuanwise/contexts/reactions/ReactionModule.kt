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
import cn.chuanwise.contexts.reactions.reactive.AbstractReactive
import cn.chuanwise.contexts.reactions.reactive.Reactive
import cn.chuanwise.contexts.reactions.reactive.ReactiveCallContext
import cn.chuanwise.contexts.reactions.reactive.ReactiveCallObserver
import cn.chuanwise.contexts.reactions.view.ViewContext
import cn.chuanwise.contexts.reactions.view.ViewContextImpl
import cn.chuanwise.contexts.reactions.view.ViewFunction
import cn.chuanwise.contexts.reactions.view.bind
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeanManagerImpl
import cn.chuanwise.contexts.util.MutableEntries
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.addBeanByCompilationType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

interface ReactionModule : Module

// 正在构建的视图上下文，用于向 ReactiveCallContext 传递参数。
@ContextsInternalApi
val buildingContext = ThreadLocal<Context>()

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class ReactionModuleImpl: ReactionModule {
    private class ViewFunctionImpl(
        private val context: Context,
        private val autoBind: Boolean,
        private val function: ViewFunction
    ) : ViewFunction {
        override fun buildView(context: ViewContext) {
            val backup = buildingContext.get()
            buildingContext.set(this.context)
            try {
                if (autoBind) {
                    context.bind {
                        function.buildView(context)
                    }
                } else {
                    function.buildView(context)
                }
            } finally {
                if (backup == null) {
                    buildingContext.remove()
                } else {
                    buildingContext.set(backup)
                }
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

    interface ReactiveCache<T> {
        val oldValue: T

        fun isReproducible(newValue: T): Boolean
    }

    // 保存对一个对象的函数调用情况，以及检查这种调用能否复现。
    private class ProxyReactiveCacheImpl<T>(override val oldValue: T) : ReactiveCallObserver<Any?>, ReactiveCache<T> {
        // 用于维护函数调用的信息。
        private val lock = ReentrantReadWriteLock()

        // 调用计数器，用于严格检查调用的顺序。
        private var stamp = 0

        // 用于保存对象引用，以便比较是否相等。
        private class Ref<T>(val value: T) {
            override fun toString(): String {
                return "Ref(value=$value)"
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Ref<*>

                return value === other.value
            }

            override fun hashCode(): Int {
                val value = value
                return if (value != null) System.identityHashCode(value) else 0
            }
        }

        // 表示一个调用主体的来源。
        private class CallSource(
            val rawProxyRef: Ref<*>,
            val rawRef: Ref<*>,
            val firstCalling: ReactiveCallContext<*>
        )
        private class Call(
            val callSource: CallSource,
            val context: ReactiveCallContext<*>,
            val stamp: Int,

            // null 表示返回值为 null，或者无法建立代理。
            val resultProxyRef: Ref<*>?,
            val resultNull: Boolean
        ) {
            override fun toString(): String {
                return "Call(method=${context.method})"
            }
        }
        private class CallRecord {
            val calls = mutableListOf<Call>()

            fun addCall(call: Call) {
                calls.add(call)
            }
        }

        private val calls = mutableListOf<Call>()
        private val callSourceByCallContexts = mutableMapOf<ReactiveCallContext<*>?, CallSource>()
        private val callSourceByProxyRefs = mutableMapOf<Ref<*>, CallSource>()

        override fun isReproducible(newValue: T): Boolean = lock.read {
            if ((newValue == null) != (oldValue == null)) {
                return@read false
            }
            if (newValue == null) {
                return@read true
            }

            val oldValue = oldValue
            checkNotNull(oldValue) { "Old value cannot be null." }

            val oldContextToNewResult = mutableMapOf<ReactiveCallContext<*>, Any?>()
            val oldContextToNewRaw = mutableMapOf<ReactiveCallContext<*>, Any?>()

            // 根据调用的顺序重试。
            for (oldCall in calls) {
                val oldCallSource = oldCall.callSource
                val newRaw = if (
                    oldCallSource.firstCalling.sourceResult == null &&
                    oldCallSource.firstCalling.sourceRaw == null
                ) {
                    // 如果没有找到对应的调用源，说明这个调用源就是 reactive value 本身。
                    newValue
                } else {
                    // 从当前老 context 往源 context 走，得到的是反过来的调用链。
                    val callChainNewToOld = generateSequence(oldCallSource.firstCalling) { it.sourceResult ?: it.sourceRaw }.toList()
                    val callChainOldToNew = callChainNewToOld.reversed()

                    // 调用链不可能为空，否则它就在前面已经处理过。
                    check(callChainOldToNew.isNotEmpty()) { "Call chain cannot be empty." }

                    // 需要按照这个顺序重新调用。
                    // 从根开始往前走。
                    var newRawLocal: Any? = newValue
                    for (i in callChainOldToNew.indices) {
                        val currOld = callChainOldToNew[i]
                        val nextOld = callChainOldToNew.getOrNull(i + 1) ?: break

                        // 如果 newRawRef 已经是 null 了，当然就没法调用了。
                        // 或者它的类型已经不支持调用这个函数，那也是一样的。
                        if (newRawLocal == null || !currOld.method.declaringClass.isAssignableFrom(newRawLocal::class.java)) {
                            return@read false
                        }

                        newRawLocal = when {
                            nextOld == null || nextOld.sourceResult == currOld -> {
                                var result = oldContextToNewResult[currOld]
                                if (result == null) {
                                    result = currOld.method.invoke(newRawLocal, *currOld.arguments)
                                    oldContextToNewResult[currOld] = result
                                    oldContextToNewRaw[currOld] = newRawLocal
                                }
                                result
                            }
                            nextOld.sourceRaw === currOld -> {
                                var result = oldContextToNewRaw[currOld]
                                if (result == null) {
                                    oldContextToNewResult[currOld] = currOld.method.invoke(newRawLocal, *currOld.arguments)
                                    result = newRawLocal
                                    oldContextToNewRaw[currOld] = result
                                }
                                result
                            }
                            else -> error("Unknown call chain.")
                        }
                    }

                    newRawLocal
                }

                // 接下来需要调用 newCallRawRef 的方法，看看结果是否一致。如果类型变了，或者值为 null，那就不用再调用了。
                if (newRaw == null || !oldCall.context.method.declaringClass.isAssignableFrom(newRaw::class.java)) {
                    return@read false
                }
                val newResult = oldCall.context.method.invoke(newRaw, *oldCall.context.arguments)

                val oldResult = oldCall.context.result
                val oldResultProxyRef = oldCall.resultProxyRef

                // 如果非空性变了，则肯定不行。
                if ((oldResult == null) != (newResult == null)) {
                    return@read false
                }

                // 如果都是 null，那确实很好处理，只需要处理都非 null 的情况。
                if (oldResult != null) {
                    // 不能直接比较 newResult 和 oldCall.context.result：
                    // 1. 如果一样，也不能说明没变化，因为可能是同一个可变对象，this == this。
                    // 2. 如果不一样，也不能说明变化了，因为可能表现出来的样子还是一样的。
                    val oldResultAsCallSource = callSourceByProxyRefs[oldResultProxyRef]

                    // 因此，只有那些后续没有继续使用的值，我们才比较是否 equals。
                    // 如果继续使用了，我们可以观测它后续的行为。
                    if (oldResultAsCallSource == null) {
                        if (oldResult != newResult) {
                            return@read false
                        }
                    }
                }

                oldContextToNewRaw[oldCall.context] = newRaw
                oldContextToNewResult[oldCall.context] = newResult
            }

            true
        }

        override fun onFunctionCall(context: ReactiveCallContext<Any?>): Unit = lock.write {
            val rawProxyRef = Ref(context.rawProxy)

            val callSource = callSourceByCallContexts.computeIfAbsent(context.sourceResult) {
                CallSource(rawProxyRef, Ref(context.raw), context)
            }
            callSourceByProxyRefs[rawProxyRef] = callSource

            val result = context.result
            val resultProxy = context.resultProxy

            val call = if (result === resultProxy) {
                if (result == null) {
                    Call(callSource, context, stamp++, null, resultNull = true)
                } else {
                    Call(callSource, context, stamp++, null, resultNull = false)
                }
            } else {
                val resultProxyRef = Ref(resultProxy)
                Call(callSource, context, stamp++, resultProxyRef, resultNull = false)
            }

            println("   ___ CALL: $call")
            calls.add(call)
        }
    }
    private class FixedReactiveCacheImpl<T>(override val oldValue: T) : ReactiveCache<T> {
        override fun isReproducible(newValue: T): Boolean {
            return oldValue == newValue
        }
    }

    inner class ReactionManagerImpl(
        override val context: Context,
        private val parent: ReactionManagerImpl?,
    ) : ReactionManager, ReactiveCallObserver<Any?> {
        override var autoFlush: Boolean = true

        private var isFlushingNotInherit: Boolean = false
        override val isFlushing: Boolean get() = parent?.isFlushing ?: isFlushingNotInherit
        override var viewContext: ViewContextImpl? = null

        private val viewFunctions = MutableEntries<ViewFunctionImpl>()
        private val reactiveCache = ConcurrentHashMap<Reactive<Any?>, ReactiveCache<Any?>>()

        override fun registerViewFunction(autoBind: Boolean, function: ViewFunction): MutableEntry<ViewFunction> {
            val finalFunction = ViewFunctionImpl(context, autoBind, function)
            return viewFunctions.add(finalFunction)
        }

        private fun clearReactiveCache() {
            reactiveCache.clear()
        }

        fun <T> setReactiveCache(reactive: Reactive<T>, value: T) {
            // 如果只是 reactive 读，只缓存第一次读的结果。
            // 以后读取时如果值发生变化，或者写入新值，则只能忽略后续修改。
            reactiveCache.computeIfAbsent(reactive as Reactive<Any?>) {
                if (reactive.type.rawClass.isFinal) {
                    FixedReactiveCacheImpl(value)
                } else {
                    ProxyReactiveCacheImpl(value)
                }
            }
        }

        override fun onFunctionCall(context: ReactiveCallContext<Any?>) {
            val cache = reactiveCache[context.reactive]
            require(cache is ProxyReactiveCacheImpl) { "Reactive value ${context.reactive} has not been cached previously." }

            if (context.context === this.context) {
                // 只在构建自己的视图的时候记录函数调用。
                cache.onFunctionCall(context)
            }

            val notChild = context.context?.allParents?.contains(this.context) != true
            if (autoFlush && !isFlushing && notChild) {
                // 其他时候，如果不是正在构建视图，且开启了自动刷新，则尝试刷新。
                tryFlush(context.reactive, (context.reactive as AbstractReactive).raw)
            }
        }

        override fun <T> tryFlush(reactive: Reactive<T>, value: T): Boolean {
            val cache = reactiveCache[reactive as Reactive<Any?>]
            requireNotNull(cache) { "Reactive value $reactive has not been cached previously." }

            if (isFlushing || cache.isReproducible(value)) {
                return false
            }
            println("=== flush context: ${context.id}")
            if (isFlushing || cache.isReproducible(value)) {
                return false
            }

            return tryFlush()
        }

        override fun tryFlush(): Boolean {
            if (isFlushing || viewFunctions.isEmpty) {
                return false
            }
            flush()
            return true
        }

        override fun flush() {
            if (isFlushing) {
                return
            }

            isFlushingNotInherit = true
            try {
                // 退出此前的视图上下文，进入新的视图上下文。
                viewContext?.exit()
                clearReactiveCache()

                if (viewFunctions.isEmpty) {
                    return
                }

                val context = context.enterChild(id = "View")
                val beanManager = InheritedMutableBeanManagerImpl(context)

                val viewContextLocal = ViewContextImpl(context, beanManager, this, this@ReactionModuleImpl)
                viewContext = viewContextLocal
                beanManager.addBeanByCompilationType(viewContextLocal)

                viewFunctions.forEach {
                    it.value.safeBuildView(viewContextLocal)
                }
            } finally {
                isFlushingNotInherit = false
            }
        }
    }

    override fun onContextInit(event: ContextInitEvent) {
        // 如果当前上下文里已经有一个 reactionManager 了，说明当前上下文就是一个 UI 上下文。
        val reactionManager = ReactionManagerImpl(
            event.context,
            event.context.reactionManagerOrNull as? ReactionManagerImpl
        )
        event.context.addBeanByCompilationType(reactionManager)
    }

    override fun onContextPostEnter(event: ContextPostEnterEvent) {
        val reactionManager = event.context.reactionManagerOrNull as? ReactionManagerImpl ?: return
        reactionManager.tryFlush()
    }
}