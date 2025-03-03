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

/**
 * 视图函数。
 *
 * 视图函数是用于构造此上下文相关的 UI 的函数。函数中可以使用响应式值，框架会自动检测其值并绑定依赖关系。
 * 除此之外，也可以手动使用 [ViewContext.bind] 绑定依赖关系。
 *
 * 视图函数应当是幂等的，只有在数据发生变化或手动调用刷新函数时才会执行。
 *
 * @author Chuanwise
 */
fun interface ViewFunction {
    /**
     * 构建视图。
     *
     * @param context 视图上下文
     */
    fun buildView(context: ViewContext)
}