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

package cn.chuanwise.contexts.reactions.model

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.reactions.ReactionModule
import cn.chuanwise.contexts.reactions.model.ModelHandlerImpl.ProxyContext
import cn.chuanwise.contexts.reactions.reactionManager
import cn.chuanwise.contexts.reactions.reactionModule
import cn.chuanwise.contexts.util.ContextsInternalApi
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.AllArguments
import net.bytebuddy.implementation.bind.annotation.Argument
import net.bytebuddy.implementation.bind.annotation.FieldValue
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import net.bytebuddy.implementation.bind.annotation.This
import net.bytebuddy.matcher.ElementMatchers
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

private const val PROXY_CLASS_PROXY_CONTEXT_FIELD_NAME = "__proxy_context"

/**
 * 默认的模型处理器实现。
 *
 * 对于非 `final` 类型或接口：
 *
 * ```kt
 * interface SomeClass {
 *     fun foo(): String
 * }
 * ```
 *
 * 处理器会生成形如下面的代理类：
 *
 * ```kt
 * open class SomeClassProxy(
 *     private val __proxy_context: ModelHandlerImpl.ProxyContext
 * ) {
 *     override fun foo(): String {
 *         return __proxy_context.onProxyMethodInvoked(...)
 *     }
 * }
 * ```
 *
 * 所有函数调用会被转发到对应的 [ProxyContext]，而对属性不做处理。
 *
 * @property proxyClassLoader 代理类加载器
 * @author Chuanwise
 */
@ContextsInternalApi
class ModelHandlerImpl(
    private val proxyClassLoader: ClassLoader
) : ModelHandler<Any?> {
    // 表示 Proxy 对象的一次函数调用，只在 ProxyContext 内使用。
    data class MethodCall(val method: Method, val args: Array<Any?>) {
        interface Result {
            val result: Any?
        }

        data class FixedResult(
            override val result: Any?
        ) : Result {
            companion object {
                private val NULL = FixedResult(null)

                fun of(result: Any?) : Result {
                    return if (result == null) NULL else FixedResult(result)
                }
            }
        }

        data class ProxyResult(
            val context: Context,
            override var result: Any?,
            val modelHandler: ModelHandler<Any?>
        ) : Result

        // 函数调用结果不参与 equals 和 hashCode 的计算。null 表示函数尚未调用。
        var result: Result? = null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MethodCall

            if (method != other.method) return false
            return args.contentEquals(other.args)
        }

        override fun hashCode(): Int {
            var result = method.hashCode()
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    // 每个代理对象都有一个对应的 ProxyContext，在这里记录代理对象的函数调用情况。
    inner class ProxyContext(
        private val expectClass: Class<*>,
        private val proxyClassContext: ProxyClassContext,
        private val context: Context,
        private val reactionModule: ReactionModule
    ) {
        lateinit var proxy: Any

        // 函数调用的返回值若没有 ModelHandler，则将其缓存在这里。
        // 在对象更新的时候，检查这些函数调用的结果是否改变，以决定是否需要重构状态。
        private val methodCalls = ConcurrentHashMap<MethodCall, MethodCall>()

        // 并不是代理类型的所有函数调用都需要记录。
        // 如果代理的对象是 List 类型，此时他的实际类型是 ArrayList，我们也只记录它对 List 接口的调用结果。
        // 如果它调用了 ArrayList 的方法，我们选择忽略，只有它调用的 List 接口里的方法才会被记录。
        private val methodCallShouldCache = ConcurrentHashMap<Method, Boolean>()
        private fun shouldCache(method: Method) : Boolean {
            return methodCallShouldCache.computeIfAbsent(method) {
                expectClass.isAssignableFrom(it.declaringClass)
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun onMethodInvoked(method: Method, args: Array<Any?>): Any? {
            if (!shouldCache(method)) {
                return method.invoke(proxy, *args)
            }

            val newCall = MethodCall(method, args)
            val oldCall = methodCalls[newCall]
            if (oldCall != null) {
                return oldCall.result
            }

            val result = method.invoke(proxy, *args)

            val expectClass = method.returnType as Class<Any?>
            val modelHandler = reactionModule.getModelHandler(expectClass)?.value

            newCall.result = if (modelHandler != null) {
                val proxy = modelHandler.toProxy(context, expectClass, result)
                MethodCall.ProxyResult(context, proxy, modelHandler)
            } else {
                MethodCall.FixedResult.of(result)
            }

            methodCalls[newCall] = newCall
            return result
        }

        // 在对象更新的时候，检查这些函数调用的结果是否改变，以决定是否需要重构状态。
        fun tryFlush(newValue: Any?) : Boolean {
            // 如果它变为 null，或者原本需要是 List，现在连 List 都不是了，就标记为脏。
            if (newValue == null || !expectClass.isInstance(newValue)) {
                flush()
                return true
            }

            // 否则逐个调用结果检查是否需要重构状态。
            var result = false

            try {
                for (methodCall in methodCalls.values) {
                    val newResult = methodCall.method.invoke(newValue, *methodCall.args)

                    when (val oldResult = methodCall.result) {
                        is MethodCall.FixedResult -> {
                            // 如果函数返回的值是一个普通的固定值，则当其发生变化的时候才需要重构状态。
                            if (oldResult.result != newResult) {
                                result = true
                            }
                        }
                        is MethodCall.ProxyResult -> {
                            // 否则，如果函数返回的值也是一个代理类型的值，则比较新旧值。
                            result = oldResult.modelHandler.tryFlush(oldResult.context, oldResult.result, newResult) || result
                        }
                        else -> error("Unknown result type: $oldResult.")
                    }
                }
            } catch (e: Throwable) {
                flush()
                throw e
            }

            if (result) {
                flush()
            }

            return result
        }

        fun flush() {
            context.reactionManager.flush()
        }
    }

    companion object {
        private val PROXY_CONTEXT_CLASS_ARRAY = arrayOf(ProxyContext::class.java)
    }

    // 不用 private 的原因是因为 ByteBuddy 生成的类需要访问
    class ProxyMethodInvokeDelegate {
        companion object {
            @JvmStatic
            @RuntimeType
            fun onProxyMethodInvoked(
                @Origin method: Method,
                @AllArguments args: Array<Any?>,
                @SuperCall call: Callable<Any?>,
                @FieldValue(PROXY_CLASS_PROXY_CONTEXT_FIELD_NAME) context: ProxyContext
            ) : Any? {
                return context.onMethodInvoked(method, args)
            }
        }
    }

    // 不用 private 的原因是因为 ByteBuddy 生成的类需要访问
    class ProxyConstructorInvokeDelegate {
        companion object {
            @JvmStatic
            fun onProxyConstructorInvoked(@This proxy: Any, @Argument(0) context: ProxyContext) {
                context.proxy = proxy
            }
        }
    }

    // 不用 private 的原因是因为 ByteBuddy 生成的类需要访问
    // dataClass 是数据的最终类型，例如可能是 List，也可能是 ArrayList
    inner class ProxyClassContext(dataClass: Class<*>) {
        private val proxyClass = ByteBuddy()
            .subclass(dataClass)
            .defineField(PROXY_CLASS_PROXY_CONTEXT_FIELD_NAME, Method::class.java, Modifier.PUBLIC)

            // 拦截所有不带 @Ignore 的、可以重写的函数，转发到 onProxyMethodInvoked 方法
            .method(
                ElementMatchers.isDeclaredBy<MethodDescription?>(dataClass)
                    .and(ElementMatchers.not(ElementMatchers.isFinal()))
                    .and(ElementMatchers.not(ElementMatchers.isNative()))
                    .and(ElementMatchers.not(ElementMatchers.isAnnotatedWith(Ignore::class.java)))
            )
            .intercept(MethodDelegation.to(ProxyMethodInvokeDelegate::class.java))

            // 定义构造函数，存储原始数据和 ProxyContext
            .defineConstructor()
            .withParameter(ProxyContext::class.java, PROXY_CLASS_PROXY_CONTEXT_FIELD_NAME)
            .intercept(MethodDelegation.to(ProxyConstructorInvokeDelegate::class.java))

            .make()
            .load(proxyClassLoader)
            .loaded
            .apply {
                proxyClassToProxyContexts[this] = this@ProxyClassContext
            }

        private val proxyClassConstructor = proxyClass.getDeclaredConstructor(*PROXY_CONTEXT_CLASS_ARRAY)

        fun newInstance(context: Context, expectClass: Class<*>, data: Any?): Any {
            val proxyContext = ProxyContext(expectClass, this, context, context.reactionModule)
            return proxyClassConstructor.newInstance(data, proxyContext)
        }
    }

    private val dataClassToProxyContexts = ConcurrentHashMap<Class<*>, ProxyClassContext>()
    private val proxyClassToProxyContexts = ConcurrentHashMap<Class<*>, ProxyClassContext>()

    private fun getOrCreateProxyClass(dataClass: Class<*>) : ProxyClassContext {
        return dataClassToProxyContexts.computeIfAbsent(dataClass) { ProxyClassContext(dataClass) }
    }

    override fun toProxy(context: Context, expectClass: Class<in Any?>, data: Any?): Any {
        return getOrCreateProxyClass(expectClass).newInstance(context, expectClass, data)
    }

    override fun toData(context: Context, proxy: Any?): Any {
        requireNotNull(proxy) { "Proxy should not be null." }
        val proxyContext = proxy::class.java.getDeclaredField(PROXY_CLASS_PROXY_CONTEXT_FIELD_NAME).get(proxy) as ProxyContext
        return proxyContext.proxy
    }

    override fun tryFlush(context: Context, oldProxy: Any?, newValue: Any?) : Boolean {
        if (oldProxy == newValue) {
            return false
        }
        requireNotNull(oldProxy) { "Old proxy should not be null." }

        val proxyContext = oldProxy::class.java.getDeclaredField(PROXY_CLASS_PROXY_CONTEXT_FIELD_NAME).get(oldProxy) as ProxyContext
        return proxyContext.tryFlush(newValue)
    }
}