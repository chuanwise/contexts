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

package cn.chuanwise.contexts.events

import cn.chuanwise.contexts.context.ContextPostEdgeAddEvent
import cn.chuanwise.contexts.context.ContextPostEnterEvent
import cn.chuanwise.contexts.context.ContextPostExitEvent
import cn.chuanwise.contexts.context.ContextPostEdgeRemoveEvent
import cn.chuanwise.contexts.context.ContextPreEdgeAddEvent
import cn.chuanwise.contexts.context.ContextPreEnterEvent
import cn.chuanwise.contexts.context.ContextPreExitEvent
import cn.chuanwise.contexts.context.ContextPreEdgeRemoveEvent
import cn.chuanwise.contexts.module.Module
import cn.chuanwise.contexts.module.ModulePreEnableEvent
import cn.chuanwise.contexts.module.addDependencyModuleClass
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.NotStableForInheritance

/**
 * 用于让上下文内的监听器也能收到上下文事件，而非仅仅是上下文管理器可以收到上下文事件。
 *
 * @author Chuanwise
 * @see createContextEventModule
 */
@NotStableForInheritance
interface ContextEventModule : Module

@ContextsInternalApi
class ContextEventModuleImpl : ContextEventModule {
    override fun onModulePreEnable(event: ModulePreEnableEvent) {
        event.addDependencyModuleClass<EventModule>()
    }

    override fun onContextEdgePreAdd(event: ContextPreEdgeAddEvent) {
        event.parent.eventPublisherOrNull?.publishToContext(event)
        event.child.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextEdgePostAdd(event: ContextPostEdgeAddEvent) {
        event.parent.eventPublisherOrNull?.publishToContext(event)
        event.child.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextEdgePreRemove(event: ContextPreEdgeRemoveEvent) {
        event.parent.eventPublisherOrNull?.publishToContext(event)
        event.child.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextEdgePostRemove(event: ContextPostEdgeRemoveEvent) {
        event.parent.eventPublisherOrNull?.publishToContext(event)
        event.child.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextPreEnter(event: ContextPreEnterEvent) {
        event.context.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextPostEnter(event: ContextPostEnterEvent) {
        event.context.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextPreExit(event: ContextPreExitEvent) {
        event.context.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextPostExit(event: ContextPostExitEvent) {
        event.context.eventPublisherOrNull?.publishToContext(event)
    }
}