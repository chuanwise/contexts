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

package cn.chuanwise.contexts.annotations

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.util.ContextsInternalApi
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

/**
 * 对象解析上下文。
 *
 * @author Chuanwise
 */
interface ArgumentResolveContext {
    val functionClass: Class<*>
    val function: KFunction<*>
    val parameter: KParameter
    val context: Context
}

@ContextsInternalApi
class ArgumentResolveContextImpl(
    override val functionClass: Class<*>,
    override val function: KFunction<*>,
    override val parameter: KParameter,
    override val context: Context
) : ArgumentResolveContext
