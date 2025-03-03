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

package cn.chuanwise.contexts.reactions.reactive

/**
 * 响应式值直接读取的上下文。
 *
 * 一旦调用 [Reactive.value]，此方法即被调用。方法的返回值将是实际读出的值。
 * 可在此处将原始类型替换为代理类型，以便检测后续对此对象的使用。
 *
 * @author Chuanwise
 */
fun interface ReactiveReadObserver<out T> {
    fun onRead(context: ReactiveReadContext<@UnsafeVariance T>)
}