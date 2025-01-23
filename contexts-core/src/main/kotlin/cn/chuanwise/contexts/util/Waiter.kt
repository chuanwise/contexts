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

import cn.chuanwise.contexts.context.Context
import kotlinx.coroutines.Deferred
import java.util.concurrent.CompletableFuture

object WaiterTimeoutException: RuntimeException("Waiter timeout.")
object WaiterCancelledException: RuntimeException("Waiter cancelled.")

/**
 * 等待器。
 *
 * @param T 事件类型。
 * @author Chuanwise
 */
interface Waiter<T> {
    /**
     * 等待上下文。
     */
    val context: Context?

    /**
     * 超时时间，单位毫秒。
     */
    val timeout: Long

    /**
     * 是否还没有开始等待。
     */
    val isAllocated: Boolean

    /**
     * 是否正在等待。
     */
    val isWaiting: Boolean

    /**
     * 是否已经超时。
     */
    val isTimeout: Boolean

    /**
     * 是否已经取消。
     */
    val isCancelled: Boolean

    /**
     * 是否已经完成。
     */
    val isCompleted: Boolean

    /**
     * 是否已经超时、已经完成或者已经取消。
     */
    val isDone: Boolean

    /**
     * 使用 [Deferred] 等待事件。
     *
     * @return 事件。
     */
    fun asDeferred(): Deferred<T>

    /**
     * 使用 Future 的方式等待事件。
     *
     * @return 事件。
     */
    @JavaFriendlyApi
    fun asFuture(): CompletableFuture<T>

    /**
     * 尝试取消等待。
     */
    fun cancel()
}