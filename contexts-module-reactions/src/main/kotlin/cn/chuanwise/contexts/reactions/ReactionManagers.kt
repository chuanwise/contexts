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

@file:JvmName("ReactionManagers")

package cn.chuanwise.contexts.reactions

import cn.chuanwise.contexts.util.BeanManager
import cn.chuanwise.contexts.util.getBean
import cn.chuanwise.contexts.util.getBeanOrFail

val BeanManager.reactionManager: ReactionManager get() = getBeanOrFail()
val BeanManager.reactionManagerOrNull: ReactionManager? get() = getBean()

inline fun <T> ReactionManager.withAutoFlush(autoFlush: Boolean, block: () -> T) : T {
    val originalAutoFlush = this.autoFlush
    this.autoFlush = autoFlush
    try {
        return block()
    } finally {
        this.autoFlush = originalAutoFlush
    }
}