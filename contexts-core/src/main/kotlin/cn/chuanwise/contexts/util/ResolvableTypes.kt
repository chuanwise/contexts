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

@file:JvmName("ResolvableTypes")
package cn.chuanwise.contexts.util

import java.util.Queue
import java.util.Stack
import kotlin.reflect.full.isSubclassOf

val ResolvableType<*>.isNothing: Boolean get() = rawClass == Nothing::class
val ResolvableType<*>.isUnit: Boolean get() = rawClass == Unit::class
val ResolvableType<*>.isString: Boolean get() = rawClass == String::class

val ResolvableType<*>.isCollection: Boolean get() = rawClass.isSubclassOf(Collection::class)
val ResolvableType<*>.isSet: Boolean get() = rawClass.isSubclassOf(Set::class)
val ResolvableType<*>.isList: Boolean get() = rawClass.isSubclassOf(List::class)
val ResolvableType<*>.isQueue: Boolean get() = rawClass.isSubclassOf(Queue::class)
val ResolvableType<*>.isStack: Boolean get() = rawClass.isSubclassOf(Stack::class)
val ResolvableType<*>.isMap: Boolean get() = rawClass.isSubclassOf(Map::class)

val ResolvableType<*>.isArray: Boolean get() = rawClass == Array::class