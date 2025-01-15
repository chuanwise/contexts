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

import java.util.Deque
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 读加减锁。
 *
 * 该锁将读写锁 [ReadWriteLock] 的写操作进一步分为两类：加和减。
 * 加只能最多一个线程执行，但减可以多个线程同时执行。
 *
 * @author Chuanwise
 */
interface ReadAddRemoveLock {
    val readLock: Lock
    val addLock: Lock
    val removeLock: Lock
}

@OptIn(ContextsInternalApi::class)
fun ReadAddRemoveLock(): ReadAddRemoveLock = ReentrantReadAddRemoveLock()

inline fun <T> ReadAddRemoveLock.read(action: () -> T): T = readLock.withLock(action)
inline fun <T> ReadAddRemoveLock.add(action: () -> T): T = addLock.withLock(action)
inline fun <T> ReadAddRemoveLock.remove(action: () -> T): T = removeLock.withLock(action)

@OptIn(ContextsInternalApi::class)
inline fun <T> List<ReadAddRemoveLock>.read(action: () -> T): T = map { it.readLock }.withLocks(action)

@OptIn(ContextsInternalApi::class)
inline fun <T> List<ReadAddRemoveLock>.add(action: () -> T): T = map { it.addLock }.withLocks(action)

@OptIn(ContextsInternalApi::class)
inline fun <T> List<ReadAddRemoveLock>.remove(action: () -> T): T = map { it.removeLock }.withLocks(action)

@ContextsInternalApi
class ReentrantReadAddRemoveLock : ReadAddRemoveLock {
    private enum class State {
        FREE,
        READ,
        WRITE_ADD,
        WRITE_ADD_REMOVE,
        WRITE_REMOVE
    }

    private var mode: State = State.FREE
    private val modeLock = ReentrantLock()

    private var readCount: Int = 0
    private var removeCount: Int = 0

    private enum class Action {
        READ,
        WRITE_ADD,
        WRITE_REMOVE
    }
    private val action = ThreadLocal.withInitial<Deque<Action>> { ArrayDeque() }

    private fun removeLastAction(): Action {
        val action = action.get()
        val last = action.removeLast()
        if (action.isEmpty()) {
            this.action.remove()
        }
        return last
    }

    private abstract class AbstractLock : Lock {
        override fun lock() {
            while (true) {
                if (tryLock()) {
                    return
                }
            }
        }

        override fun lockInterruptibly() {
            while (!Thread.currentThread().isInterrupted) {
                if (tryLock()) {
                    return
                }
            }
        }

        override fun newCondition(): Condition {
            throw UnsupportedOperationException()
        }

        override fun tryLock(time: Long, unit: TimeUnit): Boolean {
            val deadline = System.currentTimeMillis() + unit.toMillis(time)
            while (System.currentTimeMillis() < deadline) {
                if (tryLock()) {
                    return true
                }
            }
            return false
        }
    }

    private inner class ReadLock : AbstractLock() {
        private fun onLocked(): Boolean {
            readCount++
            action.get().addLast(Action.READ)
            return true
        }
        
        private fun onUnlocked() {
            check(removeLastAction() == Action.READ) { "Read lock not locked." }
            if ((--readCount) == 0) {
                mode = State.FREE
            }
        }

        override fun tryLock(): Boolean = modeLock.withLock {
            mode = when (mode) {
                State.FREE,
                State.READ -> State.READ
                State.WRITE_ADD,
                State.WRITE_ADD_REMOVE,
                State.WRITE_REMOVE -> return@withLock false
            }
            onLocked()
        }

        override fun unlock(): Unit = modeLock.withLock { 
            check(mode == State.READ) { "Read lock not locked." }
            onUnlocked()
        }
    }

    private inner class AddLock : AbstractLock() {
        private fun onLocked(): Boolean {
            action.get().addLast(Action.WRITE_ADD)
            return true
        }

        private fun onUnlocked() {
            check(removeLastAction() == Action.WRITE_ADD) { "Add lock not locked." }
            mode = when (mode) {
                State.WRITE_ADD -> State.FREE
                State.WRITE_ADD_REMOVE -> State.WRITE_REMOVE
                State.FREE,
                State.READ,
                State.WRITE_REMOVE -> error("Unexpected mode: $mode.")
            }
        }

        override fun tryLock(): Boolean = modeLock.withLock {
            mode = when (mode) {
                State.FREE -> State.WRITE_ADD
                State.WRITE_REMOVE -> State.WRITE_ADD_REMOVE
                State.READ,
                State.WRITE_ADD,
                State.WRITE_ADD_REMOVE -> return@withLock false
            }
            onLocked()
        }

        override fun unlock() = modeLock.withLock {
            check(mode == State.WRITE_ADD) { "Add lock not locked." }
            onUnlocked()
        }
    }

    private inner class RemoveLock: AbstractLock() {
        private fun onLocked(): Boolean {
            removeCount++
            action.get().addLast(Action.WRITE_REMOVE)
            return true
        }

        private fun onUnlocked() {
            check(removeLastAction() == Action.WRITE_REMOVE) { "Remove lock not locked." }
            mode = when (mode) {
                State.WRITE_REMOVE -> {
                    if ((--removeCount) == 0) {
                        State.FREE
                    } else {
                        mode
                    }
                }
                State.WRITE_ADD_REMOVE -> {
                    if ((--removeCount) == 0) {
                        State.WRITE_ADD
                    } else {
                        mode
                    }
                }
                State.FREE,
                State.READ,
                State.WRITE_ADD -> error("Unexpected mode: $mode.")
            }
        }

        override fun tryLock(): Boolean = modeLock.withLock {
            mode = when (mode) {
                State.FREE -> State.WRITE_REMOVE
                State.WRITE_ADD -> State.WRITE_ADD_REMOVE
                State.READ,
                State.WRITE_ADD_REMOVE,
                State.WRITE_REMOVE -> return@withLock false
            }
            onLocked()
        }

        override fun unlock() = modeLock.withLock {
            check(mode == State.WRITE_REMOVE) { "Remove lock not locked." }
            onUnlocked()
        }
    }

    override val readLock: Lock = ReadLock()
    override val addLock: Lock = AddLock()
    override val removeLock: Lock = RemoveLock()
}