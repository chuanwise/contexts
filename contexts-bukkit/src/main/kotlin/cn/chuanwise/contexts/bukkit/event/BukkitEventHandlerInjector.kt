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

package cn.chuanwise.contexts.bukkit.event

import cn.chuanwise.contexts.events.Listener
import cn.chuanwise.contexts.util.ContextsInternalApi
import cn.chuanwise.contexts.util.Logger
import cn.chuanwise.contexts.util.MutableEntries
import cn.chuanwise.contexts.util.MutableEntry
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.annotation.AnnotationDescription
import net.bytebuddy.implementation.MethodDelegation
import org.bukkit.Bukkit
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.plugin.IllegalPluginAccessException
import org.bukkit.plugin.Plugin
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.reflect.KClass

/**
 * Bukkit 事件处理器注入器。
 *
 * @author Chuanwise
 */
interface BukkitEventHandlerInjector {
    /**
     * 动态地往 Bukkit 事件总线上注册一个监听器。
     *
     * @param T 事件类型
     * @param eventClass 事件类型
     * @param priority 优先级
     * @param ignoreCancelled 是否忽略已取消的事件
     * @param eventHandler 监听器
     * @return 可以用于移除监听器的对象
     */
    fun <T : Event> registerEventHandler(
        eventClass: KClass<T>,
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        eventHandler: Consumer<T>
    ): MutableEntry<Consumer<T>>
}

private val EMPTY_CLASS_ARRAY = emptyArray<Class<*>>()
private val EMPTY_ANY_ARRAY = emptyArray<Any>()

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class BukkitEventHandlerInjectorImpl(
    private val plugin: Plugin,
    private val logger: Logger,
    private val bukkitListenerClassLoader: ClassLoader = plugin.javaClass.classLoader
) : BukkitEventHandlerInjector {
    // 不是 private 的目的是让 ByteBuddy 产生的类型可以访问这里的函数。
    inner class BukkitEventClassContext(val eventClass: KClass<out Event>) {
        // 管理针对一个类型的，同一优先级监听器的上下文。
        inner class EventHandlerClassContext(priority: EventPriority) {
            // 构造一个类型，继承 Any::class.java，实现 org.bukkit.event.Listener 接口。
            // 在类型内添加一个 onPlatformEventPublished 方法，只有一个参数，参数类型是 eventClass。
            // 随后为这个方法添加 @EventListener 注解，其中的 priority 是 it 的值，ignoreCancelled 是 false。
            // 最后返回这个类型。
            private val bukkitListenerClass: Class<out org.bukkit.event.Listener> = ByteBuddy()
                .subclass(Any::class.java)
                .implement(org.bukkit.event.Listener::class.java)
                .defineMethod("onPlatformEventPublished", Void.TYPE, Modifier.PUBLIC)
                .withParameter(eventClass.java)
                .intercept(MethodDelegation.to(this))
                .annotateMethod(
                    AnnotationDescription.Builder.ofType(EventHandler::class.java)
                        .define("priority", priority)
                        .define("ignoreCancelled", false)
                        .build()
                )
                .make()
                .load(bukkitListenerClassLoader)
                .loaded as Class<out org.bukkit.event.Listener>

            private val bukkitListener: org.bukkit.event.Listener =
                bukkitListenerClass.getConstructor(*EMPTY_CLASS_ARRAY)
                    .newInstance(*EMPTY_ANY_ARRAY)
                    .apply {
                        // 在 Bukkit 主线程上执行注册操作。
                        Bukkit.getScheduler().runTaskAsynchronously(plugin) { _ ->
                            Bukkit.getServer().pluginManager.registerEvents(this, plugin)
                        }
                    }

            private val listeners = MutableEntries<EventHandlerImpl<Event>>()

            // Only called in the bukkit server main thread.
            @ContextsInternalApi
            fun onPlatformEventPublished(event: Event): Boolean {
                var result = false
                for (listener in listeners) {
                    result = listener.value.safeAccept(event) || result
                }
                return result
            }

            fun <T : Event> registerEventHandler(eventHandler: EventHandlerImpl<T>): MutableEntry<EventHandlerImpl<T>> {
                return listeners.add(eventHandler as EventHandlerImpl<Event>) as MutableEntry<EventHandlerImpl<T>>
            }
        }

        private val listenerClasses: MutableMap<EventPriority, EventHandlerClassContext> = ConcurrentHashMap()

        private fun getListenerClassContext(priority: EventPriority): EventHandlerClassContext {
            return listenerClasses.computeIfAbsent(priority) { EventHandlerClassContext(priority) }
        }

        private fun getBukkitEventHandlerList(eventClass: Class<out Event>): HandlerList {
            return try {
                val declaredMethod = eventClass.getDeclaredMethod("getHandlerList")
                require(declaredMethod.trySetAccessible()) {
                    "Unable to access handler list for event " + eventClass.name + ". Static `getHandlerList` method required!"
                }

                declaredMethod.invoke(null) as HandlerList
            } catch (e: NoSuchMethodException) {
                if (eventClass.superclass != null && eventClass.superclass != Event::class.java
                    && Event::class.java.isAssignableFrom(eventClass.superclass)
                ) {
                    getBukkitEventHandlerList(eventClass.superclass.asSubclass(Event::class.java))
                } else {
                    throw IllegalPluginAccessException("Unable to find handler list for event " + eventClass.name + ". Static getHandlerList method required!")
                }
            }
        }

        fun <T : Event> registerListener(
            priority: EventPriority, eventHandler: EventHandlerImpl<T>
        ): MutableEntry<EventHandlerImpl<T>> {
            return getListenerClassContext(priority).registerEventHandler(eventHandler)
        }
    }

    // 因为只能在主线程上注册事件监听器，故无需考虑线程安全问题。
    private val bukkitListenerClassContexts = mutableMapOf<KClass<*>, BukkitEventClassContext>()

    inner class EventHandlerImpl<T : Any>(
        private val eventClass: KClass<T>,
        private val ignoreCancelled: Boolean,
        private val consumer: Consumer<T>
    ) : Consumer<T> {
        override fun accept(t: T) {
            if (!eventClass.isInstance(t)) {
                return
            }
            if (t is Cancellable) {
                // ignoreCancelled 为 false 时，只有未取消的事件才会被处理。
                if (!ignoreCancelled && t.isCancelled) {
                    return
                }
            }

            consumer.accept(t)
        }

        fun safeAccept(t: T) : Boolean {
            return try {
                accept(t)
                true
            } catch (e: Throwable) {
                logger.error(e) { "Exception occurred while handling event $t by $consumer." }
                false
            }
        }
    }

    override fun <T : Event> registerEventHandler(
        eventClass: KClass<T>,
        priority: EventPriority,
        ignoreCancelled: Boolean,
        eventHandler: Consumer<T>
    ): MutableEntry<Consumer<T>> {
        val finalListener = EventHandlerImpl(eventClass, ignoreCancelled, eventHandler)
        return bukkitListenerClassContexts
            .computeIfAbsent(eventClass) { BukkitEventClassContext(eventClass) }
            .registerListener(priority, finalListener)
    }
}