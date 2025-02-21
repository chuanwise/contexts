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

package cn.chuanwise.contexts.reactions.view

import cn.chuanwise.contexts.reactions.util.Reactive

/**
 * 标注一个上下文函数为视图函数。
 *
 * 当自动绑定 [autoBind] 启动时，将会在函数执行时自动检测使用到的 [Reactive] 对象，
 * 并自动将当前上下文与之绑定到一起。当该值刷新时，将会退出视图子上下文，并重新执行视图函数以进行 UI 刷新。
 *
 * 标注了该注解的函数可以使用 [ViewContext] 参数，并手动通过内部的设置来绑定依赖关系。
 *
 * @author Chuanwise
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class View(
    val autoBind: Boolean = true
)
