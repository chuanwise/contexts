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
@file:JvmName("Reflections")

package cn.chuanwise.contexts.util

import cn.chuanwise.contexts.context.Context
import cn.chuanwise.contexts.annotation.AnnotationModule
import cn.chuanwise.contexts.annotation.ArgumentResolveContextImpl
import cn.chuanwise.contexts.annotation.ArgumentResolver
import cn.chuanwise.contexts.annotation.ArgumentResolverFactory
import cn.chuanwise.contexts.annotation.DefaultArgumentResolverFactory
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

private val EMPTY_CLASS_ARRAY = emptyArray<Class<*>>()
private val EMPTY_ANY_ARRAY = emptyArray<Any>()

@ContextsInternalApi
val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)

@ContextsInternalApi
val Class<*>.isFinal: Boolean get() = Modifier.isFinal(modifiers)

@ContextsInternalApi
fun Class<*>.getDeclaredMethodOrNull(name: String, vararg parameterTypes: Class<*> = EMPTY_CLASS_ARRAY): Method? {
    return try {
        getDeclaredMethod(name, *parameterTypes)
    } catch (e: NoSuchMethodException) {
        null
    }
}

@ContextsInternalApi
fun Class<*>.getDeclaredStaticMethodOrNull(name: String, vararg parameterTypes: Class<*> = EMPTY_CLASS_ARRAY): Method? {
    return getDeclaredMethodOrNull(name, *parameterTypes).takeIf { it?.isStatic == true }
}

@ContextsInternalApi
fun Class<*>.getMethodOrNull(name: String, vararg parameterTypes: Class<*> = EMPTY_CLASS_ARRAY): Method? {
    return try {
        getMethod(name, *parameterTypes)
    } catch (e: NoSuchMethodException) {
        null
    }
}

@ContextsInternalApi
fun Class<*>.getDeclaredFieldOrNull(name: String): Field? {
    return try {
        getDeclaredField(name)
    } catch (e: NoSuchFieldException) {
        null
    }
}

@ContextsInternalApi
fun Class<*>.getDeclaredStaticFieldOrNull(name: String): Field? {
    return getDeclaredFieldOrNull(name).takeIf { it?.isStatic == true }
}

@ContextsInternalApi
fun Class<*>.getConstructorOrNull(
    vararg parameterTypes: Class<*> = EMPTY_CLASS_ARRAY
) = try {
    getConstructor(*parameterTypes)
} catch (e: NoSuchMethodException) {
    null
}

@ContextsInternalApi
fun Field.getSafe(instance: Any): Any? {
    return if (canAccess(instance)) {
        get(instance)
    } else {
        trySetAccessible()
        try {
            get(instance)
        } catch (e: IllegalAccessException) {
            null
        }
    }
}

@ContextsInternalApi
@Suppress("DEPRECATION")
fun Field.getStaticSafe(): Any? {
    return if (isAccessible) {
        get(declaringClass)
    } else {
        trySetAccessible()
        try {
            get(declaringClass)
        } catch (e: IllegalAccessException) {
            null
        }
    }
}

@ContextsInternalApi
fun Method.invokeSafe(instance: Any, vararg args: Any? = EMPTY_ANY_ARRAY): Any? {
    return if (canAccess(instance)) {
        invoke(instance, *args)
    } else {
        trySetAccessible()
        try {
            invoke(instance, *args)
        } catch (e: IllegalAccessException) {
            null
        }
    }
}

@ContextsInternalApi
fun <T : Any> Constructor<T>.newInstanceSafe(vararg args: Any? = EMPTY_ANY_ARRAY): T? {
    return if (canAccess(null)) {
        newInstance(*args)
    } else {
        trySetAccessible()
        try {
            newInstance(*args)
        } catch (e: IllegalAccessException) {
            null
        }
    }
}

@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
fun <T : Any> Class<T>.getInstanceOrFail(
    getInstanceMethodName: String = "getInstance"
) : T {
    getDeclaredStaticMethodOrNull(getInstanceMethodName)?.let {
        it.invokeSafe(this)?.takeIf { value ->
            isInstance(value)
        }?.let { value ->
            return value as T
        }
    }
    getDeclaredStaticFieldOrNull("INSTANCE")?.let {
        it.getStaticSafe()?.takeIf { value ->
            isInstance(value)
        }?.let { value ->
            return value as T
        }
    }

    getConstructorOrNull()
        ?.newInstanceSafe()
        ?.let { value -> return value as T }

    error("Cannot find instance of class $this. " +
            "Please ensure that the class has a static method named $getInstanceMethodName, " +
            "a static field named INSTANCE, or a public no-args constructor.")
}

@ContextsInternalApi
fun <T> KFunction<T>.callByAndRethrowException(arguments: Map<KParameter, Any?>): T {
    try {
        return if (isAccessible) {
            callBy(arguments)
        } else {
            isAccessible = true
            callBy(arguments)
        }
    } catch (e: InvocationTargetException) {
        throw e.cause!!
    }
}

@ContextsInternalApi
suspend fun <T> KFunction<T>.callSuspendByAndRethrowException(arguments: Map<KParameter, Any?>): T {
    try {
        return if (isAccessible) {
            callSuspendBy(arguments)
        } else {
            isAccessible = true
            callSuspendBy(arguments)
        }
    } catch (e: InvocationTargetException) {
        throw e.cause!!
    }
}

/**
 * 解析针对某种对象的函数。
 *
 * @param subjectSuperClass
 */
@ContextsInternalApi
@Suppress("UNCHECKED_CAST")
fun <T : Any> Context.parseSubjectClassAndCollectArgumentResolvers(
    functionClass: KClass<*>,
    function: KFunction<*>,
    defaultArgumentResolverFactory: ArgumentResolverFactory = DefaultArgumentResolverFactory,
    subjectAnnotationClass: KClass<out Annotation>? = null,
    subjectSuperClass: KClass<T>? = null,
    defaultSubjectClass: KClass<*>? = null
): Pair<Map<KParameter, ArgumentResolver>, KClass<out T>> {
    val parameters = function.parameters
    val argumentResolvers = mutableMapOf<KParameter, ArgumentResolver>()

    var subjectClass: KClass<*>? = defaultSubjectClass
    val annotationModule = contextManager.getBeanOrFail<AnnotationModule>()
    for (parameter in parameters) {
        val annotations = parameter.annotations

        // 检查是否有 @SubjectAnnotation 注解。
        if (subjectAnnotationClass != null) {
            val annotation = annotations.firstOrNull { subjectAnnotationClass.isInstance(it) }
            if (annotation != null) {
                val parameterClass = parameter.type.classifier as KClass<*>
                subjectClass = if (subjectClass == null) {
                    parameterClass
                } else {
                    require(subjectClass == parameterClass) {
                        "Cannot set multiple different subject classes near parameter ${parameter.name} " +
                                "with annotation @${subjectAnnotationClass.simpleName} " +
                                "(specified ${subjectClass!!.simpleName} and ${parameterClass.simpleName}) " +
                                "for function ${function.name} declared in ${functionClass.simpleName}. " +
                                "Details: " +
                                "function: $function, " +
                                "class where function declared in: ${functionClass.qualifiedName}, " +
                                "subject super class: ${subjectSuperClass?.qualifiedName}, " +
                                "subject annotation class: ${subjectAnnotationClass.qualifiedName}. "
                    }
                    parameterClass
                }
            }
        }
        if (parameter.kind == KParameter.Kind.EXTENSION_RECEIVER) {
            val parameterClass = parameter.type.classifier as KClass<*>
            subjectClass = if (subjectClass == null) {
                parameterClass
            } else {
                require(subjectClass == parameterClass) {
                    "Cannot set multiple different subject classes near parameter ${parameter.name} " +
                            "with extension receiver for function ${function.name} declared in ${functionClass.simpleName}. " +
                            "Details: " +
                            "function: $function, " +
                            "class where function declared in: ${functionClass.qualifiedName}, " +
                            "subject super class: ${subjectSuperClass?.qualifiedName}, " +
                            "subject annotation class: ${subjectAnnotationClass?.qualifiedName}. "
                }
                parameterClass
            }
        }

        val argumentResolveContext = ArgumentResolveContextImpl(functionClass, function, parameter, this)
        var argumentResolver = annotationModule.createArgumentResolver(argumentResolveContext)
        if (argumentResolver == null) {
            argumentResolver = defaultArgumentResolverFactory.tryCreateArgumentResolver(argumentResolveContext)
        }

        require(argumentResolver != null || parameter.isOptional) {
            "Cannot find argument resolver for required parameter ${parameter.name} " +
                    "for function ${function.name} declared in ${functionClass.simpleName}. " +
                    "Details: " +
                    "function: $function, " +
                    "class where function declared in: ${functionClass.qualifiedName}, " +
                    "subject super class: ${subjectSuperClass?.qualifiedName}, " +
                    "subject annotation class: ${subjectAnnotationClass?.qualifiedName}. "
        }
        if (argumentResolver != null) {
            argumentResolvers[parameter] = argumentResolver
        }
    }

    // 如果没有找到主体类，则尝试从参数中找到唯一的值参数。
    if (subjectClass == null) {
        val onlyOneValueParameter = parameters.singleOrNull { it.kind == KParameter.Kind.VALUE }
        if (onlyOneValueParameter != null) {
            subjectClass = onlyOneValueParameter.type.classifier as KClass<*>
        }
    }

    requireNotNull(subjectClass) {
        "Cannot find subject class for function ${function.name} declared in ${functionClass.simpleName}. " +
                "Details: " +
                "function: $function, " +
                "class where function declared in: ${functionClass.qualifiedName}, " +
                "subject super class: ${subjectSuperClass?.qualifiedName}, " +
                "subject annotation class: ${subjectAnnotationClass?.qualifiedName}. "
    }
    require(subjectSuperClass == null || subjectSuperClass.isSuperclassOf(subjectClass)) {
        "Subject class ${subjectClass.qualifiedName} is not assignable from required subject super class ${subjectSuperClass!!.qualifiedName} " +
                "for function ${function.name} declared in ${functionClass.simpleName}. " +
                "Details: " +
                "function: $function, " +
                "class where function declared in: ${functionClass.qualifiedName}, " +
                "subject super class: ${subjectSuperClass.qualifiedName}, " +
                "subject annotation class: ${subjectAnnotationClass?.qualifiedName}. "
    }

    return argumentResolvers to subjectClass as KClass<out T>
}

