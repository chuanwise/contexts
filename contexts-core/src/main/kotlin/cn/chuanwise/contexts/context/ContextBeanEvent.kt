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

package cn.chuanwise.contexts.context

import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.MutableBean

interface ContextBeanEvent<out T : Any> : ContextEvent {
    val context: Context
}

interface ContextBeanAddEvent<T : Any>: ContextBeanEvent<T> {
    val value: T
}

interface ContextBeanPreAddEvent<T : Any> : ContextBeanAddEvent<T>
interface ContextBeanPostAddEvent<T : Any> : ContextBeanAddEvent<T> {
    override val value: T
    val bean: MutableBean<T>
}

@ContextsInternalApi
class ContextBeanAddEventImpl<T : Any>(
    override val context: Context,
    override val value: T,
    override val bean: MutableBean<T>,
    override val contextManager: ContextManager
) : ContextBeanPostAddEvent<T>

@ContextsInternalApi
class ContextBeanPreAddEventImpl<T : Any>(
    override val context: Context,
    override var value: T,
    override val contextManager: ContextManager
) : ContextBeanPreAddEvent<T>

interface ContextBeanRemoveEvent<out T : Any> : ContextBeanEvent<T> {
    val value: T
    val bean: MutableBean<T>
}

interface ContextBeanPreRemoveEvent<T : Any> : ContextBeanRemoveEvent<T>
interface ContextBeanPostRemoveEvent<T : Any> : ContextBeanRemoveEvent<T>

@ContextsInternalApi
class ContextBeanRemoveEventImpl<T : Any>(
    override val context: Context,
    override val value: T,
    override val bean: MutableBean<T>,
    override val contextManager: ContextManager
) : ContextBeanPostRemoveEvent<T>

@ContextsInternalApi
class ContextBeanPreRemoveEventImpl<T : Any>(
    override val context: Context,
    override val value: T,
    override val bean: MutableBean<T>,
    override val contextManager: ContextManager
) : ContextBeanPreRemoveEvent<T>