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

@file:JvmName("Contexts")

package cn.chuanwise.contexts

import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.TopologicalIterator

private fun Set<Context>.buildParentNeighbors(): Map<Context, List<Context>> {
    return associateWith {
        it.parents.filter { parent -> parent in this }
    }
}
private fun Set<Context>.buildChildNeighbors(): Map<Context, List<Context>> {
    return associateWith {
        it.children.filter { child -> child in this }
    }
}
private fun Map<Context, List<Context>>.neighborsToParentFunction(): (Context) -> List<Context> {
    val results = mutableMapOf<Context, MutableList<Context>>()
    entries.forEach { (context, neighbors) ->
        for (neighbor in neighbors) {
            results.computeIfAbsent(neighbor) { mutableListOf() }.add(context)
        }
    }
    return { results[it] ?: emptyList() }
}

@OptIn(ContextsInternalApi::class)
fun Context.createParentToChildTopologicalIteratorInAllParents(): Iterator<Context> {
    val neighbors = allParents.toSet().buildParentNeighbors()
    return TopologicalIterator(
        counts = neighbors.mapValues { it.value.size },
        parents = neighbors.neighborsToParentFunction()
    )
}

@OptIn(ContextsInternalApi::class)
fun Context.createChildToParentTopologicalIteratorInAllParents(): Iterator<Context> {
    val neighbors = allParents.toSet().buildChildNeighbors()
    return TopologicalIterator(
        counts = neighbors.mapValues { it.value.size },
        parents = neighbors.neighborsToParentFunction()
    )
}

@OptIn(ContextsInternalApi::class)
fun Context.createParentToChildTopologicalIteratorInAllChildren(): Iterator<Context> {
    val neighbors = allChildren.toSet().buildParentNeighbors()
    return TopologicalIterator(
        counts = neighbors.mapValues { it.value.size },
        parents = neighbors.neighborsToParentFunction()
    )
}

@OptIn(ContextsInternalApi::class)
fun Context.createChildToParentTopologicalIteratorInAllChildren(): Iterator<Context> {
    val neighbors = allChildren.toSet().buildChildNeighbors()
    return TopologicalIterator(
        counts = neighbors.mapValues { it.value.size },
        parents = neighbors.neighborsToParentFunction()
    )
}