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

package cn.chuanwise.contexts.bukkit.util

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.context.ContextPostEnterEvent
import cn.chuanwise.contexts.bukkit.task.BukkitTaskManager
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_FILTER
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_INTERCEPT
import cn.chuanwise.contexts.events.DEFAULT_LISTENER_LISTEN
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.events.annotations.ListenerManager
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.JavaFriendlyApi
import cn.chuanwise.contexts.util.Waiter
import cn.chuanwise.contexts.util.WaiterCancelledException
import cn.chuanwise.contexts.util.WaiterTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asCompletableFuture
import org.bukkit.event.Event
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * 用于等待下一个 Bukkit 事件的等待器。
 *
 * 该等待器会创建一个子上下文，并注册一个监听器等待事件。
 * 在事件发生或超时后自动退出上下文。可以通过 [asDeferred] 或 [asFuture] 等待和获取结果。
 *
 * 用法：
 *
 * ```kt
 * val event = createBukkitEventWaiter<AsyncPlayerChatEvent>(timeout = 20)
 *     .attachTo(context)
 *     .asDeferred()
 *     .await()
 * ```
 *
 * @param T Bukkit 事件类型。
 * @author Chuanwise
 * @see createBukkitEventWaiter
 */
interface BukkitEventWaiter<T : Event> : Waiter<T>

@ContextsInternalApi
@OptIn(JavaFriendlyApi::class)
class BukkitEventWaiterImpl<T : Event>(
    private val eventClass: KClass<T>,
    override val timeout: Long,
    private val filter: Boolean = DEFAULT_LISTENER_FILTER,
    private val intercept: Boolean = DEFAULT_LISTENER_INTERCEPT,
    private val listen: Boolean = DEFAULT_LISTENER_LISTEN
) : BukkitEventWaiter<T> {
    private enum class State {
        ALLOCATED, WAITING, TIMEOUT, CANCELLED, COMPLETED
    }
    private val state = AtomicReference(State.ALLOCATED)

    override val isAllocated: Boolean get() = state.get() == State.ALLOCATED
    override val isWaiting: Boolean get() = state.get() == State.WAITING
    override val isTimeout: Boolean get() = state.get() == State.TIMEOUT
    override val isCompleted: Boolean get() = state.get() == State.COMPLETED
    override val isCancelled: Boolean get() = state.get() == State.CANCELLED
    override val isDone: Boolean get() = isTimeout || isCompleted || isCancelled

    private val deferred = CompletableDeferred<T>()

    override fun asDeferred(): Deferred<T> = deferred
    override fun asFuture(): CompletableFuture<T> = deferred.asCompletableFuture()

    private var mutableContext: Context? = null
    override val context: Context? get() = mutableContext

    @Listener
    fun ContextPostEnterEvent.onPostEnter(
        listenerManager: ListenerManager,
        taskManager: BukkitTaskManager,
        context: Context
    ) {
        if (!state.compareAndSet(State.ALLOCATED, State.WAITING)) {
            return
        }
        mutableContext = context

        listenerManager.registerListener(eventClass, filter, intercept, listen) {
            if (state.compareAndSet(State.ALLOCATED, State.WAITING)) {
                deferred.complete(it.event)
            }
            context.exit()
        }
        taskManager.runTaskLater(delay = timeout.millisecondToTicks()) {
            if (state.compareAndSet(State.WAITING, State.TIMEOUT)) {
                deferred.completeExceptionally(WaiterTimeoutException)
            }
            context.exit()
        }
    }

    override fun cancel() {
        if (state.compareAndSet(State.ALLOCATED, State.CANCELLED)) {
            deferred.completeExceptionally(WaiterCancelledException)
        }
        if (state.compareAndSet(State.WAITING, State.CANCELLED)) {
            deferred.completeExceptionally(WaiterCancelledException)
            mutableContext?.exit()
        }
    }
}

