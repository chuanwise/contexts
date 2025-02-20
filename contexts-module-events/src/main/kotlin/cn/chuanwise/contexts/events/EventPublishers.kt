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

@file:JvmName("EventPublishers")

package cn.chuanwise.contexts.events

import cn.chuanwise.contexts.util.BeanManager
import cn.chuanwise.contexts.util.getBeanValueOrFail

/**
 * 获取事件管理器。
 */
val BeanManager.eventPublisherOrNull: EventPublisher<Any>? get() = getBean()

/**
 * 获取事件管理器。
 */
val BeanManager.eventPublisher: EventPublisher<Any> get() = getBeanValueOrFail()