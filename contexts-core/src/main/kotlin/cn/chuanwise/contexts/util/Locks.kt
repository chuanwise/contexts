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

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock

/**
 * 获取锁时出现异常。
 *
 * 如果 [exceptionOnLock] 非空，表明函数在获取第 [exceptionLockIndex] 个锁时出现异常。
 * 在释放已经获取的锁时，还可能继续出现新的异常，也可能全部成功释放。此时 [exceptionsOnUnlock]
 * 和 [exceptionUnlockIndexes] 分别记录了释放锁时出现的异常和异常对应的锁索引。
 *
 * 如果 [exceptionOnLock] 为空，表明函数在获取锁时没有出现异常，但是在释放锁时出现异常。
 * 此时 [exceptionsOnUnlock] 和 [exceptionUnlockIndexes] 必定非空。
 *
 * @property message 异常信息。
 * @property cause 异常原因。
 * @property exceptionOnLock 获取锁时出现的异常。
 * @property exceptionLockIndex 获取锁时出现异常的锁索引。
 * @property exceptionsOnUnlock 释放锁时出现的异常。
 */
class LockAcquireException(
    override val message: String?,
    override val cause: Throwable?,
    val exceptionOnLock: Throwable?,
    val exceptionLockIndex: Int?,
    val exceptionsOnUnlock: List<Throwable>,
    val exceptionUnlockIndexes: List<Int>
) : RuntimeException(message, cause)

/**
 * 尝试获取所有锁。若获取失败，则释放已经获取的锁。
 *
 * @param locks 锁列表。在函数调用期间不可修改。
 * @return 是否成功获取所有锁。
 * @throws LockAcquireException 获取锁时出现异常。
 */
@ContextsInternalApi
fun tryAcquireLocks(locks: List<Lock>): Boolean {
    var index = 0
    val size = locks.size

    var exceptionOnLock: Throwable? = null
    while (index < size) {
        val locked = try {
            locks[index].tryLock()
        } catch (e: Throwable) {
            exceptionOnLock = e
            break
        }
        if (locked) {
            index++
        } else {
            break
        }
    }
    if (index == size) {
        return true
    }

    val notYetAcquiredLockIndex = index

    val exceptionUnlockIndexes = mutableListOf<Int>()
    val exceptionsOnUnlock = mutableListOf<Throwable>()
    while (index > 0) {
        try {
            locks[--index].unlock()
        } catch (e: Throwable) {
            exceptionsOnUnlock.add(e)
            exceptionUnlockIndexes.add(index)
        }
    }

    if (exceptionOnLock != null) {
        throw LockAcquireException(
            message = "Exception occurred when acquiring lock $notYetAcquiredLockIndex.",
            cause = exceptionOnLock,
            exceptionOnLock = exceptionOnLock,
            exceptionLockIndex = notYetAcquiredLockIndex,
            exceptionsOnUnlock = exceptionsOnUnlock,
            exceptionUnlockIndexes = emptyList()
        )
    }
    if (exceptionsOnUnlock.isNotEmpty()) {
        throw LockAcquireException(
            message = "Exception occurred when releasing locks $exceptionUnlockIndexes after failed to acquire lock $notYetAcquiredLockIndex.",
            cause = exceptionsOnUnlock.first(),
            exceptionOnLock = null,
            exceptionLockIndex = null,
            exceptionsOnUnlock = exceptionsOnUnlock,
            exceptionUnlockIndexes = exceptionUnlockIndexes
        )
    }

    return false
}

/**
 * 按照一个顺序获取锁。若中途失败，则释放已经获取的锁，并且尝试重新获取。
 *
 * @param locks 锁列表。
 * @throws LockAcquireException 获取锁时出现异常。
 */
@ContextsInternalApi
fun acquireLocks(locks: List<Lock>) {
    do {
        if (tryAcquireLocks(locks)) {
            break
        }
        Thread.sleep(0)
    } while (true)
}

/**
 * 按照一个顺序获取锁。若中途失败，则释放已经获取的锁，并且尝试重新获取。
 *
 * @param locks 锁列表。
 * @param maxSpinCount 最大自旋次数。
 * @throws LockAcquireException 获取锁时出现异常。
 */
@ContextsInternalApi
fun acquireLocks(locks: List<Lock>, maxSpinCount: Int) {
    require(maxSpinCount > 0) { "Max spin count must be greater than 0." }
    for (i in 0 until maxSpinCount) {
        if (tryAcquireLocks(locks)) {
            return
        }
        Thread.sleep(0)
    }
}

/**
 * 释放锁时出现异常。
 *
 * @property message 异常信息。
 * @property cause 异常原因。
 * @property exceptionsOnUnlock 释放锁时出现的异常。
 * @property exceptionUnlockIndexes 释放锁时出现异常的锁索引。
 */
class LockReleaseException(
    override val message: String?,
    override val cause: Throwable,
    val exceptionsOnUnlock: List<Throwable>,
    val exceptionUnlockIndexes: List<Int>
) : RuntimeException(message, cause)

/**
 * 释放锁。
 *
 * @param locks 锁列表。
 * @throws LockReleaseException 释放锁时出现异常。
 */
@ContextsInternalApi
fun releaseLocks(locks: List<Lock>) {
    val exceptionsOnUnlock = mutableListOf<Throwable>()
    val exceptionUnlockIndexes = mutableListOf<Int>()

    for (index in locks.indices) {
        try {
            locks[index].unlock()
        } catch (e: Throwable) {
            exceptionsOnUnlock.add(e)
            exceptionUnlockIndexes.add(index)
        }
    }

    if (exceptionsOnUnlock.isNotEmpty()) {
        throw LockReleaseException(
            message = "Exception occurred when releasing locks $exceptionUnlockIndexes.",
            cause = exceptionsOnUnlock.first(),
            exceptionsOnUnlock = exceptionsOnUnlock,
            exceptionUnlockIndexes = exceptionUnlockIndexes
        )
    }
}

/**
 * 获取所有锁。
 */
@ContextsInternalApi
fun List<Lock>.acquireAll() = acquireLocks(this)

/**
 * 释放所有锁。
 */
@ContextsInternalApi
fun List<Lock>.releaseAll() = releaseLocks(this)

/**
 * 在获取所有锁后执行一个动作，然后释放所有锁。
 *
 * @param T 动作的返回值类型。
 * @param action 动作。
 * @return 动作的返回值。
 */
@ContextsInternalApi
inline fun <T> List<Lock>.withLocks(action: () -> T): T {
    acquireAll()
    return try {
        action()
    } finally {
        releaseAll()
    }
}

/**
 * 在获取所有读锁后执行一个动作，然后释放所有锁。
 *
 * @param T 动作的返回值类型。
 * @param action 动作。
 * @return 动作的返回值。
 */
@ContextsInternalApi
inline fun <T> List<ReadWriteLock>.withReadLocks(action: () -> T): T {
    val locks = map { it.readLock() }
    locks.acquireAll()
    return try {
        action()
    } finally {
        locks.releaseAll()
    }
}

/**
 * 在获取所有写锁后执行一个动作，然后释放所有锁。
 *
 * @param T 动作的返回值类型。
 * @param action 动作。
 * @return 动作的返回值。
 */
@ContextsInternalApi
inline fun <T> List<ReadWriteLock>.withWriteLocks(action: () -> T): T {
    val locks = map { it.writeLock() }
    locks.acquireAll()
    return try {
        action()
    } finally {
        locks.releaseAll()
    }
}