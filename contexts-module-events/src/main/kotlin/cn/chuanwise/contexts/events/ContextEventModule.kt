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

import cn.chuanwise.contexts.ContextPostAddEvent
import cn.chuanwise.contexts.ContextPostEnterEvent
import cn.chuanwise.contexts.ContextPostExitEvent
import cn.chuanwise.contexts.ContextPostRemoveEvent
import cn.chuanwise.contexts.ContextPreAddEvent
import cn.chuanwise.contexts.ContextPreEnterEvent
import cn.chuanwise.contexts.ContextPreExitEvent
import cn.chuanwise.contexts.ContextPreRemoveEvent
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

    override fun onContextPreAdd(event: ContextPreAddEvent) {
        event.parent.eventPublisherOrNull?.publishToContext(event)
        event.child.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextPostAdd(event: ContextPostAddEvent) {
        event.parent.eventPublisherOrNull?.publishToContext(event)
        event.child.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextPreRemove(event: ContextPreRemoveEvent) {
        event.parent.eventPublisherOrNull?.publishToContext(event)
        event.child.eventPublisherOrNull?.publishToContext(event)
    }

    override fun onContextPostRemove(event: ContextPostRemoveEvent) {
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