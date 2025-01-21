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

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.events.Listener
import cn.chuanwise.contexts.util.Beans
import cn.chuanwise.contexts.util.MutableEntry
import cn.chuanwise.contexts.util.getBeanValue
import cn.chuanwise.contexts.util.getBeanValueOrFail
import org.bukkit.event.Event
import org.bukkit.event.EventPriority

/**
 * Bukkit 事件管理器。
 *
 * @author Chuanwise
 */
interface BukkitEventManager {
    /**
     * 当前上下文。
     */
    val context: Context

    /**
     * 注册监听器。
     *
     * @param T 事件类型
     * @param eventClass 事件类
     * @param priority 优先级
     * @param ignoreCancelled 是否忽略取消
     * @param filter 是否过滤
     * @param intercept 是否拦截
     * @param listener 监听器
     * @return 监听器
     */
    fun <T : Event> registerListener(
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        filter: Boolean = true,
        intercept: Boolean = false,
        listen: Boolean = false,
        listener: Listener<T>
    ) : MutableEntry<Listener<T>>
}
