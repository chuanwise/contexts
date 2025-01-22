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

package cn.chuanwise.contexts.filters

import cn.chuanwise.contexts.Context

/**
 * 过滤器解析器，将多个父上下文的过滤器结果解析为一个结果。
 *
 * @author Chuanwise
 */
interface FilterResolver {
    /**
     * 解析过滤结果。
     *
     * @param context 当前上下文。
     * @param filterContext 过滤上下文。
     */
    fun <T : Any> resolveFilter(context: Context, filterContext: FilterContext<T>)
}

/**
 * 只有当所有父上下文的过滤器都通过时，才会通过的过滤器解析器。
 *
 * @author Chuanwise
 */
object AllFilterResolver : FilterResolver {
    override fun <T : Any> resolveFilter(context: Context, filterContext: FilterContext<T>) {
        for (parent in context.parents) {
            val filterManager = parent.filterManagerOrNull ?: continue
            filterManager.filter(filterContext.value).let { if (it.isNotTrue) return }
        }
    }
}

/**
 * 只要有一个父上下文的过滤器通过时，就会通过的过滤器解析器。
 *
 * @author Chuanwise
 */
object AnyFilterResolver : FilterResolver {
    override fun <T : Any> resolveFilter(context: Context, filterContext: FilterContext<T>) {
        for (parent in context.parents) {
            val filterManager = parent.filterManagerOrNull ?: continue
            filterManager.filter(filterContext.value).let { if (it.isNotFalse) return }
        }
    }
}

/**
 * 无论如何都通过的过滤器解析器。
 *
 * @author Chuanwise
 */
object NullFilterResolver : FilterResolver {
    override fun <T : Any> resolveFilter(context: Context, filterContext: FilterContext<T>) {
        filterContext.result = null
    }
}