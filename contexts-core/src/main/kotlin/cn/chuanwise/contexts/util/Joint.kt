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

package cn.chuanwise.contexts.util

import kotlin.reflect.KClass

/**
 * 声明一个联合类型，用于在注册到上下文管理器时，将多个对象绑定在一起。
 *
 * @property beanClass 联合对象的值类型
 * @property keys 联合对象的键
 * @property primary 是否为主键
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Joint(
    val beanClass: KClass<*>,
    val keys: Array<String> = [],
    val primary: Boolean = false
)
