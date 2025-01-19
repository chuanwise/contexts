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

import cn.chuanwise.contexts.Context

/**
 * 一个上下文的范围，用于创建从一个上下文出发访问上下文的迭代器。
 *
 * @author Chuanwise
 * @see ContextScope
 * @see AllChildrenDeepFirstSearchScope
 * @see AllChildrenBreadthFirstSearchScope
 * @see AllChildrenChildToParentScope
 * @see AllChildrenParentToChildScope
 * @see AllChildrenAndContextScope
 * @see ContextAndAllChildrenScope
 * @see AllParentsDeepFirstSearchScope
 * @see AllParentsBreadthFirstSearchScope
 * @see AllParentsChildToParentScope
 * @see AllParentsParentToChildScope
 * @see AllParentsAndContextScope
 * @see ContextAndAllParentsScope
 */
fun interface Scope {
    /**
     * 创建一个从指定上下文出发的迭代器。
     *
     * @param context 上下文
     * @return 迭代器
     */
    fun createIterator(context: Context): Iterator<Context>
}

/**
 * 只包含当前上下文的范围。
 *
 * @author Chuanwise
 */
object ContextScope : Scope {
    @OptIn(ContextsInternalApi::class)
    override fun createIterator(context: Context): Iterator<Context> {
        return SingletonIterator(context)
    }
}

/**
 * 包含当前上下文的所有子上下文，采用深度优先搜索的顺序访问。
 *
 * @author Chuanwise
 */
object AllChildrenDeepFirstSearchScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return context.createAllChildrenDeepFirstSearchIterator()
    }
}

/**
 * 包含当前上下文的所有子上下文，采用广度优先搜索的顺序访问。
 *
 * @author Chuanwise
 */
object AllChildrenBreadthFirstSearchScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return context.createAllChildrenBreadthFirstSearchIterator()
    }
}

/**
 * 包含当前上下文的所有子上下文，采用从子到父的拓扑排序的顺序访问。
 *
 * @author Chuanwise
 */
object AllChildrenChildToParentScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return context.allChildren.createChildToParentTopologicalSortingIterator()
    }
}

/**
 * 包含当前上下文的所有子上下文，采用从父到子的拓扑排序的顺序访问。
 *
 * @author Chuanwise
 */
object AllChildrenParentToChildScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return context.allChildren.createParentToChildTopologicalSortingIterator()
    }
}

/**
 * 包含当前上下文的所有子上下文和当前上下文，采用从子到父的拓扑排序的顺序访问。
 *
 * @author Chuanwise
 */
object AllChildrenAndContextScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return sequence {
            yieldAll(context.allChildren.createChildToParentTopologicalSortingIterator())
            yield(context)
        }.iterator()
    }
}

/**
 * 包含当前上下文和所有子上下文。
 *
 * @author Chuanwise
 */
object ContextAndAllChildrenScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return sequence {
            yield(context)
            yieldAll(context.allChildren.createChildToParentTopologicalSortingIterator())
        }.iterator()
    }
}

/**
 * 包含当前上下文的所有父上下文，采用深度优先搜索的顺序访问。
 *
 * @author Chuanwise
 */
object AllParentsDeepFirstSearchScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return context.createAllParentsDeepFirstSearchIterator()
    }
}

/**
 * 包含当前上下文的所有父上下文，采用广度优先搜索的顺序访问。
 *
 * @author Chuanwise
 */
object AllParentsBreadthFirstSearchScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return context.createAllParentsBreadthFirstSearchIterator()
    }
}

/**
 * 包含当前上下文的所有父上下文，采用从子到父的拓扑排序的顺序访问。
 *
 * @author Chuanwise
 */
object AllParentsChildToParentScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return context.allParents.createChildToParentTopologicalSortingIterator()
    }
}

/**
 * 包含当前上下文的所有父上下文，采用从父到子的拓扑排序的顺序访问。
 *
 * @author Chuanwise
 */
object AllParentsParentToChildScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return context.allParents.createParentToChildTopologicalSortingIterator()
    }
}

/**
 * 包含当前上下文的所有父上下文和当前上下文，采用从子到父的拓扑排序的顺序访问。
 *
 * @author Chuanwise
 */
object AllParentsAndContextScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return sequence {
            yieldAll(context.allParents.createChildToParentTopologicalSortingIterator())
            yield(context)
        }.iterator()
    }
}

/**
 * 包含当前上下文和所有父上下文。
 *
 * @author Chuanwise
 */
object ContextAndAllParentsScope : Scope {
    override fun createIterator(context: Context): Iterator<Context> {
        return sequence {
            yield(context)
            yieldAll(context.allParents.createChildToParentTopologicalSortingIterator())
        }.iterator()
    }
}
