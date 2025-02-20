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
import cn.chuanwise.contexts.util.MutableBeanEntry
import cn.chuanwise.contexts.util.ResolvableType

interface ContextBeanEvent<out T> : ContextEvent {
    val value: T
    val context: Context
    
    val id: String?
    val isPrimary: Boolean
    val type: ResolvableType<*>
}

interface ContextBeanAddEvent<T>: ContextBeanEvent<T>

interface ContextBeanPreAddEvent<T> : ContextBeanAddEvent<T> {
    override var id: String?
    override var isPrimary: Boolean
    override var type: ResolvableType<*>
}

interface ContextBeanPostAddEvent<T> : ContextBeanAddEvent<T> {
    override val value: T
    val bean: MutableBeanEntry<T>

    override val id: String? get() = bean.id
    override val isPrimary: Boolean get() = bean.isPrimary
    override val type: ResolvableType<*> get() = bean.type
}

@ContextsInternalApi
class ContextBeanPostAddEventImpl<T>(
    override val context: Context,
    override val value: T,
    override val bean: MutableBeanEntry<T>,
    override val contextManager: ContextManager
) : ContextBeanPostAddEvent<T>

@ContextsInternalApi
class ContextBeanPreAddEventImpl<T>(
    override val context: Context,
    override var value: T,
    override val contextManager: ContextManager,
    override var id: String?,
    override var isPrimary: Boolean,
    override var type: ResolvableType<*>
) : ContextBeanPreAddEvent<T>

interface ContextBeanRemoveEvent<out T> : ContextBeanEvent<T> {
    val bean: MutableBeanEntry<T>

    override val id: String? get() = bean.id
    override val isPrimary: Boolean get() = bean.isPrimary
    override val type: ResolvableType<*> get() = bean.type

    override val value: T get() = bean.value
}

interface ContextBeanPreRemoveEvent<T> : ContextBeanRemoveEvent<T>
interface ContextBeanPostRemoveEvent<T> : ContextBeanRemoveEvent<T>

@ContextsInternalApi
class ContextBeanPostRemoveEventImpl<T>(
    override val context: Context,
    override val bean: MutableBeanEntry<T>,
    override val contextManager: ContextManager
) : ContextBeanPostRemoveEvent<T>

@ContextsInternalApi
class ContextBeanPreRemoveEventImpl<T>(
    override val context: Context,
    override val bean: MutableBeanEntry<T>,
    override val contextManager: ContextManager
) : ContextBeanPreRemoveEvent<T>