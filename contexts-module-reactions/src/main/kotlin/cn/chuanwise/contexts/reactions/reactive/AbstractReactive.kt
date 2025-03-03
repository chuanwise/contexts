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

package cn.chuanwise.contexts.reactions.reactive

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.reactions.getBuildingReactionManager
import cn.chuanwise.contexts.reactions.reactionManagerOrNull
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.isFinal
import cn.chuanwise.typeresolver.ResolvableType
import cn.chuanwise.typeresolver.ResolvableTypeBuilder
import cn.chuanwise.typeresolver.createTypeResolver
import cn.chuanwise.typeresolver.resolve
import cn.chuanwise.typeresolver.resolveByOuterType
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.AllArguments
import net.bytebuddy.implementation.bind.annotation.FieldValue
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.matcher.ElementMatchers.isDeclaredBy
import net.bytebuddy.matcher.ElementMatchers.isFinal
import net.bytebuddy.matcher.ElementMatchers.not
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.Deque
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

@ContextsInternalApi
val primaryReadObserver = ThreadLocal<ReactiveReadObserver<Any?>?>()

@OptIn(ContextsInternalApi::class)
inline fun <T> ReactiveReadObserver<Any?>.withPrimaryReadObserver(block: () -> T): T {
    val backup = primaryReadObserver.get()
    primaryReadObserver.set(this)

    try {
        return block()
    } finally {
        if (backup == null) {
            primaryReadObserver.remove()
        } else {
            primaryReadObserver.set(backup)
        }
    }
}

@ContextsInternalApi
val readObservers = ThreadLocal<Deque<ReactiveReadObserver<Any?>>?>()

@OptIn(ContextsInternalApi::class)
inline fun <T> ReactiveReadObserver<Any?>.withReadObserver(block: () -> T) : T {
    var deque = readObservers.get()
    if (deque == null) {
        deque = ArrayDeque()
        readObservers.set(deque)
    }

    try {
        return block()
    } finally {
        check(deque.removeLast() === this) { "The last read observer is not the current read observer." }
        if (deque.isEmpty()) {
            readObservers.remove()
        }
    }
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
abstract class AbstractReactive<T>(
    override val type: ResolvableType<T>,
    private val proxyClassLoader: ClassLoader?
) : Reactive<T> {
    abstract val rawValue: T

    companion object {
        // 每个代理类型内都有一个对应的代理上下文，以便调用函数并执行代理方法。
        private const val PROXY_CONTEXT_FIELD_NAME = "__proxy_context__"

        @JvmStatic
        protected val typeResolver = createTypeResolver()

        private fun <T : Any> T.toProxyContextOrNull() : ProxyContext<T>? {
            if (javaClass.isFinal) {
                return null
            }

            return try {
                javaClass.getDeclaredField(PROXY_CONTEXT_FIELD_NAME).get(this) as ProxyContext<T>
            } catch (exception: NoSuchFieldException) {
                null
            }
        }

        class ProxyContext<T : Any>(
            val raw: T,
            val type: ResolvableType<T>,
            private val proxyClassContext: ProxyClassContext<T>,
            private val reactive: AbstractReactive<Any?>,
            private val observer: ReactiveCallObserver<Any?>,
            private var sourceResult: ReactiveCallContext<Any?>?,
            private var sourceRaw: ReactiveCallContext<Any?>?
        ) {
            lateinit var proxy: T

            fun onProxyMethodCall(method: Method, arguments: Array<Any?>) : Any? {
                val result: Any? = method.invoke(raw, *arguments)
                val context: Context? = getBuildingReactionManager()?.context

                val callContext = ReactiveCallContextImpl(
                    reactive, context, context?.reactionManagerOrNull, proxy, raw, method, arguments,
                    result, sourceResult, sourceRaw
                )

                sourceResult = null
                sourceRaw = callContext

                callContext.resultProxy = when {
                    result == null || result == raw || method.returnType.isFinal || result == Unit -> result
                    else -> {
                        val kotlinFunction = method.kotlinFunction

                        // 如果 kotlinFunction 为空，有两种可能。
                        // 一种是方法来自于 Mutable 集合，例如 MutableMap 的 put。另一种是方法是属性 Getter。
                        val compilationReturnType = if (kotlinFunction != null) {
                            type.memberFunctionsByRawFunction[kotlinFunction]?.returnType
                        } else {
                            val property = proxyClassContext.propertyGetterMethods[method] as? KProperty<*>
                            if (property != null) {
                                type.memberPropertiesByRawProperty[property]?.type
                            } else {
                                // 方法不是 Getter，只能此时被迫推导了。
                                typeResolver.resolveByOuterType(type, method.genericReturnType)
                            }
                        }

                        if (compilationReturnType == null) {
                            // 尝试构建实际类型的代理。
                            tryToProxyOrNull(
                                result, typeResolver.resolve(result::class as KClass<Any>),
                                reactive, observer, callContext
                            )
                        } else {
                            tryToProxyOrNull(result, compilationReturnType as ResolvableType<Any>, reactive, observer, callContext)
                        }
                    }
                }
                observer.onCall(callContext)

                return callContext.resultProxy
            }
        }

        class ProxyMethodCallInterceptor {
            companion object {
                @JvmStatic
                @RuntimeType
                fun interceptProxyMethodCall(
                    @Origin method: Method,
                    @AllArguments arguments: Array<Any?>,
                    @FieldValue(PROXY_CONTEXT_FIELD_NAME) context: ProxyContext<*>
                ) : Any? {
                    return context.onProxyMethodCall(method, arguments)
                }
            }
        }

        // 创建有关一个非 final 类型的代理类上下文。rawClass 必须是非 final 类型。
        class ProxyClassContext<T : Any>(rawClass: Class<T>, proxyClassLoader: ClassLoader) {
            @Suppress("UNCHECKED_CAST")
            private val proxyClass: Class<T> = ByteBuddy()
                .subclass(rawClass)

                .method(isDeclaredBy<MethodDescription?>(rawClass).and(not(isFinal())))
                .intercept(MethodDelegation.to(ProxyMethodCallInterceptor::class.java))

                .defineField(PROXY_CONTEXT_FIELD_NAME, ProxyContext::class.java, Visibility.PUBLIC)

                .make()
                .load(proxyClassLoader)
                .loaded as Class<T>

            private val proxyConstructor = proxyClass.getDeclaredConstructor()
            private val proxyContextField = proxyClass.getDeclaredField(PROXY_CONTEXT_FIELD_NAME)

            // 如果函数没有对应的 KFunction，说明可能是属性 Getter 或 Setter。此时可以通过这两个映射来获取属性。
            val propertyGetterMethods = rawClass.kotlin.memberProperties.associateBy { it.getter.javaMethod }
//            val propertySetterMethods = rawClass.kotlin.memberProperties.filterIsInstance<KMutableProperty<*>>().associateBy { it.setter.javaMethod }

            fun createProxy(
                raw: T,
                type: ResolvableType<T>,
                reactive: AbstractReactive<Any?>,
                observer: ReactiveCallObserver<Any?>,
                sourceResult: ReactiveCallContext<Any?>?
            ) : T {
                val context = ProxyContext(raw, type, this, reactive, observer, sourceResult, sourceRaw = null)
                val proxy = proxyConstructor.newInstance()

                proxyContextField.set(proxy, context)
                context.proxy = proxy

                return proxy
            }
        }

        private val proxyClassContexts = ConcurrentHashMap<Class<*>, ProxyClassContext<*>>()

        private fun <T : Any> tryToProxy(
            raw: T,
            type: ResolvableType<T>,
            reactive: AbstractReactive<Any?>,
            observer: ReactiveCallObserver<Any?>,
            source: ReactiveCallContext<Any?>?
        ) : T {
            if (type.rawClass.isFinal) {
                return raw
            }

            val proxyClassLoader = reactive.proxyClassLoader
            requireNotNull(proxyClassLoader) { "Proxy class loader must not be null." }

            val context = proxyClassContexts.computeIfAbsent(type.rawClass.java) {
                ProxyClassContext(it, proxyClassLoader)
            } as ProxyClassContext<T>

            // 如果 type 对应的是 Mutable 类型，内部解析的实际上是非 Mutable 类型，会带来许多问题。
            // 只能把非 Mutable 类型替换为 Mutable 类型。
            return context.createProxy(raw, type, reactive, observer, source)
        }

        private fun ResolvableTypeBuilder<*>.typeArguments(type: ResolvableType<*>) {
            type.typeArguments.forEach {
                val value = it.type
                requireNotNull(value)
                typeArgument(value, it.rawParameter.variance)
            }
        }

        private fun <T : Any> tryToRaw(proxy: T) : T {
            return proxy.toProxyContextOrNull()?.raw ?: proxy
        }

        fun <T> tryToProxyOrNull(
            raw: T,
            type: ResolvableType<T>,
            reactive: AbstractReactive<Any?>,
            observer: ReactiveCallObserver<Any?>,
            source: ReactiveCallContext<Any?>?
        ) : T {
            return if (raw == null) null as T else tryToProxy(raw, type as ResolvableType<T & Any>, reactive, observer, source)
        }

        fun <T> tryToRawOrNull(value: T) : T {
            return if (value == null) null as T else tryToRaw(value)
        }
    }

    protected fun onValueRead(value: T): T {
        val context = ReactiveReadContextImpl(this, value)

        val primaryObserver = primaryReadObserver.get() as? ReactiveReadObserver<T>
        if (primaryObserver != null) {
            primaryObserver.onRead(context)
        }

        val readObservers = readObservers.get()
        if (readObservers != null) {
            for (observer in readObservers.descendingIterator()) {
                observer as ReactiveReadObserver<T>
                observer.onRead(context)
            }
        }

        return context.value
    }
}