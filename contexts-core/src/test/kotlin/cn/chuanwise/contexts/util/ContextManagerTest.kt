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
        val a = contextManager.enterRoot(id = "A")
        val b = contextManager.enterRoot(id = "B")

        b.addParent(a)

        assertEquals(b, a.children.single())
        assertEquals(a, b.singleParent)
    }

    @Test
    fun testAddTwoChildren() {
        val a = contextManager.enterRoot(id = "A")

        val b = a.enterChild(id = "B")
        val c = a.enterChild(id = "C")

        assertEquals(2, a.childCount)
        assertEquals(b, a.getChildById("B"))
        assertEquals(c, a.getChildById("C"))
    }

    @Test
    fun testAddTwoChildrenDeeper() {
        val a = contextManager.enterRoot(id = "A")

        val b = a.enterChild(id = "B")
        val c = b.enterChild(id = "C")
        val d = b.enterChild(id = "D")
        val e = b.enterChild(id = "E")

        assertEquals(1, a.childCount)
        assertEquals(b, a.getChildById("B"))

        assertEquals(3, b.childCount)
        assertEquals(c, b.getChildById("C"))
        assertEquals(d, b.getChildById("D"))
        assertEquals(e, b.getChildById("E"))

        assertEquals(setOf(b, c, d, e), a.allChildren.toSet())
        assertEquals(setOf(c, d, e), b.allChildren.toSet())

        assertEquals(setOf(a, b), c.allParents.toSet())
        assertEquals(setOf(a, b), d.allParents.toSet())
        assertEquals(setOf(a, b), e.allParents.toSet())
    }

    @Test
    fun testAddTwoParent() {
        val a = contextManager.enterRoot(id = "A")
        val b = contextManager.enterRoot(id = "B")

        val c = contextManager.enterRoot(id = "C")

        c.addParent(a)
        c.addParent(b)

        assertEquals(1, a.childCount)
        assertEquals(1, b.childCount)

        assertEquals(c, a.getChildById("C"))
        assertEquals(c, b.getChildById("C"))

        assertEquals(setOf(a, b), c.allParents.toSet())
        assertEquals(setOf(a, b), c.parents.toSet())
    }
}