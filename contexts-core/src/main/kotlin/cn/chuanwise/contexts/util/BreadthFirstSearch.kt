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

import cn.chuanwise.contexts.Context
import java.util.Deque
import java.util.ArrayDeque

/**
 * 广度优先搜索迭代器。
 *
 * @author Chuanwise
 */
interface BreadthFirstSearch : Iterator<Context>

/**
 * 创建一个广度优先搜索迭代器。
 *
 * @param queue 搜索队列。
 * @param visited 已访问的上下文。
 * @return 广度优先搜索迭代器。
 */
@JvmOverloads
@OptIn(ContextsInternalApi::class)
fun BreadthFirstSearch(
    queue: Deque<Context>,
    visited: MutableSet<Context> = mutableSetOf()
): BreadthFirstSearch = BreadthFirstSearchImpl(queue, visited)

/**
 * 创建一个广度优先搜索迭代器。
 *
 * @param root 根上下文。
 * @param visited 已访问的上下文。
 * @return 广度优先搜索迭代器。
 */
@JvmOverloads
@OptIn(ContextsInternalApi::class)
fun BreadthFirstSearch(
    root: Context,
    visited: MutableSet<Context> = mutableSetOf()
): BreadthFirstSearch = BreadthFirstSearchImpl(ArrayDeque<Context>().apply { add(root) }, visited)

@ContextsInternalApi
class BreadthFirstSearchImpl(
    private val queue: Deque<Context>,
    private val visited: MutableSet<Context>
) : BreadthFirstSearch {
    override fun hasNext(): Boolean {
        if (queue.isEmpty()) {
            return false
        }
        var context: Context
        do {
            context = queue.peek()
            if (context !in visited) {
                return true
            }
        } while (queue.poll() != null)
        return false
    }

    override fun next(): Context {
        check(hasNext()) { "No more elements" }
        val context = queue.poll()
        visited.add(context)
        for (child in context.children) {
            if (child !in visited) {
                queue.add(child)
            }
        }
        return context
    }
}