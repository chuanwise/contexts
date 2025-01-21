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
import cn.chuanwise.contexts.ContextPreEnterEvent
import cn.chuanwise.contexts.ContextPreExitEvent
import cn.chuanwise.contexts.events.annotations.Listener
import cn.chuanwise.contexts.events.eventPublisher
import cn.chuanwise.contexts.util.ContextsInternalApi
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.ItemStack
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一个玩家正在使用的快捷栏菜单。
 *
 * @author Chuanwise
 * @see createOnlineHotBarMenu
 */
interface OnlineHotBarMenu : AutoCloseable {
    val isClosed: Boolean
    val isOpened: Boolean

    /**
     * 获取当前的玩家。
     * 若菜单非打开状态，将抛出异常。
     */
    val player: Player

    /**
     * 获取当前的菜单。
     */
    val menu: HotBarMenu

    /**
     * 获取菜单上下文。
     */
    val menuContext: Context

    /**
     * 获取每个按钮的上下文。
     */
    val buttonContexts: Map<HotBarMenuButton, Context>

    /**
     * 获取当前聚焦的按钮（如果有）。
     */
    val focusedButton: HotBarMenuButton?

    /**
     * 获取当前聚焦的按钮的上下文（如果有）。
     */
    val focusedButtonContext: Context?

    /**
     * 获取快捷栏槽位数量。
     */
    val slotCount: Int

    /**
     * 获取左侧填充槽位数量。
     */
    val maxLeftPaddingSlotCount: Int

    /**
     * 获取右侧填充槽位数量。
     */
    val maxRightPaddingSlotCount: Int

    /**
     * 玩家打开菜单前的快捷栏备份。
     * 若菜单还没有打开过，则抛出异常。
     */
    val playerHotBarItemStacks: List<ItemStack?>

    /**
     * 用于填充视角的物品。
     */
    val paddingItemStack: ItemStack
}

@ContextsInternalApi
class OnlineHotBarMenuImpl(
    override val menu: HotBarMenu,
    override val slotCount: Int = DEFAULT_SLOT_COUNT,
    override val maxLeftPaddingSlotCount: Int = DEFAULT_MAX_LEFT_PADDING_SLOT_COUNT,
    override val maxRightPaddingSlotCount: Int = DEFAULT_MAX_RIGHT_PADDING_SLOT_COUNT,
    override val paddingItemStack: ItemStack = DEFAULT_PADDING_ITEM_STACK
) : OnlineHotBarMenu {
    companion object {
        const val DEFAULT_SLOT_COUNT = 9

        const val DEFAULT_MAX_LEFT_PADDING_SLOT_COUNT = DEFAULT_SLOT_COUNT - 1
        const val DEFAULT_MAX_RIGHT_PADDING_SLOT_COUNT = DEFAULT_SLOT_COUNT - 1

        @JvmStatic
        val DEFAULT_PADDING_ITEM_STACK = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
    }

    private var mutableMenuContext: Context? = null
    override val menuContext: Context get() = mutableMenuContext ?: error("Online hot bar menu context is not entered.")

    private var mutablePlayer: Player? = null
    override val player: Player get() = mutablePlayer ?: error("The online hot bar menu is not opened.")

    private val mutableButtonContexts = mutableMapOf<HotBarMenuButton, Context>()
    override val buttonContexts: Map<HotBarMenuButton, Context> get() = mutableButtonContexts

    private var mutableFocusedButton: HotBarMenuButton? = null
    override val focusedButton: HotBarMenuButton? get() = mutableFocusedButton
    override val focusedButtonContext: Context? get() = focusedButton?.let { getButtonContext(it) }

    private var mutablePlayerHotBarItemStacks: List<ItemStack?>? = null
    override val playerHotBarItemStacks: List<ItemStack?> get() = mutablePlayerHotBarItemStacks ?: error("The online hot bar menu is never opened.")

    // slot index [0, hotBarSlotCount) + slotToButtonIndexOffset => button index
    private var slotToButtonIndexOffset: Int = 0

    private val mutableIsOpened = AtomicBoolean(false)
    override val isOpened: Boolean get() = mutableIsOpened.get()
    override val isClosed: Boolean get() = !mutableIsOpened.get()

    private abstract inner class AbstractHotBarMenuEvent : HotBarMenuEvent {
        override val menu: HotBarMenu get() = this@OnlineHotBarMenuImpl.menu
    }

    private inner class HotBarMenuButtonFocusStatusChangedEvent(
        override val focusStatus: Boolean
    ) : AbstractHotBarMenuEvent(), HotBarMenuFocusStatusChangedEvent

    private val hotBarMenuButtonFocusStatusChangedToFalseEvent = HotBarMenuButtonFocusStatusChangedEvent(false)
    private val hotBarMenuButtonFocusStatusChangedToTrueEvent = HotBarMenuButtonFocusStatusChangedEvent(true)

    private fun flushPlayerHotBarSlots() {
        val player = player
        val buttons = menu.buttons
        for (i in 0 until slotCount) {
            val itemStack = buttons.getOrNull(i + slotToButtonIndexOffset)?.itemStack ?: paddingItemStack
            player.inventory.setItem(i, itemStack)
        }
    }

    private fun getPlayerFocusedButton(): HotBarMenuButton? {
        return menu.buttons.getOrNull(player.inventory.heldItemSlot + slotToButtonIndexOffset)
    }

    private fun flushPlayerFocusedButton(): HotBarMenuButton? {
        return getPlayerFocusedButton().apply { mutableFocusedButton = this }
    }

    private fun getButtonContext(button: HotBarMenuButton): Context {
        return mutableButtonContexts.computeIfAbsent(button) {
            menuContext.enterChild(button, key = "button")
        }
    }

    // 计算当前已经在右侧填充了多少个槽位。
    private fun getCurrentlyRightPaddingSlotCount(): Int {
        return slotCount - (menu.buttons.size - slotToButtonIndexOffset)
    }

    // 计算当前已经在左侧填充了多少个槽位。
    private fun getCurrentlyLeftPaddingSlotCount(): Int {
        return slotToButtonIndexOffset
    }

    @Listener
    fun ContextPreEnterEvent.onPreEnter(player: Player, context: Context) {
        check(mutableIsOpened.compareAndSet(false, true)) { "The online hot bar menu is already opened." }
        mutableMenuContext = context
        mutablePlayer = player

        // 备份玩家快捷栏。
        mutablePlayerHotBarItemStacks = player.inventory.contents
            .copyOfRange(0, slotCount)
            .map { it?.clone() }
            .toList()

        // 雷普玩家的快捷栏。
        flushPlayerHotBarSlots()
    }

    @Listener
     fun ContextPreExitEvent.onPreExit() {
        check(mutableIsOpened.compareAndSet(true, false)) { "The online hot bar menu is already closed." }

        // 向当前的按钮发送退出事件。
        getPlayerFocusedButton()?.let {
            val buttonContext = getButtonContext(it)
            buttonContext.eventPublisher.publishToContext(hotBarMenuButtonFocusStatusChangedToFalseEvent)
        }

        // 恢复玩家快捷栏。
        val player = player
        playerHotBarItemStacks.forEachIndexed { index, itemStack ->
            player.inventory.setItem(index, itemStack)
        }

        mutableButtonContexts.clear()
    }

    @Listener
    fun ContextPostEnterEvent.onPostEnter() {
        // 如果玩家此时聚焦在一个按钮上，则向那个按钮发送聚焦事件。
        flushPlayerFocusedButton()?.let {
            val buttonContext = getButtonContext(it)
            buttonContext.eventPublisher.publishToContext(hotBarMenuButtonFocusStatusChangedToTrueEvent)
        }
    }

    @Listener(intercept = true)
    fun PlayerItemHeldEvent.onPlayerItemHeldEvent() {
        // 如果物品栏槽位没有发生变化，则忽略此事件。
        if (newSlot == previousSlot) {
            return
        }

        // 检查这种变化属于 +1、-1 还是突变。
        val plusOne = ((previousSlot + 1) % slotCount) == player.inventory.heldItemSlot
        val minusOne = ((previousSlot - 1 + slotCount) % slotCount) == player.inventory.heldItemSlot

        // 忽略突变。
        when {
            plusOne -> {
                // 如果是 +1 且上一个槽位已经到了边界，则检查是否需要把视图向右移动。
                if (previousSlot == slotCount - 1) {
                    // 如果是非负数，表示 padding 数量。负数表示右侧还没有显示出来的按钮数量。
                    val currentlyRightPaddingSlotCount = getCurrentlyRightPaddingSlotCount()

                    if (currentlyRightPaddingSlotCount < maxRightPaddingSlotCount) {
                        // 如果还可以向右边滚动，若是潜行，则直接滚动都末端。否则只移动一个位置。
                        if (player.isSneaking) {
                            slotToButtonIndexOffset = maxRightPaddingSlotCount - currentlyRightPaddingSlotCount
                        } else {
                            slotToButtonIndexOffset++
                        }
                    } else {
                        // 如果已经不能向右边滚动，若启动了潜行，则可以回到开头。
                        if (player.isSneaking) {
                            slotToButtonIndexOffset = maxLeftPaddingSlotCount
                        }
                    }
                    isCancelled = true
                }
            }
            minusOne -> {
                // 如果是 -1 且上一个槽位已经到了边界，则检查是否需要把视图向左移动。
                if (previousSlot == 0) {
                    // 如果是非负数，表示 padding 数量。负数表示左侧还没有显示出来的按钮数量。
                    val currentlyLeftPaddingSlotCount = getCurrentlyLeftPaddingSlotCount()

                    if (currentlyLeftPaddingSlotCount < maxLeftPaddingSlotCount) {
                        // 如果还可以向左边滚动，若是潜行，则直接滚动都末端。否则只移动一个位置。
                        if (player.isSneaking) {
                            slotToButtonIndexOffset = -(maxLeftPaddingSlotCount - currentlyLeftPaddingSlotCount)
                        } else {
                            slotToButtonIndexOffset--
                        }
                    } else {
                        // 如果已经不能向左边滚动，若启动了潜行，则可以回到末。
                        if (player.isSneaking) {
                            slotToButtonIndexOffset = -maxRightPaddingSlotCount
                        }
                    }

                    isCancelled = true
                }
            }
        }

        // 槽位变化但是按钮没有变化可能是因为菜单发生改变。但只要聚焦没变也忽略。
        val currentlyFocusedButton = getPlayerFocusedButton()
        val previouslyFocusedButton = mutableFocusedButton
        if (currentlyFocusedButton === previouslyFocusedButton) {
            return
        }

        // 向原来的按钮发送聚焦改变为 False 事件。
        if (previouslyFocusedButton != null) {
            getButtonContext(previouslyFocusedButton).eventPublisher.publishToContext(hotBarMenuButtonFocusStatusChangedToFalseEvent)
        }

        mutableFocusedButton = currentlyFocusedButton

        // 向新的按钮（如果有）发送聚焦改变为 True 事件。
        if (currentlyFocusedButton != null) {
            getButtonContext(currentlyFocusedButton).eventPublisher.publishToContext(hotBarMenuButtonFocusStatusChangedToTrueEvent)
        }
    }

    override fun close() {
        menuContext.exit()
    }
}