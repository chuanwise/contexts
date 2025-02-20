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

package cn.chuanwise.contexts.context

import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.TopologicalIterator
import cn.chuanwise.contexts.util.callByAndRethrowException
import cn.chuanwise.contexts.util.callSuspendByAndRethrowException
import cn.chuanwise.contexts.util.coroutineScopeOrNull
import cn.chuanwise.contexts.util.getBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.function.Consumer
import java.util.function.Function
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

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

@ContextsInternalApi
data class CoroutineScopeConfiguration(
    val id: String?,
    val primary: Boolean?,
    val runBlocking: Boolean?
)

@ContextsInternalApi
fun cn.chuanwise.contexts.util.CoroutineScope.toConfiguration() : CoroutineScopeConfiguration {
    return CoroutineScopeConfiguration(
        id = key,
        primary = primary.toBooleanOrNull(),
        runBlocking = runBlocking.toBooleanOrNull()
    )
}

@ContextsInternalApi
fun callFunctionAsync(
    context: Context,
    function: KFunction<*>,
    functionClass: Class<*>,
    arguments: Map<KParameter, Any?>,
    coroutineScopeConfiguration: CoroutineScopeConfiguration?,
    runBlocking: Boolean,
    onException: Consumer<Throwable>,
    onFinally: Runnable
) {
    if (function.isSuspend) {
        val block: suspend CoroutineScope.() -> Unit = {
            try {
                function.callSuspendByAndRethrowException(arguments)
            } catch (e: Throwable) {
                onException.accept(e)
            } finally {
                onFinally.run()
            }
        }

        if (coroutineScopeConfiguration == null) {
            val coroutineScope = context.coroutineScopeOrNull
            if (coroutineScope == null) {
                if (runBlocking) {
                    context.contextManager.logger.warn {
                        "Suspend callback function ${function.name} declared in ${functionClass.simpleName} " +
                                "for context $context can not find a default coroutine scope. " +
                                "Make sure there is a coroutine scope available in current context, " +
                                "or use @CoroutineScope to specify a coroutine scope (if not). " +
                                "But it can be run in blocking mode, it still works but may cause performance issues. " +
                                "Details: " +
                                "function class: ${functionClass.name}, " +
                                "function: $function. "
                    }

                    runBlocking(block = block)
                } else {
                    onException.accept(IllegalStateException(
                        "Suspend callback function ${function.name} declared in ${functionClass.simpleName} " +
                                "for context $context can not find a default coroutine scope. " +
                                "Make sure there is a coroutine scope available in current context, " +
                                "or use @CoroutineScope to specify a coroutine scope (if not). " +
                                "It CAN NOT be called in blocking mode, so it will be ignored. " +
                                "Details: " +
                                "function class: ${functionClass.name}, " +
                                "function: $function. "
                    ))
                }
            } else {
                coroutineScope.launch(block = block)
            }
        } else {
            val coroutineScope = coroutineScopeConfiguration.let {
                context.getBean<CoroutineScope>(id = it.id, primary = it.primary)
            } ?: context.coroutineScopeOrNull

            if (coroutineScope == null) {
                if (runBlocking) {
                    when (coroutineScopeConfiguration.runBlocking) {
                        true -> {
                            context.contextManager.logger.warn {
                                "Suspend callback function ${function.name} declared in ${functionClass.simpleName} " +
                                        "for context $context can not find a default coroutine scope. " +
                                        "Make sure there is a coroutine scope with key = ${coroutineScopeConfiguration.id} " +
                                        "and primary = ${coroutineScopeConfiguration.primary} available in current context, " +
                                        "But it can be run in blocking mode, it still works but may cause performance issues. " +
                                        "Details: " +
                                        "function class: ${functionClass.name}, " +
                                        "function: $function. "
                            }
                            runBlocking(block = block)
                        }
                        false -> {
                            onException.accept(IllegalStateException(
                                "Suspend callback function ${function.name} declared in ${functionClass.simpleName} " +
                                        "for context $context can not find a default coroutine scope. " +
                                        "Make sure there is a coroutine scope with key = ${coroutineScopeConfiguration.id} " +
                                        "and primary = ${coroutineScopeConfiguration.primary} available in current context, " +
                                        "It CAN NOT be called in blocking mode (caused by runBlocking = false), so it will be ignored. " +
                                        "Details: " +
                                        "function class: ${functionClass.name}, " +
                                        "function: $function. "
                            ))
                        }
                        null -> runBlocking(block = block)
                    }
                } else {
                    onException.accept(IllegalStateException(
                        "Suspend callback function ${function.name} declared in ${functionClass.simpleName} " +
                                "for context $context can not find a default coroutine scope. " +
                                "Make sure there is a coroutine scope with key = ${coroutineScopeConfiguration.id} " +
                                "and primary = ${coroutineScopeConfiguration.primary} available in current context, " +
                                "But it CAN NOT BE run in blocking mode (caused by caller), so it will be ignored. " +
                                "Details: " +
                                "function class: ${functionClass.name}, " +
                                "function: $function. "
                    ))
                }
            } else {
                coroutineScope.launch(block = block)
            }
        }
    } else {
        try {
            function.callByAndRethrowException(arguments)
        } catch (e: Throwable) {
            onException.accept(e)
        } finally {
            onFinally.run()
        }
    }
}

@ContextsInternalApi
fun <T> callFunctionSync(
    context: Context,
    function: KFunction<T>,
    functionClass: Class<*>,
    arguments: Map<KParameter, Any?>,
    coroutineScopeConfiguration: CoroutineScopeConfiguration?,
    onException: Function<Throwable, T>,
    onFinally: Runnable
): T {
    if (function.isSuspend) {
        val block: suspend CoroutineScope.() -> T = {
            try {
                function.callSuspendByAndRethrowException(arguments)
            } catch (e: Throwable) {
                onException.apply(e)
            } finally {
                onFinally.run()
            }
        }

        if (coroutineScopeConfiguration != null) {
            context.contextManager.logger.warn {
                "Suspend callback function ${function.name} declared in ${functionClass.simpleName} " +
                        "for context $context are required to run in blocking mode by caller. " +
                        "@CoroutineScope will be ignored. " +
                        "Details: " +
                        "function class: ${functionClass.name}, " +
                        "function: $function. " +
                        "coroutine scope key: ${coroutineScopeConfiguration.id}, " +
                        "coroutine scope primary: ${coroutineScopeConfiguration.primary}, " +
                        "coroutine scope runBlocking: ${coroutineScopeConfiguration.runBlocking}."
            }
        }

        return runBlocking(block = block)
    } else {
        return try {
            function.callByAndRethrowException(arguments)
        } catch (e: Throwable) {
            onException.apply(e)
        } finally {
            onFinally.run()
        }
    }
}