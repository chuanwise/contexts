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

import cn.chuanwise.contexts.reactions.proxy.NoOperationProxyHandler
import cn.chuanwise.contexts.reactions.proxy.ProxyFactoryImpl
import cn.chuanwise.contexts.reactions.proxy.createProxy
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ContextsInternalApi::class)
class ProxyFactoryTest {
    private val proxyFactory = ProxyFactoryImpl(proxyClassLoader = ProxyFactoryTest::class.java.classLoader)

    interface Foo {
        fun fooFunLikeToString(): String
        fun fooFun(t: Int)
    }

    object FooImpl : Foo {
        override fun fooFunLikeToString(): String {
            return "Foo fun like toString() called"
        }

        override fun fooFun(t: Int) {
            println("Foo fun called with t = $t")
        }
    }

    @Test
    fun testProxyCreate() {
        val foo = proxyFactory.createProxy<Foo>(FooImpl, NoOperationProxyHandler).valueProxy
        assertEquals("Proxy(value=$FooImpl)", foo.toString())

        val charSequence = proxyFactory.createProxy<CharSequence>("114", NoOperationProxyHandler).valueProxy
        assertEquals("114", charSequence.toString())
    }
}