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

@ContextsInternalApi
class DeepFirstSearchImpl<T>(
    private val stack: Deque<T>,
    private val visited: MutableSet<T> = mutableSetOf(),
    private val neighbors: (T) -> Iterable<T>
) : Iterator<T> {
    override fun hasNext(): Boolean {
        if (stack.isEmpty()) {
            return false
        }
        var value: T
        do {
            value = stack.peekLast()
            if (value !in visited) {
                return true
            }
        } while (stack.poll() != null)
        return false
    }

    override fun next(): T {
        check(hasNext()) { "No more elements" }
        val value = stack.pop()
        visited.add(value)
        for (neighbor in neighbors(value)) {
            if (neighbor !in visited) {
                stack.push(neighbor)
            }
        }
        return value
    }
}

@OptIn(ContextsInternalApi::class)
fun Context.createAllChildrenDeepFirstSearchIterator(): Iterator<Context> {
    return DeepFirstSearchImpl(ArrayDeque(children), mutableSetOf(), Context::children)
}

@OptIn(ContextsInternalApi::class)
fun Context.createAllParentsDeepFirstSearchIterator(): Iterator<Context> {
    return DeepFirstSearchImpl(ArrayDeque(parents), mutableSetOf(), Context::parents)
}