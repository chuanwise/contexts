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

import org.junit.jupiter.api.Test

class TopologicalIteratorTest {
    data class Node(
        val key: String,
    ) {
        val parents: MutableList<Node> = mutableListOf()
        val children: MutableList<Node> = mutableListOf()
    }

    private fun connect(parent: Node, child: Node) {
        parent.children.add(child)
        child.parents.add(parent)
    }

    private fun <T> List<T>.assertRelativeLocation(element: T, before: T) {
        val index = indexOf(element)
        val beforeIndex = indexOf(before)
        assert(index < beforeIndex) {
            "Expected $element to be before $before, but found $index and $beforeIndex"
        }
    }

    @Test
    @OptIn(ContextsInternalApi::class)
    fun testTopologicalSortingIterator() {
        val a = Node("A")
        val b = Node("B")
        val c = Node("C")
        val d = Node("D")
        val e = Node("E")
        val f = Node("F")

        val nodes = listOf(a, b, c, d, e, f)

        connect(a, b)
        connect(a, c)
        connect(a, d)
        connect(b, e)
        connect(c, e)
        connect(c, f)

        val childToParentKeys = TopologicalIterator(
            nodes.associateWith { it.children.size }
        ) { it.parents }
            .asSequence()
            .map { it.key }
            .toList()

        childToParentKeys.assertRelativeLocation("E", "B")
        childToParentKeys.assertRelativeLocation("E", "C")
        childToParentKeys.assertRelativeLocation("F", "C")
        childToParentKeys.assertRelativeLocation("B", "A")
        childToParentKeys.assertRelativeLocation("C", "A")
        childToParentKeys.assertRelativeLocation("D", "A")

        val parentToChildKeys =
            TopologicalIterator(
                nodes.associateWith { it.parents.size }
            ) { it.children }
                .asSequence()
                .map { it.key }
                .toList()

        parentToChildKeys.assertRelativeLocation("A", "B")
        parentToChildKeys.assertRelativeLocation("A", "C")
        parentToChildKeys.assertRelativeLocation("A", "D")
        parentToChildKeys.assertRelativeLocation("B", "E")
        parentToChildKeys.assertRelativeLocation("C", "E")
        parentToChildKeys.assertRelativeLocation("C", "F")
    }
}