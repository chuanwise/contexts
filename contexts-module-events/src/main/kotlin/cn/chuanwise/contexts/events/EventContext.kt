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

import cn.chuanwise.contexts.Context

/**
 * 事件上下文。
 *
 * @param T 事件类型
 * @author Chuanwise
 */
interface EventContext<T : Any> {
    /**
     * 上下文。
     */
    val context: Context

    /**
     * 事件
     */
    val event: T

    /**
     * 是否被拦截
     */
    var isIntercepted: Boolean
}