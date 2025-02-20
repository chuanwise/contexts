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
 * 用于为一个类型添加一个联合对象的工厂。
 * 
 * 例如：
 * 
 * ```kt
 * @JointFactory(C::class)
 * object A
 *
 * object B
 * class C: JointBeanFactory {
 *     override fun createBean(beanManager: BeanManager, bean: BeanEntry<*>): Any? {
 *         return B
 *     }
 * }
 * ```
 *
 * 当我们将 `A` 类型的对象添加到 [BeanManager] 中时，因为 `A` 类型有 `@JointFactory(C::class)` 注解，
 * 系统会调用 `C` 类型的 `createBean` 方法，并将返回的 `B` 类型的对象也一同注册到 [BeanManager] 中。
 *
 * @property beanFactoryClass 联合对象的工厂类型
 * @property id 对象的 ID
 * @property primary 是否为主要对象
 * @author Chuanwise
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JointFactory(
    val beanFactoryClass: KClass<out JointBeanFactory>,
    val id: String = "",
    val primary: Boolean = false
)
