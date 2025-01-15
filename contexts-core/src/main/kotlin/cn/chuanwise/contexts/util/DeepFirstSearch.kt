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
import java.util.ArrayDeque
import java.util.Deque

/**
 * 深度优先搜索迭代器。
 *
 * @author Chuanwise
 */
interface DeepFirstSearch : Iterator<Context>

/**
 * 创建一个深度优先搜索迭代器。
 *
 * @param stack 搜索栈。
 * @param visited 已访问的上下文。
 * @return 深度优先搜索迭代器。
 */
@JvmOverloads
@OptIn(ContextsInternalApi::class)
fun DeepFirstSearch(
    stack: Deque<Context>,
    visited: MutableSet<Context> = mutableSetOf()
): DeepFirstSearch = DeepFirstSearchImpl(stack, visited)

/**
 * 创建一个深度优先搜索迭代器。
 *
 * @param root 根上下文。
 * @param visited 已访问的上下文。
 * @return 深度优先搜索迭代器。
 */
@JvmOverloads
@OptIn(ContextsInternalApi::class)
fun DeepFirstSearch(
    root: Context,
    visited: MutableSet<Context> = mutableSetOf()
): DeepFirstSearch = DeepFirstSearchImpl(ArrayDeque<Context>().apply { add(root) }, visited)

@ContextsInternalApi
class DeepFirstSearchImpl(
    private val stack: Deque<Context>,
    private val visited: MutableSet<Context>
) : DeepFirstSearch {
    override fun hasNext(): Boolean {
        if (stack.isEmpty()) {
            return false
        }
        var context: Context
        do {
            context = stack.peekLast()
            if (context !in visited) {
                return true
            }
        } while (stack.poll() != null)
        return false
    }

    override fun next(): Context {
        check(hasNext()) { "No more elements" }
        val next = stack.pop()
        visited.add(next)
        for (child in next.children) {
            if (child !in visited) {
                stack.push(child)
            }
        }
        return next
    }
}