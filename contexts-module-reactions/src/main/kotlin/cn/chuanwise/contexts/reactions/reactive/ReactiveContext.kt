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

package cn.chuanwise.contexts.reactions.reactive

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.reactions.ReactionManager
import cn.chuanwise.contexts.reactions.getBuildingReactionManager
import cn.chuanwise.contexts.reactions.reactionManager
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.NotStableForInheritance

/**
 * 响应式上下文。
 *
 * @param T 响应式值的类型
 * @author Chuanwise
 */
@NotStableForInheritance
interface ReactiveContext<T> {
    /**
     * 响应式值。
     */
    val reactive: Reactive<T>

    /**
     * 如果本次操作是在某个上下文中，则该值为上下文。
     */
    val context: Context?

    /**
     * 如果本次操作是在某个上下文中，则该值为上下文的反应管理器。
     */
    val reactionManager: ReactionManager?
}

@ContextsInternalApi
abstract class AbstractReactiveContext<T> : ReactiveContext<T> {
    final override val reactionManager: ReactionManager? = getBuildingReactionManager()
    final override val context: Context? = reactionManager?.context
}