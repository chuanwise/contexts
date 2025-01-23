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

package cn.chuanwise.contexts.events.annotations

import cn.chuanwise.contexts.events.DEFAULT_LISTENER_FILTER
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_INTERCEPT
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_LISTEN
import kotlin.reflect.KClass

/**
 * 事件监听器函数注解。
 *
 * 在注册上下文对象时，将会被 [EventAnnotationModule] 自动扫描并注册。
 * 注销上下文对象并不会导致其被注销。
 *
 * @property eventClass 事件类
 * @author Chuanwise
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Listener(
    val eventClass: KClass<*> = Nothing::class,
    val filter: Boolean = DEFAULT_LISTENER_FILTER,
    val intercept: Boolean = DEFAULT_LISTENER_INTERCEPT,
    val listen: Boolean = DEFAULT_LISTENER_LISTEN
)

/**
 * 事件传播器函数注解。
 *
 * 在注册上下文对象时，将会被 [EventAnnotationModule] 自动扫描并注册。
 * 注销上下文对象并不会导致其被注销。
 *
 * 事件传播器用于将那些需要在上下文间传播的事件传播给自己的子上下文。
 * 默认事件传播器将事件简单地传播给所有子上下文，可以通过此注解来自定义传播规则。
 *
 * 这是一个随机把事件传给任意一个子上下文的事件传播器示例：
 *
 * ```kt
 * @EventSpreader
 * fun Any.spreadEventToRandomChild(
 *     currentContext: Context,
 *     eventContext: EventContext<PlayerEvent>
 * ) {
 *     context.children.randomOrNull()?.eventPublisher?.publish(eventContext)
 * }
 * ```
 *
 * 可以限定事件传播器生效的事件类型。上述例子传播所有事件。事件类型的指定方式和 [Listener] 注解中的方式相同。
 *
 * @property eventClass 事件类
 * @author Chuanwise
 * @see Listener
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventSpreader(
    val eventClass: KClass<*> = Nothing::class
)

/**
 * 事件参数注解。
 *
 * @author Chuanwise
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Event