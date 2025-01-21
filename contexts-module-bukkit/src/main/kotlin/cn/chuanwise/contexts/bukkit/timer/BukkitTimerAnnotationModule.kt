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

package cn.chuanwise.contexts.bukkit.timer

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.annotations.ArgumentResolver
import cn.chuanwise.contexts.annotations.ArgumentResolveContextImpl
import cn.chuanwise.contexts.annotations.DefaultArgumentResolverFactory
import cn.chuanwise.contexts.annotations.annotationModule
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.module.ModulePreEnableEvent
import cn.chuanwise.contexts.module.addDependencyModuleClass
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeans
import cn.chuanwise.contexts.util.callSuspendByAndRethrowException
import cn.chuanwise.contexts.util.coroutineScopeOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

interface BukkitTimerAnnotationModule : Module

@ContextsInternalApi
class BukkitTimerAnnotationModuleImpl : BukkitTimerAnnotationModule, Module {
    private class ReflectConsumerImpl(
        private val context: Context,
        private val functionClass: Class<*>,
        private val function: KFunction<*>,
        private val argumentResolvers: Map<KParameter, ArgumentResolver>,
        private val async: Boolean
    ) : Consumer<BukkitTask> {
        override fun accept(t: BukkitTask) {
            val beans = InheritedMutableBeans(context).apply {
                registerBean(t)
            }
            val arguments = argumentResolvers.mapValues { it.value.resolveArgument(beans) }

            if (function.isSuspend) {
                val block: suspend CoroutineScope.() -> Unit = {
                    try {
                        function.callSuspendByAndRethrowException(arguments)
                    } catch (e: Throwable) {
                        onExceptionOccurred(e, t)
                    }
                }

                if (async) {
                    val coroutineScope = beans.coroutineScopeOrNull
                    if (coroutineScope == null) {
                        context.contextManager.logger.warn {
                            "Function ${function.name} in class ${functionClass.simpleName} is suspend, " +
                                    "but no coroutine scope found. It will blocking caller thread. " +
                                    "Details: " +
                                    "function class: ${functionClass.name}, " +
                                    "function: $function. "
                        }
                        runBlocking(block = block)
                    } else {
                        coroutineScope.launch(block = block)
                    }
                } else {
                    val coroutineScope = beans.getBeanValue(CoroutineScope::class.java, key = "minecraft")
                    requireNotNull(coroutineScope) {
                        "Suspend timer must have a context CoroutineScope bean named 'minecraft' to run in server thread."
                    }
                    coroutineScope.launch(block = block)
                }
            } else {
                try {
                    function.callBy(arguments)
                } catch (e: Throwable) {
                    onExceptionOccurred(e, t)
                }
            }
        }

        private fun onExceptionOccurred(e: Throwable, t: BukkitTask) {
            context.contextManager.logger.error(e) {
                "Exception occurred when running timer by calling function ${function.name} " +
                        "declared in ${functionClass.simpleName}, and its bukkit task id is ${t.taskId}. " +
                        "Details: " +
                        "function: $function, " +
                        "function class: ${functionClass.name}. "
            }
        }
    }

    override fun onModulePreEnable(event: ModulePreEnableEvent) {
        event.addDependencyModuleClass<BukkitTimerModule>()
    }

    override fun onModulePostEnable(event: ModulePostEnableEvent) {
        val annotationModule = event.contextManager.annotationModule
        annotationModule.registerFunctionProcessor(Timer::class.java) {
            val function = it.function
            val functionClass = it.value::class.java

            val argumentResolvers = function.parameters.associateWith { para ->
                val argumentResolveContext = ArgumentResolveContextImpl(functionClass, function, para, it.context)

                annotationModule.createArgumentResolver(argumentResolveContext)
                    ?: DefaultArgumentResolverFactory.tryCreateArgumentResolver(argumentResolveContext)
            }

            val action = ReflectConsumerImpl(it.context, functionClass, function, argumentResolvers, it.annotation.async)
            if (it.annotation.async) {
                it.context.bukkitTimerManager.runTaskTimerAsynchronously(it.annotation.delay, it.annotation.period, action)
            } else {
                it.context.bukkitTimerManager.runTaskTimer(it.annotation.delay, it.annotation.period, action)
            }
        }
    }
}