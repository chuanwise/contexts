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

package cn.chuanwise.contexts.bukkit.ui

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.context.ContextPostEnterEvent
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.addBeanByRuntimeType
import org.bukkit.inventory.ItemStack
import java.util.function.Function

/**
 * 聚焦到按钮上时进入一个子上下文的按钮。
 *
 * @author Chuanwise
 */
interface FocusingHotBarItem : HotBarItem {
    val focusingContext: Context?
}

@ContextsInternalApi
class FocusingHotBarItemImpl(
    override val itemStack: ItemStack,
    private val subContextFactory: Function<Context, Context>
) : FocusingHotBarItem {
    private var mutableFocusingContext: Context? = null
    override val focusingContext: Context? get() = mutableFocusingContext

    @Listener
    fun ContextPostEnterEvent.onPostEnter(context: Context) {
        context.addBeanByRuntimeType(itemStack)
    }

    @Listener
    fun OnlineHotBarSurfaceFocusStatusChangedEvent.onFocusStatusChanged(context: Context) {
        mutableFocusingContext = if (focusStatus) {
            subContextFactory.apply(context)
        } else {
            val focusingContext = mutableFocusingContext
            checkNotNull(focusingContext) { "Focusing context is null." }

            focusingContext.exit()
            null
        }
    }
}