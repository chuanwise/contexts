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
 * 用于标注一个对象类型为联合类型。
 *
 * 当将一个具有联合类型的对象添加到对象容器时，其将自动添加所有联合类型的对象。
 *
 * @property beanClass 联合类型
 * @author Chuanwise
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Joint(
    val beanClass: KClass<*>,
    val id: String = "",
    val primary: Boolean = false
)
