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
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

interface BukkitTimerAnnotationModule : Module {
}

@ContextsInternalApi
class BukkitTimerAnnotationModuleImpl : BukkitTimerAnnotationModule, Module {
    override fun onModulePreEnable(event: ModulePreEnableEvent) {
        event.addDependencyModuleClass<BukkitTimerModule>()
    }

    private class ReflectConsumerImpl(
        private val context: Context,
        private val functionClass: Class<*>,
        private val function: KFunction<*>,
        private val argumentResolvers: Map<KParameter, ArgumentResolver>
    ) : Consumer<BukkitTask> {
        override fun accept(t: BukkitTask) {
            TODO()
        }
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

            val action = ReflectConsumerImpl(it.context, functionClass, function, argumentResolvers)
            it.context.bukkitTimerManager.runTaskTimer(it.annotation.delay, it.annotation.period, action)
        }
    }
}