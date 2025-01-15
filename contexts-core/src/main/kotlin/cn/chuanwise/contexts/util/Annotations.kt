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

/**
 * 标注一个 API 是 `contexts-core` 的内部 API。
 *
 * 除非开发者正在开发核心功能，否则不应该在其他模块中使用。这些 API 可能在未经任何事先警告的前提下发生变化。
 *
 * @author Chuanwise
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal to contexts-core and should not be used in other modules."
)
@Retention(AnnotationRetention.BINARY)
annotation class ContextsInternalApi

/**
 * 标注一个 API 在使用上是稳定的，但在继承上不稳定。
 *
 * 可能在未经任何事先警告的前提下新增抽象方法。
 *
 * @author Chuanwise
 */
@Retention(AnnotationRetention.BINARY)
annotation class NotStableForInheritance

/**
 * 标注一个 API 是为 Java 开发者设计的，不应该在 Kotlin 中使用。可能存在性能低等问题。
 *
 * @author Chuanwise
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is designed for Java developers and should not be used in Kotlin."
)
@Retention(AnnotationRetention.BINARY)
annotation class JavaFriendlyApi(
    val replacement: String = "No replacement provided."
)
