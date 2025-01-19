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

@OptIn(ContextsInternalApi::class)
fun createJavaLogger(logger: java.util.logging.Logger): Logger = JavaLoggerImpl(logger)

@ContextsInternalApi
class JavaLoggerImpl(
    private val logger: java.util.logging.Logger
) : Logger {
    override fun info(message: String) {
        logger.info(message)
    }

    override fun info(exception: Throwable, message: String) {
        logger.info(message)
        exception.printStackTrace()
    }

    override fun info(supplier: Supplier<String>) {
        logger.info(supplier)
    }

    override fun info(exception: Throwable, supplier: Supplier<String>) {
        logger.info(supplier)
        exception.printStackTrace()
    }

    override fun warn(message: String) {
        logger.warning(message)
    }

    override fun warn(exception: Throwable, message: String) {
        logger.warning(message)
        exception.printStackTrace()
    }

    override fun warn(supplier: Supplier<String>) {
        logger.warning(supplier)
    }

    override fun warn(exception: Throwable, supplier: Supplier<String>) {
        logger.warning(supplier)
        exception.printStackTrace()
    }

    override fun error(message: String) {
        logger.severe(message)
    }

    override fun error(exception: Throwable, message: String) {
        logger.severe(message)
        exception.printStackTrace()
    }

    override fun error(supplier: Supplier<String>) {
        logger.severe(supplier)
    }

    override fun error(exception: Throwable, supplier: Supplier<String>) {
        logger.severe(supplier)
        exception.printStackTrace()
    }

    override fun debug(message: String) {
        logger.fine(message)
    }

    override fun debug(exception: Throwable, message: String) {
        logger.fine(message)
        exception.printStackTrace()
    }

    override fun debug(supplier: Supplier<String>) {
        logger.fine(supplier)
    }

    override fun debug(exception: Throwable, supplier: Supplier<String>) {
        logger.fine(supplier)
        exception.printStackTrace()
    }

    override fun trace(message: String) {
        logger.finest(message)
    }

    override fun trace(exception: Throwable, message: String) {
        logger.finest(message)
        exception.printStackTrace()
    }

    override fun trace(supplier: Supplier<String>) {
        logger.finest(supplier)
    }

    override fun trace(exception: Throwable, supplier: Supplier<String>) {
        logger.finest(supplier)
        exception.printStackTrace()
    }
}