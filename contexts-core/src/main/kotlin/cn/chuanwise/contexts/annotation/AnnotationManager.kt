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

package cn.chuanwise.contexts.annotation

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.util.BeanManager
import cn.chuanwise.contexts.util.getBean
import cn.chuanwise.contexts.util.getBeanOrFail

/**
 * 反射管理器。
 *
 * @author Chuanwise
 */
interface AnnotationManager {
    /**
     * 上下文。
     */
    val context: Context

    /**
     * 扫描一个对象。
     *
     * @param value 对象
     */
    fun scan(value: Any)
}

val BeanManager.annotationManager: AnnotationManager get() = getBeanOrFail()
val BeanManager.annotationManagerOrNull: AnnotationManager? get() = getBean()
