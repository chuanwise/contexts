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

package cn.chuanwise.contexts.bukkit.event

import cn.chuanwise.contexts.events.EventContext
import cn.chuanwise.contexts.events.EventProcessor
import cn.chuanwise.contexts.events.eventPublisher
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.event.Event
import org.bukkit.event.EventPriority

object BukkitEventProcessor : EventProcessor<Event> {
    private val priorities = listOf(
        EventPriority.LOWEST, EventPriority.LOW,
        EventPriority.NORMAL,
        EventPriority.HIGH, EventPriority.HIGHEST,
        EventPriority.MONITOR
    )

    @OptIn(ContextsInternalApi::class)
    override fun process(eventContext: EventContext<Event>) {
        for (priority in priorities) {
            eventContext.beans.addBean(priority).use {
                eventContext.context.eventPublisher.publish(eventContext)
            }
        }
    }
}