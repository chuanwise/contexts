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

import java.util.function.Supplier

@ContextsInternalApi
class ConsoleLoggerImpl(
    private val infoEnabled: Boolean = true,
    private val warningEnabled: Boolean = true,
    private val errorEnabled: Boolean = true,
    private val debugEnabled: Boolean = false,
    private val traceEnabled: Boolean = false
) : Logger {
    override fun info(message: String) {
        if (infoEnabled) {
            println("[INFO] $message")
        }
    }

    override fun info(exception: Throwable, message: String) {
        if (infoEnabled) {
            println("[INFO] $message")
            exception.printStackTrace()
        }
    }

    override fun info(supplier: Supplier<String>) {
        if (infoEnabled) {
            println("[INFO] ${supplier.get()}")
        }
    }

    override fun info(exception: Throwable, supplier: Supplier<String>) {
        if (infoEnabled) {
            println("[INFO] ${supplier.get()}")
            exception.printStackTrace()
        }
    }

    override fun warn(message: String) {
        if (warningEnabled) {
            println("[WARN] $message")
        }
    }

    override fun warn(exception: Throwable, message: String) {
        if (warningEnabled) {
            println("[WARN] $message")
            exception.printStackTrace()
        }
    }

    override fun warn(supplier: Supplier<String>) {
        if (warningEnabled) {
            println("[WARN] ${supplier.get()}")
        }
    }

    override fun warn(exception: Throwable, supplier: Supplier<String>) {
        if (warningEnabled) {
            println("[WARN] ${supplier.get()}")
            exception.printStackTrace()
        }
    }

    override fun error(message: String) {
        if (errorEnabled) {
            println("[ERROR] $message")
        }
    }

    override fun error(exception: Throwable, message: String) {
        if (errorEnabled) {
            println("[ERROR] $message")
            exception.printStackTrace()
        }
    }

    override fun error(supplier: Supplier<String>) {
        if (errorEnabled) {
            println("[ERROR] ${supplier.get()}")
        }
    }


    override fun error(exception: Throwable, supplier: Supplier<String>) {
        if (errorEnabled) {
            println("[ERROR] ${supplier.get()}")
            exception.printStackTrace()
        }
    }

    override fun debug(message: String) {
        if (debugEnabled) {
            println("[DEBUG] $message")
        }
    }

    override fun debug(exception: Throwable, message: String) {
        if (debugEnabled) {
            println("[DEBUG] $message")
            exception.printStackTrace()
        }
    }

    override fun debug(supplier: Supplier<String>) {
        if (debugEnabled) {
            println("[DEBUG] ${supplier.get()}")
        }
    }

    override fun debug(exception: Throwable, supplier: Supplier<String>) {
        if (debugEnabled) {
            println("[DEBUG] ${supplier.get()}")
            exception.printStackTrace()
        }
    }

    override fun trace(message: String) {
        if (traceEnabled) {
            println("[TRACE] $message")
        }
    }

    override fun trace(exception: Throwable, message: String) {
        if (traceEnabled) {
            println("[TRACE] $message")
            exception.printStackTrace()
        }
    }

    override fun trace(supplier: Supplier<String>) {
        if (traceEnabled) {
            println("[TRACE] ${supplier.get()}")
        }
    }

    override fun trace(exception: Throwable, supplier: Supplier<String>) {
        if (traceEnabled) {
            println("[TRACE] ${supplier.get()}")
            exception.printStackTrace()
        }
    }
}