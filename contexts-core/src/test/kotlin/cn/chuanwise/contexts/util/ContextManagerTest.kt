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

import cn.chuanwise.contexts.context.ContextManager
import cn.chuanwise.contexts.context.createContextManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContextManagerTest {
    private lateinit var contextManager: ContextManager

    @BeforeEach
    fun beforeEach() {
        contextManager = createContextManager()
    }

    @AfterEach
    fun afterEach() {
        contextManager.close()
    }

    @Test
    fun testAddParent() {
        val a = contextManager.enterRoot(key = "A")
        val b = contextManager.enterRoot(key = "B")

        b.addParent(a)

        assertEquals(b, a.children.single())
        assertEquals(a, b.singleParent)
    }

    @Test
    fun testAddTwoChildren() {
        val a = contextManager.enterRoot(key = "A")

        val b = a.enterChild(key = "B")
        val c = a.enterChild(key = "C")

        assertEquals(2, a.childCount)
        assertEquals(b, a.getChildByKey("B"))
        assertEquals(c, a.getChildByKey("C"))
    }

    @Test
    fun testAddTwoChildrenDeeper() {
        val a = contextManager.enterRoot(key = "A")

        val b = a.enterChild(key = "B")
        val c = b.enterChild(key = "C")
        val d = b.enterChild(key = "D")
        val e = b.enterChild(key = "E")

        assertEquals(1, a.childCount)
        assertEquals(b, a.getChildByKey("B"))

        assertEquals(3, b.childCount)
        assertEquals(c, b.getChildByKey("C"))
        assertEquals(d, b.getChildByKey("D"))
        assertEquals(e, b.getChildByKey("E"))

        assertEquals(setOf(b, c, d, e), a.allChildren.toSet())
        assertEquals(setOf(c, d, e), b.allChildren.toSet())

        assertEquals(setOf(a, b), c.allParents.toSet())
        assertEquals(setOf(a, b), d.allParents.toSet())
        assertEquals(setOf(a, b), e.allParents.toSet())
    }

    @Test
    fun testAddTwoParent() {
        val a = contextManager.enterRoot(key = "A")
        val b = contextManager.enterRoot(key = "B")

        val c = contextManager.enterRoot(key = "C")

        c.addParent(a)
        c.addParent(b)

        assertEquals(1, a.childCount)
        assertEquals(1, b.childCount)

        assertEquals(c, a.getChildByKey("C"))
        assertEquals(c, b.getChildByKey("C"))

        assertEquals(setOf(a, b), c.allParents.toSet())
        assertEquals(setOf(a, b), c.parents.toSet())
    }
}