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

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.isSuperclassOf

/**
 * 泛型参数的范围。
 *
 * @author Chuanwise
 */
interface ResolvableTypeArgumentBound {
    /**
     * 范围对应的可解析类型。
     */
    val type: ResolvableType<*>

    /**
     * 判断范围是否解析成功。
     */
    val isResolved: Boolean
}

/**
 * 泛型实际参数。
 *
 * @author Chuanwise
 */
interface ResolvableTypeArgument {
    /**
     * 对应的定义时的 参数名。
     */
    val name: String

    /**
     * 参数的变化范围。如果 [isAll] 则为 `null`。
     */
    val variance: KVariance?

    /**
     * 可解析类型。如果 [isAll] 则为 `null`。
     */
    val type: ResolvableType<*>?

    /**
     * 在 [KType.arguments] 内的对应参数。
     */
    val argument: KTypeProjection

    /**
     * 参数的上限，即那些用 T : String, U : T 之类的语法定义的上限。
     */
    val bounds: List<ResolvableTypeArgumentBound>

    /**
     * 是否表示所有类型，即 Kotlin 的 * 或者 Java 的 ?。
     */
    val isAll: Boolean

    /**
     * 参数是否解析完成，若否表示缺少信息。
     */
    val isResolved: Boolean
}

/**
 * 可解析类型，用于解析 Kotlin 类型，尤其是泛型参数。
 *
 * @param T 被解析的类型。
 * @author Chuanwise
 */
interface ResolvableType<T> {
    /**
     * 判断该类型是否是一个普通的类，即没有泛型参数的类。
     */
    val isPlainClass: Boolean

    /**
     * 判断该类型是否是泛型类型。
     */
    val isParameterizedClass: Boolean

    /**
     * 获取该类型的父类或实现的接口。
     */
    val parentTypes: List<ResolvableType<*>>

    /**
     * 判断类型是否可空。
     */
    val isNullable: Boolean

    /**
     * 获取 Kotlin 类型。
     */
    val rawType: KType

    /**
     * 获取 Kotlin 类。
     */
    val rawClass: KClass<T & Any>

    /**
     * 该类型的泛型参数名称（按照定义顺序）。
     */
    val typeParameterNames: List<String>

    /**
     * 类型泛型参数的值，可以通过 [ResolvableTypeArgument.type] 获取具体类型。
     */
    val typeArguments: Map<String, ResolvableTypeArgument>

    /**
     * 判断该类型是否已经解析完成。
     */
    val isResolved: Boolean

    /**
     * 判断类型能否从另一个类型赋值。
     *
     * @param type 另一个类型。
     * @return 能否赋值。
     */
    fun isAssignableFrom(type: ResolvableType<*>): Boolean

    /**
     * 按照泛型参数名称获取泛型参数的值。
     *
     * @param rawClass 泛型原始类型。
     * @param name 对应的参数名。
     * @return 具体参数值。
     */
    fun getTypeParameter(rawClass: KClass<*>, name: String): ResolvableTypeArgument?
    fun getTypeParameterOrFail(rawClass: KClass<*>, name: String): ResolvableTypeArgument

    /**
     * 按照泛型参数顺序获取泛型参数的值。
     *
     * @param rawClass 泛型原始类型。
     * @param index 对应的参数索引。
     * @return 具体参数值。
     */
    fun getTypeParameter(rawClass: KClass<*>, index: Int): ResolvableTypeArgument?
    fun getTypeParameterOrFail(rawClass: KClass<*>, index: Int): ResolvableTypeArgument
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
class ResolvableTypeImpl(
    override val rawType: KType,
    instances: MutableMap<KType, ResolvableType<*>>
) : ResolvableType<Any> {
    override val rawClass: KClass<Any>
    override val isNullable: Boolean

    override val isPlainClass: Boolean get() = typeParameterNames.isEmpty()
    override val isParameterizedClass: Boolean get() = typeParameterNames.isNotEmpty()

    override val typeParameterNames: List<String>
    override val typeArguments: Map<String, ResolvableTypeArgument>

    override val parentTypes: List<ResolvableType<*>>

    private fun MutableMap<KType, ResolvableType<*>>.createResolvableType(rawType: KType): ResolvableTypeImpl {
        return (this[rawType] ?: getResolvableTypeCache(rawType) ?: ResolvableTypeImpl(rawType, this)) as ResolvableTypeImpl
    }

    private abstract class AbstractResolvableTypeArgumentBound : ResolvableTypeArgumentBound {
        abstract fun tryResolve(
            name: String, type: KType,
            instances: MutableMap<KType, ResolvableType<*>>,
            arguments: MutableMap<String, ResolvableTypeArgument>
        ): Boolean
    }

    private class CompletedResolvableTypeArgumentBound(
        override val type: ResolvableTypeImpl
    ) :  AbstractResolvableTypeArgumentBound() {
        override val isResolved: Boolean get() = type.isResolved

        override fun tryResolve(
            name: String, type: KType,
            instances: MutableMap<KType, ResolvableType<*>>,
            arguments: MutableMap<String, ResolvableTypeArgument>
        ): Boolean {
            return this.type.tryResolve(name, type, instances, arguments)
        }
    }

    private class LazyInitArgumentResolvableTypeArgumentBound(
        private val argumentName: String
    ) : AbstractResolvableTypeArgumentBound() {
        private var argument: AbstractResolvableTypeArgument? = null
        override val isResolved: Boolean get() = argument?.isResolved ?: error("Bound not yet initialized.")
        override val type: ResolvableType<*> get() = argument?.type ?: error("Bound not yet initialized.")

        override fun tryResolve(
            name: String, type: KType,
            instances: MutableMap<KType, ResolvableType<*>>,
            arguments: MutableMap<String, ResolvableTypeArgument>
        ): Boolean {
            this.argument = arguments[argumentName] as? AbstractResolvableTypeArgument
            return argument?.tryResolve(name, type, instances, arguments) == true
        }
    }

    private abstract class AbstractResolvableTypeArgument : ResolvableTypeArgument {
        abstract fun tryResolve(
            name: String, type: KType,
            instances: MutableMap<KType, ResolvableType<*>>,
            arguments: MutableMap<String, ResolvableTypeArgument>
        ): Boolean
    }

    private class AllResolvableTypeArgument(
        override val name: String,
        override val argument: KTypeProjection
    ) : AbstractResolvableTypeArgument() {
        override val variance: KVariance? get() = null
        override val type: ResolvableType<*>? get() = null
        override val bounds: List<ResolvableTypeArgumentBound> get() = emptyList()
        override val isAll: Boolean get() = true
        override val isResolved: Boolean get() = true

        override fun tryResolve(
            name: String, type: KType,
            instances: MutableMap<KType, ResolvableType<*>>,
            arguments: MutableMap<String, ResolvableTypeArgument>
        ): Boolean {
            return false
        }
    }

    private inner class ResolvableTypeArgumentImpl(
        override val name: String,
        override val variance: KVariance,
        override val argument: KTypeProjection,
        override val bounds: List<AbstractResolvableTypeArgumentBound>,
        var kotlin: KType,
        type: ResolvableType<*>? = null,
    ) : AbstractResolvableTypeArgument() {
        override val isAll: Boolean get() = false

        private var mutableType: ResolvableType<*>? = type
        override val type: ResolvableType<*> get() = mutableType ?: error("Type argument $name has not been resolved yet.")

        private var mutableIsRevolved = type != null
        override val isResolved: Boolean get() = mutableIsRevolved

        override fun tryResolve(
            name: String, type: KType,
            instances: MutableMap<KType, ResolvableType<*>>,
            arguments: MutableMap<String, ResolvableTypeArgument>
        ): Boolean {
            if (isResolved) {
                return false
            }
            var result = false

            val classifier = kotlin.classifier
            if (classifier is KTypeParameter && classifier.name == name) {
                kotlin = type
                mutableType = instances.createResolvableType(type)
                result = true
            }

            for (upperBound in bounds) {
                result = upperBound.tryResolve(name, type, instances, arguments) || result
            }

            if (result) {
                flushIsResolved()
            }
            return result
        }

        private fun flushIsResolved() {
            mutableIsRevolved = (mutableType?.isResolved == true) && bounds.all { it.isResolved }
        }
    }

    private var mutableIsResolved: Boolean = false
    override val isResolved: Boolean get() = (isParameterizedClass && mutableIsResolved) || !isParameterizedClass

    private fun tryResolve(
        name: String, type: KType,
        instances: MutableMap<KType, ResolvableType<*>>,
        arguments: MutableMap<String, ResolvableTypeArgument>
    ): Boolean {
        if (isResolved) {
            return false
        }
        var result = false

        for ((typeArgumentName, typeArgument) in typeArguments) {
            if (typeArgument !is ResolvableTypeArgumentImpl) {
                continue
            }
            result = typeArgument.tryResolve(name, type, instances, arguments) || result

            for (parentType in parentTypes) {
                parentType as ResolvableTypeImpl
                parentType.tryResolve(typeArgumentName, typeArgument.kotlin, instances, arguments)
            }
        }

        if (result) {
            flushIsResolved()
        }
        return result
    }

    private fun flushIsResolved() {
        mutableIsResolved = typeArguments.values.all { it.isResolved }
    }

    init {
        // 把初始化到一半的类型放入 instances，避免在解析 String : Comparable<String>
        // 这样的类型时，解析里层泛型参数时递归导致栈溢出。
        instances[rawType] = this

        rawClass = when (val classifier = rawType.classifier) {
            is KClass<*> -> classifier as KClass<Any>
            else -> throw IllegalArgumentException("Can not create a resolvable type of classifier: $classifier (type: $rawType)")
        }
        isNullable = rawType.isMarkedNullable

        parentTypes = rawClass.supertypes.map { instances.createResolvableType(it) }

        val typeParameters = rawClass.typeParameters
        val typeParameterCount = typeParameters.size

        if (typeParameterCount > 0) {
            typeParameterNames = typeParameters.map { it.name }

            val typeArgumentLocal = mutableMapOf<String, AbstractResolvableTypeArgument>()
            typeArguments = typeArgumentLocal

            val typeArguments = rawType.arguments
            val directTypeArguments = typeArguments.subList(typeArguments.size - typeParameterCount, typeArguments.size)

            for (i in 0 until typeParameterCount) {
                val argument = directTypeArguments[i]
                val parameter = typeParameters[i]

                val type = argument.type
                val variance = argument.variance

                if (type == null || variance == null) {
                    typeArgumentLocal[parameter.name] = AllResolvableTypeArgument(parameter.name, argument)
                    continue
                }

                typeArgumentLocal[parameter.name] = when (val classifier = type.classifier) {
                    is KClass<*> -> {
                        for (parentType in parentTypes) {
                            parentType.tryResolve(parameter.name, type, instances, typeArgumentLocal as MutableMap<String, ResolvableTypeArgument>)
                        }

                        ResolvableTypeArgumentImpl(
                            name = parameter.name,
                            variance = variance,
                            type = instances.createResolvableType(type),
                            argument = argument,
                            bounds = emptyList(),
                            kotlin = type
                        )
                    }
                    is KTypeParameter -> {
                        val upperBounds = mutableListOf<AbstractResolvableTypeArgumentBound>()
                        for (upperBound in classifier.upperBounds) {
                            val finalUpperBound = when (val upperBoundClassifier = upperBound.classifier) {
                                is KClass<*> -> CompletedResolvableTypeArgumentBound(instances.createResolvableType(upperBound))
                                is KTypeParameter -> LazyInitArgumentResolvableTypeArgumentBound(upperBoundClassifier.name)
                                else -> throw IllegalArgumentException(
                                    "Unexpected classifier of upper bound $upperBound " +
                                            "of type argument ${parameter.name} of class $rawType is $upperBoundClassifier!")
                            }
                            upperBounds.add(finalUpperBound)
                        }
                        ResolvableTypeArgumentImpl(
                            name = parameter.name,
                            variance = variance,
                            argument = argument,
                            bounds = upperBounds,
                            kotlin = type
                        )
                    }
                    else -> error("Unexpected type argument classifier: $classifier for argument type $type.")
                }
            }

            flushIsResolved()
        } else {
            typeParameterNames = emptyList()
            typeArguments = emptyMap()
        }
    }

    override fun isAssignableFrom(type: ResolvableType<*>): Boolean {
        if (this == type) {
            return true
        }
        if (!rawClass.isSuperclassOf(type.rawClass)) {
            return false
        }

        for ((typeArgumentName, typeArgument) in typeArguments) {
            val thatTypeParameter = type.getTypeParameterOrFail(rawClass, typeArgumentName)
            if (thatTypeParameter.isAll) {
                continue
            }

            require(typeArgument.isResolved) { "Type argument $typeArgumentName in type $rawType has not been resolved yet! " }

            if (thatTypeParameter.bounds.isEmpty()) {
                val result = when (val variance = typeArgument.variance) {
                    KVariance.IN -> thatTypeParameter.type!!.isAssignableFrom(typeArgument.type!!)
                    KVariance.OUT -> typeArgument.type!!.isAssignableFrom(thatTypeParameter.type!!)
                    KVariance.INVARIANT -> typeArgument.type == thatTypeParameter.type
                    null -> error("Unexpected type variance: null")
                }
                if (!result) {
                    return false
                }
            } else {
                for (bound in thatTypeParameter.bounds) {
                    if (!bound.type.isAssignableFrom(thatTypeParameter.type!!)) {
                        return false
                    }
                }
            }
        }

        return isNullable || !type.isNullable
    }

    override fun getTypeParameter(rawClass: KClass<*>, name: String): ResolvableTypeArgument? {
        if (rawClass == this.rawClass) {
            return typeArguments[name]
        }
        for (parentType in parentTypes) {
            parentType.getTypeParameter(rawClass, name)?.let { return it }
        }
        return null
    }

    override fun getTypeParameter(rawClass: KClass<*>, index: Int): ResolvableTypeArgument? {
        if (rawClass == this.rawClass) {
            return typeParameterNames.getOrNull(index)?.let { typeArguments[it] }
        }
        for (parentType in parentTypes) {
            parentType.getTypeParameter(rawClass, index)?.let { return it }
        }
        return null
    }

    override fun getTypeParameterOrFail(rawClass: KClass<*>, name: String): ResolvableTypeArgument {
        return getTypeParameter(rawClass, name) ?: throw NoSuchElementException("No such type argument called $name defined in $rawClass.")
    }

    override fun getTypeParameterOrFail(rawClass: KClass<*>, index: Int): ResolvableTypeArgument {
        return getTypeParameter(rawClass, index) ?: throw NoSuchElementException("No $index-th type argument defined in $rawClass.")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResolvableTypeImpl

        return rawType == other.rawType
    }

    override fun hashCode(): Int {
        return rawType.hashCode()
    }
}
