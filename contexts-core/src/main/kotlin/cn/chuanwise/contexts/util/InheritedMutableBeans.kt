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

@ContextsInternalApi
class InheritedMutableBeans(
    private val parent: Beans
): AbstractMutableBeans() {
    private val beans = MutableBeanImpl()

    override fun registerBean(bean: MutableBean<*>) {
        beans.registerBean(bean)
    }

    override fun removeBean(bean: MutableBean<*>) {
        beans.removeBean(bean)
    }

    override fun <T : Any> getBean(beanClass: Class<T>, primary: Boolean?, key: Any?): Bean<T>? {
        return beans.getBean(beanClass, primary, key) ?: parent.getBean(beanClass, primary, key)
    }

    override fun <T : Any> getBeans(beanClass: Class<T>): List<Bean<T>> {
        return beans.getBeans(beanClass) + parent.getBeans(beanClass)
    }

    override fun <T : Any> getBeanValues(beanClass: Class<T>): List<T> {
        return beans.getBeanValues(beanClass) + parent.getBeanValues(beanClass)
    }
}