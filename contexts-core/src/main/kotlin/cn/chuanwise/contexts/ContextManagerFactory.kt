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

@file:JvmName("ContextManagerFactory")

package cn.chuanwise.contexts

import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.Logger
import cn.chuanwise.contexts.util.createJavaLogger

@OptIn(ContextsInternalApi::class)
fun createContextManager(logger: Logger): ContextManager {
    return ContextManagerImpl(logger)
}

fun createContextManager(): ContextManager {
    val logger = java.util.logging.Logger.getLogger("ContextManager")
    return createContextManager(createJavaLogger(logger))
}