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

package cn.chuanwise.contexts.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResolvableTypeTest {
    @Test
    fun testRawClass() {
        val stringType = createResolvableType<String>()
        assertEquals(String::class, stringType.rawClass)
    }

    interface FixedList<T>
    interface StringFixedList : FixedList<String>
    object StringFixedListImpl : StringFixedList

    interface A<out T>
    interface B<G : CharSequence, U : G> : A<U>
    interface C : B<String, String>

    @Test
    fun testNullSafe() {
        val nullableString = createResolvableType<String?>()
        val string = createResolvableType<String>()

        assertFalse(string.isAssignableFrom(nullableString))
        assertTrue(nullableString.isAssignableFrom(string))
    }

    @Test
    fun testABC() {
        val a = createResolvableType<A<String>>()
        val b = createResolvableType<B<String, String>>()
        val c = createResolvableType<C>()

        assertEquals(String::class, a.getTypeParameterOrFail(A::class, "T").type!!.rawClass)
        assertEquals(String::class, b.getTypeParameterOrFail(A::class, "T").type!!.rawClass)

        assertEquals(String::class, b.getTypeParameterOrFail(B::class, "G").type!!.rawClass)
        assertEquals(String::class, b.getTypeParameterOrFail(B::class, "U").type!!.rawClass)

        assertEquals(String::class, c.getTypeParameterOrFail(A::class, "T").type!!.rawClass)
        assertEquals(String::class, b.getTypeParameterOrFail(B::class, "U").type!!.rawClass)
        assertEquals(String::class, b.getTypeParameterOrFail(B::class, "G").type!!.rawClass)
    }

    @Test
    fun testParameterizedClass() {
        val anyList = createResolvableType<List<Any>>()
        val stringList = createResolvableType<List<String>>()

        assertFalse(anyList.isAssignableFrom(stringList))

        val anyFixedList = createResolvableType<FixedList<Any>>()
        val stringFixedList = createResolvableType<StringFixedList>()

        assertFalse(anyFixedList.isAssignableFrom(stringFixedList))
    }

    @Test
    fun testResolve() {
        val anyFixedList = createResolvableType<FixedList<Any>>()
        val stringFixedList = createResolvableType<FixedList<String>>()
        val stringFixedListImpl = createResolvableType<StringFixedListImpl>()

        assertFalse(anyFixedList.isAssignableFrom(stringFixedList))
        assertFalse(anyFixedList.isAssignableFrom(stringFixedListImpl))

        assertTrue(stringFixedList.isAssignableFrom(stringFixedListImpl))
    }

    @Test
    fun testList() {
        val stringListMap = createResolvableType<Map<String, List<String>>>()

        val keyType = stringListMap.getTypeParameterOrFail(Map::class, 0).type
        assertEquals(createResolvableType<String>(), keyType)

        val stringList = stringListMap.getTypeParameterOrFail(Map::class, "V").type!!
        assertEquals(createResolvableType<List<String>>(), stringList)

        val stringType = stringList.getTypeParameterOrFail(List::class, 0).type
        assertEquals(createResolvableType<String>(), stringType)
    }

    @Test
    fun testArray() {
        val intArray = createResolvableType<IntArray>()
        val longArray = createResolvableType<LongArray>()

        assertFalse(intArray.isAssignableFrom(longArray))
        assertFalse(longArray.isAssignableFrom(intArray))

        val nullableIntArray = createResolvableType<Array<Int?>>()
        val nullableDoubleArray = createResolvableType<Array<Double?>>()

        assertFalse(nullableIntArray.isAssignableFrom(nullableDoubleArray))
        assertFalse(nullableDoubleArray.isAssignableFrom(nullableIntArray))
    }
}