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

@file:JvmName("ReactiveContexts")

package cn.chuanwise.contexts.reactions.reactive

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.reactions.DEFAULT_AUTO_BIND
import cn.chuanwise.contexts.reactions.view.View
import cn.chuanwise.contexts.reactions.view.ViewContext

private interface ViewFunctionContextBean

private class ViewFunctionContextBeanAutoBindEnabled(
    private val block: ViewContext.() -> Unit
) : ViewFunctionContextBean {
    @View(autoBind = true)
    fun ViewContext.buildView() {
        block()
    }
}

private class ViewFunctionContextBeanAutoBindDisabled(
    private val block: ViewContext.() -> Unit
) : ViewFunctionContextBean {
    @View(autoBind = false)
    fun ViewContext.buildView() {
        block()
    }
}

private fun createViewFunctionContextBean(autoBind: Boolean, block: ViewContext.() -> Unit) : ViewFunctionContextBean {
    return if (autoBind) {
        ViewFunctionContextBeanAutoBindEnabled(block)
    } else {
        ViewFunctionContextBeanAutoBindDisabled(block)
    }
}

fun Context.view(childId: String? = null, autoBind: Boolean = DEFAULT_AUTO_BIND, block: ViewContext.() -> Unit) : Context {
    val bean = createViewFunctionContextBean(autoBind, block)
    return if (childId == null) enterChild(bean) else enterChild(bean, id = childId)
}

fun ViewContext.pane(childId: String? = null, autoBind: Boolean = DEFAULT_AUTO_BIND, block: ViewContext.() -> Unit) : Context {
    return context.view(childId, autoBind, block)
}