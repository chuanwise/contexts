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

package cn.chuanwise.contexts.reactions.annotations

import cn.chuanwise.contexts.annotation.AnnotationFunctionProcessor
import cn.chuanwise.contexts.annotation.AnnotationModule
import cn.chuanwise.contexts.annotation.ArgumentResolveContextImpl
import cn.chuanwise.contexts.annotation.ArgumentResolver
import cn.chuanwise.contexts.annotation.DefaultArgumentResolverFactory
import cn.chuanwise.contexts.annotation.annotationModule
import cn.chuanwise.contexts.context.CoroutineScopeConfiguration
import cn.chuanwise.contexts.context.callFunctionAsync
import cn.chuanwise.contexts.context.toConfiguration
import cn.chuanwise.contexts.events.EventModule
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModulePostEnableEvent
import cn.chuanwise.contexts.module.ModulePreEnableEvent
import cn.chuanwise.contexts.module.addDependencyModuleClass
import cn.chuanwise.contexts.reactions.ReactionModule
import cn.chuanwise.contexts.reactions.reactionManager
import cn.chuanwise.contexts.reactions.view.View
import cn.chuanwise.contexts.reactions.view.ViewContext
import cn.chuanwise.contexts.reactions.view.ViewFunction
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.CoroutineScope
import cn.chuanwise.contexts.util.MutableEntry
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

interface ReactionAnnotationModule : Module {
}

@ContextsInternalApi
class ReactionAnnotationModuleImpl : ReactionAnnotationModule {
    override fun onModulePreEnable(event: ModulePreEnableEvent) {
        event.addDependencyModuleClass<ReactionModule>()
        event.addDependencyModuleClass<AnnotationModule>()
        event.addDependencyModuleClass<EventModule>()
    }

    private class ReflectViewFunction(
        val function: KFunction<*>,
        val functionClass: KClass<*>,
        val argumentResolvers: Map<KParameter, ArgumentResolver>,
        val coroutineScopeConfiguration: CoroutineScopeConfiguration?
    ) : ViewFunction {
        override fun buildView(context: ViewContext) {
            val arguments = argumentResolvers.mapValues { it.value.resolveArgument(context.beanManager) }
            callFunctionAsync(
                context.context, function, functionClass, arguments, coroutineScopeConfiguration,
                runBlocking = true,
                onException = {
                    context.context.contextManager.logger.error(it) {
                        "Exception occurred while building view " +
                                "by method ${function.name} declared in ${function::class.simpleName} for context $context. " +
                                "Details: " +
                                "method class: ${function::class.qualifiedName}, " +
                                "method: $function."
                    }
                }
            )
        }
    }

    private lateinit var viewAnnotationProcessor: MutableEntry<AnnotationFunctionProcessor<View>>

    override fun onModulePostEnable(event: ModulePostEnableEvent) {
        val annotationModule = event.contextManager.annotationModule

        viewAnnotationProcessor = annotationModule.registerAnnotationFunctionProcessor(View::class) {
            val autoBind = it.annotation.autoBind

            val function = it.function
            val functionClass = it.value::class

            val argumentResolvers = function.parameters.associateWith { para ->
                val argumentResolveContext = ArgumentResolveContextImpl(functionClass, function, para, it.context)

                annotationModule.createArgumentResolver(argumentResolveContext)
                    ?: DefaultArgumentResolverFactory.tryCreateArgumentResolver(argumentResolveContext)
            }
            val finalFunction = ReflectViewFunction(
                function, functionClass, argumentResolvers, it.function.findAnnotation<CoroutineScope>()?.toConfiguration()
            )
            it.context.reactionManager.registerViewFunction(autoBind, finalFunction)
        }
    }
}