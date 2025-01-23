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

@file:JvmName("BukkitEventWaiterFactory")
package cn.chuanwise.contexts.bukkit.util

import cn.chuanwise.contexts.events.DEFAULT_LISTENER_FILTER
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_INTERCEPT
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_LISTEN
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.event.Event

@JvmOverloads
@OptIn(ContextsInternalApi::class)
fun <T : Event> createBukkitEventWaiter(
    eventClass: Class<T>,
    timeout: Long,
    filter: Boolean = DEFAULT_LISTENER_FILTER,
    intercept: Boolean = DEFAULT_LISTENER_INTERCEPT,
    listen: Boolean = DEFAULT_LISTENER_LISTEN
) : BukkitEventWaiter<T> {
    return BukkitEventWaiterImpl(eventClass, timeout, filter, intercept, listen)
}

inline fun <reified T: Event> createBukkitEventWaiter(
    timeout: Long,
    filter: Boolean = DEFAULT_LISTENER_FILTER,
    intercept: Boolean = DEFAULT_LISTENER_INTERCEPT,
    listen: Boolean = DEFAULT_LISTENER_LISTEN
) : BukkitEventWaiter<T> {
    return createBukkitEventWaiter(T::class.java, timeout, filter, intercept, listen)
}