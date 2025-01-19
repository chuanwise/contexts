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
import cn.chuanwise.contexts.Module
import cn.chuanwise.contexts.events.ContextPostAddEvent
import cn.chuanwise.contexts.events.ContextPreEnterEvent
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.InheritedMutableBeans
import cn.chuanwise.contexts.util.MutableBean
import cn.chuanwise.contexts.util.MutableBeans
import cn.chuanwise.contexts.util.MutableEntries
import cn.chuanwise.contexts.util.MutableEntry

/**
 * 过滤器模块。
 *
 * @author Chuanwise
 */
interface FilterModule : Module

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class FilterModuleImpl : FilterModule {
    private object SingleParentFilterResolverImpl : FilterResolver {
        override fun <T : Any> resolveFilter(context: Context, filterContext: FilterContext<T>) {
            context.parent.filterManager.filter(filterContext)
        }
    }

    private class FilterManagerImpl(
        override val context: Context,
    ) : FilterManager {
        private var resolverBean: MutableBean<FilterResolver>? = null
        override var resolver: FilterResolver?
            get() = resolverBean?.value
            set(value) {
                if (value == null) {
                    resolverBean?.remove()
                    resolverBean = null
                } else {
                    val resolverBeanLocal = resolverBean
                    if (resolverBeanLocal == null) {
                        context.registerBean(value).let { resolverBean = it }
                    } else {
                        resolverBeanLocal.value = value
                    }
                }
            }

        private abstract inner class AbstractFilter<T : Any>(
            val filter: Filter<T>,
            val cache: Boolean
        ) : Filter<T> by filter {
            fun safeFilter(filterContext: FilterContext<T>) : Boolean? {
                return try {
                    filter(filterContext)
                } catch (e: Throwable) {
                    context.contextManager.logger.error(e) {
                        "Error occurred while filtering value ${filterContext.value} by filter ${filter::class.simpleName}." +
                                "Details: " +
                                "filter class: ${filter::class.qualifiedName}, " +
                                "value class: ${this::class.qualifiedName}, " +
                                "filter cache: $cache. "
                    }
                    null
                }
            }
        }

        private inner class ValueCheckedFilterImpl<T : Any>(
            val valueClass: Class<T>,
            filter: Filter<T>,
            cache: Boolean
        ) : AbstractFilter<T>(filter, cache) {
            override fun filter(filterContext: FilterContext<T>): Boolean? {
                if (!valueClass.isInstance(filterContext.value)) {
                    return null
                }
                return super.filter(filterContext)
            }
        }

        private inner class FilterImpl<T : Any>(
            filter: Filter<T>,
            cache: Boolean
        ) : AbstractFilter<T>(filter, cache)

        private val filters = MutableEntries<AbstractFilter<Any>>()

        class FilterContextImpl<T : Any>(
            override val value: T,
            override val context: Context,
            override val beans: MutableBeans,
            override val caches: MutableMap<Filter<T>, Boolean?> = mutableMapOf(),
            override var result: Boolean? = null
        ) : FilterContext<T>

        override fun <T : Any> filter(value: T): FilterContext<T> {
            val beans = InheritedMutableBeans(context).apply {
                registerBean(value, primary = true)
            }
            val context = FilterContextImpl(value, context, beans).apply { beans.registerBean(this) }
            return filter(context)
        }

        override fun <T : Any> filter(filterContext: FilterContext<T>): FilterContext<T> {
            require(filterContext is FilterContextImpl<*>) { "FilterContext must be created by this module." }
            val caches = filterContext.caches as MutableMap<Filter<T>, Boolean?>

            // 先在本层尝试一次检查。
            for (entry in filters) {
                val filter = entry.value as Filter<T>
                var result = caches[filter]
                if (result == null) {
                    result = entry.value.safeFilter(filterContext)
                    if (entry.value.cache) {
                        caches[filter] = result
                    }
                }

                if (result != null) {
                    filterContext.result = result
                    return filterContext
                }
            }

            var resolver = resolver
            val parentCount = context.parentCount

            resolver = resolver ?: when (parentCount) {
                1 -> SingleParentFilterResolverImpl
                0 -> NullFilterResolver
                else -> error("Cannot resolve filters from multiple parents without a resolver. " +
                        "Use context.filterManager.resolver = ... to set one and try again. ")
            }

            resolver.resolveFilter(context, filterContext)
            return filterContext
        }

        override fun <T : Any> registerFilter(valueClass: Class<T>, cache: Boolean, filter: Filter<T>): MutableEntry<Filter<T>> {
            return filters.add(ValueCheckedFilterImpl(valueClass, filter, cache) as ValueCheckedFilterImpl<Any>) as MutableEntry<Filter<T>>
        }

        override fun registerFilter(cache: Boolean, filter: Filter<Any>): MutableEntry<Filter<Any>> {
            return filters.add(FilterImpl(filter, cache))
        }
    }

    override fun onContextPreEnter(event: ContextPreEnterEvent) {
        val filterManager = FilterManagerImpl(event.context)
        event.context.registerBean(filterManager)
    }

    override fun onContextPostAdd(event: ContextPostAddEvent) {
        val filterManager = event.child.filterManagerOrNull ?: return
        if (event.child.parentCount > 1) {
            check(filterManager.resolver != null) {
                "Cannot resolve filters from multiple parents without a resolver."
            }
        }
    }
}