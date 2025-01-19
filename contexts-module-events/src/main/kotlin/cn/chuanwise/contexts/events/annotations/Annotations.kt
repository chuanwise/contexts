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

import cn.chuanwise.contexts.util.Scope
import kotlin.reflect.KClass

/**
 * 事件监听器函数注解。
 *
 * @property eventClass 事件类
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Listener(
    val eventClass: KClass<*> = Nothing::class,
    val filter: Boolean = true,
    val intercept: Boolean = false
)

/**
 * 标注一个可能被过滤的类型，以指定其默认过滤范围。
 *
 * @property scopeClass 过滤器的作用域
 */
@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class Scope(
    val scopeClass: KClass<Scope>
)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Event