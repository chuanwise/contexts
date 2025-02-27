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
 * 函数调用观察者。
 *
 * 对于 [Reactive] 值的函数调用，若其类型非 `final` 类型，则在调用之后将会执行此函数通知所有监听者。
 *
 * @param T 代理类型和原始类型
 * @author Chuanwise
 */
interface ReactiveCallObserver<T> {
    fun onFunctionCall(context: ReactiveCallContext<T>)
}