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

import cn.chuanwise.contexts.Context
import cn.chuanwise.contexts.ContextPostEnterEvent
import cn.chuanwise.contexts.ContextPreExitEvent
import cn.chuanwise.contexts.events.EventContext
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.events.annotations.Spreader
import cn.chuanwise.contexts.events.annotations.listenerManager
import cn.chuanwise.contexts.events.eventPublisher
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.ItemStack
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一个玩家正在使用的快捷栏界面。
 *
 * @author Chuanwise
 * @see createOnlineHotBarMenu
 */
interface OnlineHotBarSurface : AutoCloseable {
    val isClosed: Boolean
    val isOpened: Boolean

    /**
     * 获取按钮。
     */
    val items: List<HotBarItem>

    /**
     * 获取当前的玩家。
     * 若菜单非打开状态，将抛出异常。
     */
    val player: Player

    /**
     * 获取菜单上下文。
     */
    val context: Context

    /**
     * 获取每个按钮的上下文。
     */
    val itemContexts: Map<HotBarItem, Context>

    /**
     * 获取当前聚焦的按钮。
     */
    val focusedItem: HotBarItem

    /**
     * 获取当前聚焦的按钮的上下文。
     */
    val focusedItemContext: Context

    /**
     * 玩家打开菜单前的快捷栏备份。
     * 若菜单还没有打开过，则抛出异常。
     */
    val playerHotBarItemStacks: List<ItemStack?>
}

@ContextsInternalApi
class OnlineHotBarSurfaceImpl(
    override val items: List<HotBarItem>
) : OnlineHotBarSurface {
    companion object {
        const val SLOT_COUNT = 9
    }

    init {
        require(items.size == SLOT_COUNT) {
            "The size of items must be $SLOT_COUNT."
        }
    }

    private var mutableMenuContext: Context? = null
    override val context: Context get() = mutableMenuContext ?: error("Online hot bar menu context is not entered.")

    private var mutablePlayer: Player? = null
    override val player: Player get() = mutablePlayer ?: error("The online hot bar menu is not opened.")

    private val mutableButtonContexts = mutableMapOf<HotBarItem, Context>()
    override val itemContexts: Map<HotBarItem, Context> get() = mutableButtonContexts

    private var mutableFocusedItem: HotBarItem? = null
    override val focusedItem: HotBarItem get() = mutableFocusedItem ?: error("The online hot bar menu is not opened.")
    override val focusedItemContext: Context get() = getButtonContext(focusedItem)

    private var mutablePlayerHotBarItemStacks: List<ItemStack?>? = null
    override val playerHotBarItemStacks: List<ItemStack?>
        get() = mutablePlayerHotBarItemStacks ?: error("The online hot bar menu is never opened.")

    private val mutableIsOpened = AtomicBoolean(false)
    override val isOpened: Boolean get() = mutableIsOpened.get()
    override val isClosed: Boolean get() = !mutableIsOpened.get()

    private abstract inner class AbstractOnlineHotBarSurfaceEvent : OnlineHotBarSurfaceEvent {
        override val surface: OnlineHotBarSurface get() = this@OnlineHotBarSurfaceImpl
    }

    private inner class OnlineOnlineHotBarSurfaceButtonFocusStatusChangedEvent(
        override val focusStatus: Boolean
    ) : AbstractOnlineHotBarSurfaceEvent(), OnlineHotBarSurfaceFocusStatusChangedEvent

    private val hotBarMenuButtonFocusStatusChangedToFalseEvent = OnlineOnlineHotBarSurfaceButtonFocusStatusChangedEvent(false)
    private val hotBarMenuButtonFocusStatusChangedToTrueEvent = OnlineOnlineHotBarSurfaceButtonFocusStatusChangedEvent(true)

    private fun getButtonContext(button: HotBarItem): Context {
        return mutableButtonContexts.computeIfAbsent(button) {
            context.enterChild(button)
        }
    }

    @Listener
    fun ContextPostEnterEvent.onPostEnter(player: Player, context: Context) {
        check(mutableIsOpened.compareAndSet(false, true)) { "The online hot bar menu is already opened." }
        mutableMenuContext = context
        mutablePlayer = player

        // 备份玩家快捷栏。
        mutablePlayerHotBarItemStacks = player.inventory.contents
            .copyOfRange(0, SLOT_COUNT)
            .map { it?.clone() }
            .toList()

        // 设置玩家快捷栏。
        items.forEachIndexed { index, item ->
            player.inventory.setItem(index, item.itemStack)
        }

        // 如果玩家此时聚焦在一个按钮上，则向那个按钮发送聚焦事件。
        val focusedItem = items[player.inventory.heldItemSlot]
        mutableFocusedItem = focusedItem

        val buttonContext = getButtonContext(focusedItem)
        buttonContext.eventPublisher.publishToContext(hotBarMenuButtonFocusStatusChangedToTrueEvent)
    }

    @Listener
    fun ContextPreExitEvent.onPreExit() {
        check(mutableIsOpened.compareAndSet(true, false)) { "The online hot bar menu is already closed." }

        // 向当前的按钮发送退出事件。
        mutableFocusedItem?.let {
            val buttonContext = getButtonContext(it)
            buttonContext.eventPublisher.publishToContext(hotBarMenuButtonFocusStatusChangedToFalseEvent)
        }
        mutableFocusedItem = null

        // 恢复玩家快捷栏。
        val player = player
        playerHotBarItemStacks.forEachIndexed { index, itemStack ->
            player.inventory.setItem(index, itemStack)
        }

        mutableButtonContexts.clear()
    }

    @Listener(intercept = true)
    fun PlayerItemHeldEvent.onPlayerItemHeldEvent() {
        // 如果物品栏槽位没有发生变化，则忽略此事件。
        if (newSlot == previousSlot) {
            return
        }

        // 槽位变化但是按钮没有变化可能是因为菜单发生改变。但只要聚焦没变也忽略。
        val currentlyFocusedItem = items[newSlot]
        val previouslyFocusedItem = mutableFocusedItem
        if (currentlyFocusedItem === previouslyFocusedItem) {
            return
        }

        // 向原来的按钮发送聚焦改变为 False 事件。
        if (previouslyFocusedItem != null) {
            getButtonContext(previouslyFocusedItem).eventPublisher.publishToContext(
                hotBarMenuButtonFocusStatusChangedToFalseEvent
            )
        }

        mutableFocusedItem = currentlyFocusedItem

        // 向新的按钮发送聚焦改变为 True 事件。
        getButtonContext(currentlyFocusedItem).eventPublisher.publishToContext(hotBarMenuButtonFocusStatusChangedToTrueEvent)
    }

    @Spreader
    fun PlayerEvent.spread(eventContext: EventContext<PlayerEvent>) {
        // 只把事件发给当前正在聚焦的按钮上。
        focusedItemContext.eventPublisher.publish(eventContext)
    }

    override fun close() {
        context.exit()
    }
}