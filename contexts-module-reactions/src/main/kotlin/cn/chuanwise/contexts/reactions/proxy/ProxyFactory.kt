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

package cn.chuanwise.contexts.reactions.proxy

import cn.chuanwise.contexts.reactions.view.Ignore
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.NotStableForInheritance
import cn.chuanwise.contexts.util.getDeclaredFieldOrNull
import cn.chuanwise.contexts.util.getDeclaredMethodOrNull
import cn.chuanwise.contexts.util.getMethodOrNull
import cn.chuanwise.contexts.util.getSafe
import cn.chuanwise.typeresolver.ResolvableType
import cn.chuanwise.typeresolver.TypeResolver
import cn.chuanwise.typeresolver.createTypeResolver
import cn.chuanwise.typeresolver.resolveByOuterType
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.dynamic.Transformer.ForField
import net.bytebuddy.implementation.FieldAccessor
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.AllArguments
import net.bytebuddy.implementation.bind.annotation.FieldValue
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.matcher.ElementMatchers
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * 代理工厂，用于把给定对象包装为一个代理。
 *
 * @author Chuanwise
 */
@NotStableForInheritance
interface ProxyFactory {
    fun <T : Any> createProxy(type: ResolvableType<T>, value: T, handler: ProxyHandler<T>) : Proxy<T>
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class ProxyFactoryImpl(
    proxyClassCacheCapacity: Int = 1024,
    private val typeResolver: TypeResolver = createTypeResolver(),
    private val proxyClassLoader: ClassLoader? = null
) : ProxyFactory {
    companion object {
        /**
         * 每个代理类型必须有一个 public 的实例属性，名字如下，存储对应的 [Proxy] 引用。
         */
        private const val PROXY_FIELD_NAME = "__proxy__"

        /**
         * 如果一个对象是一个代理，则返回代理对象，其中 [Proxy.value] 即当前值；否则返回 `null`。
         *
         * @param T 代理对象类型
         * @return 代理对象，或 `null`
         */
        @OptIn(ContextsInternalApi::class)
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> tryResolveAsProxy(valueProxy: T) : Proxy<T>? {
            val field = valueProxy::class.java.getDeclaredFieldOrNull(PROXY_FIELD_NAME) ?: return null
            val proxy = field.getSafe(valueProxy) as? Proxy<T> ?: return null
            return if (proxy.valueProxy === valueProxy) proxy else null
        }
    }

    private class ProxyCallImpl<T : Any>(
        override val proxy: Proxy<T>,
        override val arguments: Array<Any?>,
        override val rawMethod: Method,
        override val returnType: ResolvableType<*>?,
        override val rawFunction: KFunction<*>?,
        override val rawPropertyToGet: KProperty<*>?,
        override val rawPropertyToSet: KMutableProperty<*>?
    ) : ProxyCall<T> {
        override fun call(value: T): Any? {
            return rawMethod.invoke(value, *arguments)
        }

        override fun toString(): String {
            return "ProxyCall(rawMethod=$rawMethod, arguments=${arguments.contentToString()}, value=${proxy.value})"
        }
    }

    private inner class ProxyImpl<T: Any>(
        override val type: ResolvableType<T>,
        override val value: T,
        private val proxyClass: ProxyClass<T>,
        private val proxyType: ProxyClass<T>.ProxyType,
        override var proxyHandler: ProxyHandler<T>
    ) : Proxy<T> {
        override lateinit var valueProxy: T

        private val Method.rawFunctionOrNull: KFunction<*>?
            get() = try { kotlinFunction } catch (e: Error) {
                null // may raise KotlinReflectionInternalError
            }

        fun onCall(method: Method, arguments: Array<Any?>) : Any? {
            val rawFunction = method.rawFunctionOrNull

            val returnType: ResolvableType<*>? = proxyType.resolveReturnType(method, rawFunction)
            val call = ProxyCallImpl(
                this, arguments, method, returnType, rawFunction,
                proxyClass.rawMethodToGetter[method], proxyClass.rawMethodToSetter[method]
            )

            return proxyHandler.onCall(call)
        }

        fun onToString(): String {
            return "Proxy(value=$value)"
        }
    }

    // public 的原因是让 ByteBuddy 生成的类可以访问这里的方法。
    class ProxyClassInterceptor {
        companion object {
            @JvmStatic
            @RuntimeType
            @ContextsInternalApi
            fun onCallIntercepted(
                @Origin method: Method, @AllArguments arguments: Array<Any?>,
                @FieldValue(PROXY_FIELD_NAME) proxy: Proxy<*>
            ) : Any? {
                require(proxy is ProxyImpl) { "The proxy must be an instance of ProxyImpl." }
                return proxy.onCall(method, arguments)
            }
        }
    }

    class ProxyClassToStringInterceptor {
        companion object {
            @JvmStatic
            @RuntimeType
            @ContextsInternalApi
            fun onToStringIntercepted(@FieldValue(PROXY_FIELD_NAME) proxy: Proxy<*>) : String {
                require(proxy is ProxyImpl) { "The proxy must be an instance of ProxyImpl." }
                return proxy.onToString()
            }
        }
    }

    // 用于表示无法推导函数的返回值。
    private object NotResolvable

    private inner class ProxyClass<T : Any>(javaClass: Class<T>) {
        private val proxyJavaClass = javaClass.toProxyClass(proxyClassLoader ?: javaClass.classLoader)
        private val proxyJavaClassConstructor = proxyJavaClass.getConstructor()

        private val proxyField = proxyJavaClass.getDeclaredField(PROXY_FIELD_NAME)

        val rawMethodToGetter = javaClass.kotlin.memberProperties.associateBy { it.getter.javaMethod }
        val rawMethodToSetter = javaClass.kotlin.memberProperties
            .filterIsInstance<KMutableProperty<*>>()
            .associateBy { it.setter.javaMethod }

        // 缓存每个类型每个函数的返回值类型，避免每次都要推导。
        inner class ProxyType(val type: ResolvableType<T>) {
            // 值虽然是 Any，但是只有两种可能：NotResolvable 或 ResolvableType<*>。
            val rawMethodToReturnType = ConcurrentHashMap<Method, Any>()

            private fun resolveReturnTypeNoCache(rawMethod: Method, rawFunction: KFunction<*>?) : ResolvableType<*>? {
                // 如果有对应的 Kotlin Function，直接获取返回值类型。
                if (rawFunction != null) {
                    type.memberFunctionsByRawFunction[rawFunction]?.returnType?.let { return it }
                }

                // 如果是属性 Getter 或 Setter，获取对应的属性。
                val gettingProperty = rawMethodToGetter[rawMethod]
                if (gettingProperty != null) {
                    type.memberPropertiesByRawProperty[gettingProperty]?.type?.let { return it }
                }

                val settingProperty = rawMethodToSetter[rawMethod]
                if (settingProperty != null) {
                    type.memberPropertiesByRawProperty[settingProperty]?.type?.let { return it }
                }

                // 只能直接推导类型。
                typeResolver.resolveByOuterType(type, rawMethod.genericReturnType)?.let { return it }

                // 拼尽全力无法解析，只能返回 null。
                return null
            }

            // 从缓存里获取返回值类型，或进行一次推导尝试。
            fun resolveReturnType(rawMethod: Method, rawFunction: KFunction<*>?): ResolvableType<*>? {
                return rawMethodToReturnType.computeIfAbsent(rawMethod) {
                    resolveReturnTypeNoCache(rawMethod, rawFunction) ?: NotResolvable
                } as? ResolvableType<*>
            }
        }
        val proxyTypes = ConcurrentHashMap<ResolvableType<T>, ProxyType>()

        private fun Class<T>.toProxyClass(proxyClassLoader: ClassLoader): Class<T> {
            // 如果代理类型是接口，我们不代理 toString() 方法，所以可以在 toString() 变成输出代理信息的地方。
            return ByteBuddy()
                .subclass(this)

                // 定义属性以便支持通过代理对象获取代理属性。
                .defineField(PROXY_FIELD_NAME, Proxy::class.java, Visibility.PUBLIC)

                // 如果类型没有定义 toString() 函数，则拦截 toString 调用，以便输出代理信息。
                .run {
                    val methodDelegation: MethodDelegation = if (getMethodOrNull("toString") == null) {
                        MethodDelegation.to(ProxyClassToStringInterceptor::class.java)
                    } else {
                        MethodDelegation.to(ProxyClassInterceptor::class.java)
                    }

                    method(ElementMatchers.isToString()).intercept(methodDelegation)
                }

                // 拦截其他定义的方法。
                .method(
                    ElementMatchers.isDeclaredBy<MethodDescription?>(this).and(
                        ElementMatchers.not(ElementMatchers.isAnnotatedWith(Ignore::class.java))
                    )
                )
                .intercept(MethodDelegation.to(ProxyClassInterceptor::class.java))
                .make()
                .load(proxyClassLoader)
                .loaded as Class<T>
        }

        fun createProxy(type: ResolvableType<T>, value: T, handler: ProxyHandler<T>): Proxy<T> {
            val proxyType = proxyTypes.computeIfAbsent(type) { ProxyType(type) }
            val proxy = ProxyImpl(type, value, this, proxyType, handler)

            val valueProxy = proxyJavaClassConstructor.newInstance()
            proxy.valueProxy = valueProxy
            proxyField.set(valueProxy, proxy)

            return proxy
        }
    }

    private val proxyClassCache = ConcurrentHashMap<Class<*>, ProxyClass<*>>()

    override fun <T : Any> createProxy(type: ResolvableType<T>, value: T, handler: ProxyHandler<T>): Proxy<T> {
        require(!type.rawClass.isFinal) { "Cannot proxy final class: ${type.rawClass}" }
        val proxyClass = proxyClassCache.computeIfAbsent(type.rawClass.java) { ProxyClass(it) } as ProxyClass<T>
        return proxyClass.createProxy(type, value, handler)
    }
}