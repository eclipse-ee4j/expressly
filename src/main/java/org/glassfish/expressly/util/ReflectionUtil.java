/*
 * Copyright (c) 2022, 2022 Contributors to the Eclipse Foundation.
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.expressly.util;

import static java.beans.Introspector.getBeanInfo;
import static java.lang.reflect.Modifier.isPublic;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.glassfish.expressly.lang.ELSupport;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.MethodNotFoundException;
import jakarta.el.PropertyNotFoundException;

/**
 * Utilities for Managing Serialization and Reflection
 *
 * @author Jacob Hookom [jacob@hookom.net]
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public class ReflectionUtil {

    protected static final String[] EMPTY_STRING = new String[0];

    protected static final String[] PRIMITIVE_NAMES = new String[] {
        "boolean",
        "byte",
        "char",
        "double",
        "float",
        "int",
        "long",
        "short",
        "void" };

    protected static final Class<?>[] PRIMITIVES = new Class[] {
        boolean.class,
        byte.class,
        char.class,
        double.class,
        float.class,
        int.class,
        long.class,
        short.class,
        Void.TYPE };

    /**
     *
     */
    private ReflectionUtil() {
        super();
    }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        if (name == null || "".equals(name)) {
            return null;
        }

        Class<?> clazz = forNamePrimitive(name);
        if (clazz == null) {
            if (name.endsWith("[]")) {
                clazz = Array.newInstance(
                                  Class.forName(
                                      name.substring(0, name.length() - 2),
                                      true,
                                      Thread.currentThread().getContextClassLoader()),
                                  0)
                              .getClass();
            } else {
                clazz = Class.forName(
                            name,
                            true,
                            Thread.currentThread().getContextClassLoader());
            }
        }

        return clazz;
    }

    protected static Class<?> forNamePrimitive(String name) {
        if (name.length() <= 8) {
            int index = Arrays.binarySearch(PRIMITIVE_NAMES, name);
            if (index >= 0) {
                return PRIMITIVES[index];
            }
        }

        return null;
    }

    /**
     * Converts an array of Class names to Class types
     *
     * @param classNames
     * @return The array of Classes
     * @throws ClassNotFoundException
     */
    public static Class<?>[] toTypeArray(String[] classNames) throws ClassNotFoundException {
        if (classNames == null) {
            return null;
        }

        Class<?>[] typeArray = new Class[classNames.length];
        for (int i = 0; i < classNames.length; i++) {
            typeArray[i] = forName(classNames[i]);
        }

        return typeArray;
    }

    /**
     * Converts an array of Class types to Class names
     *
     * @param classTypes
     * @return The array of Classes
     */
    public static String[] toTypeNameArray(Class<?>[] classTypes) {
        if (classTypes == null) {
            return null;
        }

        String[] classNames = new String[classTypes.length];
        for (int i = 0; i < classTypes.length; i++) {
            classNames[i] = classTypes[i].getName();
        }

        return classNames;
    }

    /**
     * @param base The base object
     * @param property The property
     * @return The PropertyDescriptor for the base with the given property
     * @throws ELException
     * @throws PropertyNotFoundException
     */
    public static PropertyDescriptor getPropertyDescriptor(Object base, Object property) throws ELException, PropertyNotFoundException {
        String name = ELSupport.coerceToString(property);
        try {
            PropertyDescriptor[] descriptor = getBeanInfo(base.getClass()).getPropertyDescriptors();
            for (int i = 0; i < descriptor.length; i++) {
                if (descriptor[i].getName().equals(name)) {
                    return descriptor[i];
                }
            }
        } catch (IntrospectionException ie) {
            throw new ELException(ie);
        }

        throw new PropertyNotFoundException(MessageFactory.get("error.property.notfound", base, name));
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    public static Object invokeMethod(ELContext context, Method method, Object base, Object[] params) {
        Object[] parameters = buildParameters(context, method.getParameterTypes(), method.isVarArgs(), params);

        try {
            return method.invoke(base, parameters);
        } catch (IllegalAccessException | IllegalArgumentException iae) {
            throw new ELException(iae);
        } catch (InvocationTargetException ite) {
            throw new ELException(ite.getCause());
        }
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    public static Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes, Object[] paramValues) {
        if (clazz == null || methodName == null) {
            throw new MethodNotFoundException(MessageFactory.get("error.method.notfound", clazz, methodName, paramString(paramTypes)));
        }

        if (paramTypes == null) {
            paramTypes = getTypesFromValues(paramValues);
        }

        Wrapper result = findWrapper(clazz, Wrapper.wrap(clazz.getMethods(), methodName), methodName, paramTypes, paramValues);
        if (result == null) {
            return null;
        }

        return getMethod(clazz, (Method) result.unWrap());
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private static Wrapper findWrapper(Class<?> clazz, List<Wrapper> wrappers, String name, Class<?>[] requiredParamTypes, Object[] requiredParamValues) {
        List<Wrapper> assignableCandidates = new ArrayList<Wrapper>();
        List<Wrapper> coercibleCandidates = new ArrayList<Wrapper>();
        List<Wrapper> varArgsCandidates = new ArrayList<Wrapper>();

        int requiredParamCount = getParamCount(requiredParamTypes);

        for (Wrapper wrapper : wrappers) {
            Class<?>[] candidateParamTypes = wrapper.getParameterTypes();

            int canidateParamCount = getParamCount(candidateParamTypes);

            // Check the number of parameters
            if (!(requiredParamCount == canidateParamCount || (wrapper.isVarArgs() && requiredParamCount >= canidateParamCount - 1))) {
                // Method has wrong number of parameters
                continue;
            }

            // Check the parameters match
            boolean assignable = false;
            boolean coercible = false;
            boolean varArgs = false;
            boolean noMatch = false;

            for (int i = 0; i < canidateParamCount; i++) {
                if (i == (canidateParamCount - 1) && wrapper.isVarArgs()) {
                    varArgs = true;

                    // Exact var array type match
                    if (canidateParamCount == requiredParamCount) {
                        if (candidateParamTypes[i] == requiredParamTypes[i]) {
                            continue;
                        }
                    }

                    // Unwrap the array's component type
                    Class<?> varType = candidateParamTypes[i].getComponentType();
                    for (int j = i; j < requiredParamCount; j++) {
                        if (
                           !isAssignableFrom(requiredParamTypes[j], varType) &&
                           !(requiredParamValues != null && j < requiredParamValues.length && isCoercibleFrom(requiredParamValues[j], varType))) {
                            noMatch = true;
                            break;
                        }
                    }
                } else if (candidateParamTypes[i].equals(requiredParamTypes[i])) {
                    // no-op
                } else if (isAssignableFrom(requiredParamTypes[i], candidateParamTypes[i])) {
                    assignable = true;
                } else {
                    if (requiredParamValues == null || i >= requiredParamValues.length) {
                        noMatch = true;
                        break;
                    } else {
                        if (isCoercibleFrom(requiredParamValues[i], candidateParamTypes[i])) {
                            coercible = true;
                        } else {
                            noMatch = true;
                            break;
                        }
                    }
                }
            }

            if (noMatch) {
                continue;
            }

            if (varArgs) {
                varArgsCandidates.add(wrapper);
            } else if (coercible) {
                coercibleCandidates.add(wrapper);
            } else if (assignable) {
                assignableCandidates.add(wrapper);
            } else {
                // If a method is found where every parameter matches exactly,
                // return it
                return wrapper;
            }

        }

        String errorMsg = MessageFactory.get("error.method.ambiguous", clazz, name, paramString(requiredParamTypes));
        if (!assignableCandidates.isEmpty()) {
            return findMostSpecificWrapper(assignableCandidates, requiredParamTypes, false, errorMsg);
        }

        if (!coercibleCandidates.isEmpty()) {
            return findMostSpecificWrapper(coercibleCandidates, requiredParamTypes, true, errorMsg);
        }

        if (!varArgsCandidates.isEmpty()) {
            return findMostSpecificWrapper(varArgsCandidates, requiredParamTypes, true, errorMsg);
        }

        throw new MethodNotFoundException(MessageFactory.get("error.method.notfound", clazz, name, paramString(requiredParamTypes)));
    }

    private static int getParamCount(Class<?>[] paramTypes) {
        if (paramTypes == null) {
            return 0;
        }

        return paramTypes.length;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private static Wrapper findMostSpecificWrapper(List<Wrapper> candidates, Class<?>[] matchingTypes, boolean elSpecific, String errorMsg) {
        List<Wrapper> ambiguouses = new ArrayList<Wrapper>();
        for (Wrapper candidate : candidates) {
            boolean lessSpecific = false;

            Iterator<Wrapper> it = ambiguouses.iterator();
            while (it.hasNext()) {
                int result = isMoreSpecific(candidate, it.next(), matchingTypes, elSpecific);
                if (result == 1) {
                    it.remove();
                } else if (result == -1) {
                    lessSpecific = true;
                }
            }

            if (!lessSpecific) {
                ambiguouses.add(candidate);
            }
        }

        if (ambiguouses.size() > 1) {
            throw new MethodNotFoundException(errorMsg);
        }

        return ambiguouses.get(0);
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private static int isMoreSpecific(Wrapper wrapper1, Wrapper wrapper2, Class<?>[] matchingTypes, boolean elSpecific) {
        Class<?>[] paramTypes1 = wrapper1.getParameterTypes();
        Class<?>[] paramTypes2 = wrapper2.getParameterTypes();

        if (wrapper1.isVarArgs()) {
            // JLS8 15.12.2.5 Choosing the Most Specific Method
            int length = Math.max(Math.max(paramTypes1.length, paramTypes2.length), matchingTypes.length);
            paramTypes1 = getComparingParamTypesForVarArgsMethod(paramTypes1, length);
            paramTypes2 = getComparingParamTypesForVarArgsMethod(paramTypes2, length);

            if (length > matchingTypes.length) {
                Class<?>[] matchingTypes2 = new Class<?>[length];
                System.arraycopy(matchingTypes, 0, matchingTypes2, 0, matchingTypes.length);
                matchingTypes = matchingTypes2;
            }
        }

        int result = 0;
        for (int i = 0; i < paramTypes1.length; i++) {
            if (paramTypes1[i] != paramTypes2[i]) {
                int r2 = isMoreSpecific(paramTypes1[i], paramTypes2[i], matchingTypes[i], elSpecific);
                if (r2 == 1) {
                    if (result == -1) {
                        return 0;
                    }
                    result = 1;
                } else if (r2 == -1) {
                    if (result == 1) {
                        return 0;
                    }
                    result = -1;
                } else {
                    return 0;
                }
            }
        }

        if (result == 0) {
            // The nature of bridge methods is such that it actually
            // doesn't matter which one we pick as long as we pick
            // one. That said, pick the 'right' one (the non-bridge
            // one) anyway.
            result = Boolean.compare(wrapper1.isBridge(), wrapper2.isBridge());
        }

        return result;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private static int isMoreSpecific(Class<?> type1, Class<?> type2, Class<?> matchingType, boolean elSpecific) {
        type1 = getBoxingTypeIfPrimitive(type1);
        type2 = getBoxingTypeIfPrimitive(type2);

        if (type2.isAssignableFrom(type1)) {
            return 1;
        }

        if (type1.isAssignableFrom(type2)) {
            return -1;
        }

        if (!elSpecific) {
            return 0;
        }

        /*
         * Number will be treated as more specific
         *
         * ASTInteger only return Long or BigInteger, no Byte / Short / Integer. ASTFloatingPoint also.
         *
         */
        if (matchingType != null && Number.class.isAssignableFrom(matchingType)) {
            boolean b1 = Number.class.isAssignableFrom(type1) || type1.isPrimitive();
            boolean b2 = Number.class.isAssignableFrom(type2) || type2.isPrimitive();

            if (b1 && !b2) {
                return 1;
            }

            if (b2 && !b1) {
                return -1;
            }

            return 0;
        }

        return 0;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private static Class<?> getBoxingTypeIfPrimitive(Class<?> clazz) {
        if (!clazz.isPrimitive()) {
            return clazz;
        }

        if (clazz == Boolean.TYPE) {
            return Boolean.class;
        }

        if (clazz == Character.TYPE) {
            return Character.class;
        }

        if (clazz == Byte.TYPE) {
            return Byte.class;
        }

        if (clazz == Short.TYPE) {
            return Short.class;
        }

        if (clazz == Integer.TYPE) {
            return Integer.class;
        }

        if (clazz == Long.TYPE) {
            return Long.class;
        }

        if (clazz == Float.TYPE) {
            return Float.class;
        }

        return Double.class;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private static Class<?>[] getComparingParamTypesForVarArgsMethod(Class<?>[] paramTypes, int length) {
        Class<?>[] result = new Class<?>[length];
        System.arraycopy(paramTypes, 0, result, 0, paramTypes.length - 1);
        Class<?> type = paramTypes[paramTypes.length - 1].getComponentType();
        for (int i = paramTypes.length - 1; i < length; i++) {
            result[i] = type;
        }

        return result;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private static final String paramString(Class<?>[] types) {
        if (types != null) {
            StringBuilder paramString = new StringBuilder();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == null) {
                    paramString.append("null, ");
                } else {
                    paramString.append(types[i].getName()).append(", ");
                }
            }

            if (paramString.length() > 2) {
                paramString.setLength(paramString.length() - 2);
            }

            return paramString.toString();
        }

        return null;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    static boolean isAssignableFrom(Class<?> src, Class<?> target) {
        // src will always be an object
        // Short-cut. null is always assignable to an object and in EL null
        // can always be coerced to a valid value for a primitive
        if (src == null) {
            return true;
        }

        return getBoxingTypeIfPrimitive(target).isAssignableFrom(src);
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private static boolean isCoercibleFrom(Object src, Class<?> target) {
        // TODO: This isn't pretty but it works. Significant refactoring would
        // be required to avoid the exception.
        try {
            ELSupport.coerceToType(src, target);
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    public static Class<?>[] getTypesFromValues(Object[] values) {
        if (values == null) {
            return null;
        }

        Class<?> result[] = new Class<?>[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                result[i] = null;
            } else {
                result[i] = values[i].getClass();
            }
        }

        return result;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     *
     * Get a public method form a public class or interface of a given method. Note that if a PropertyDescriptor is obtained
     * for a non-public class that implements a public interface, the read/write methods will be for the class, and
     * therefore inaccessible. To correct this, a version of the same method must be found in a superclass or interface.
     *
     */
    static Method getMethod(Class<?> type, Method method) {
        if (method == null || isPublic(type.getModifiers())) {
            return method;
        }

        Class<?>[] interfaces = type.getInterfaces();
        Method publicMethod = null;
        for (int i = 0; i < interfaces.length; i++) {
            try {
                publicMethod = interfaces[i].getMethod(method.getName(), method.getParameterTypes());
                publicMethod = getMethod(publicMethod.getDeclaringClass(), publicMethod);
                if (publicMethod != null) {
                    return publicMethod;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }

        Class<?> superClass = type.getSuperclass();
        if (superClass != null) {
            try {
                publicMethod = superClass.getMethod(method.getName(), method.getParameterTypes());
                publicMethod = getMethod(publicMethod.getDeclaringClass(), publicMethod);
                if (publicMethod != null) {
                    return publicMethod;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }

        return null;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    static Constructor<?> getConstructor(Class<?> type, Constructor<?> constructor) {
        if (constructor == null || isPublic(type.getModifiers())) {
            return constructor;
        }

        Constructor<?> publicConstructor = null;
        Class<?> superClass = type.getSuperclass();
        if (superClass != null) {
            try {
                publicConstructor = superClass.getConstructor(constructor.getParameterTypes());
                publicConstructor = getConstructor(publicConstructor.getDeclaringClass(), publicConstructor);
                if (publicConstructor != null) {
                    return publicConstructor;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }

        return null;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    static Object[] buildParameters(ELContext context, Class<?>[] parameterTypes, boolean isVarArgs, Object[] params) {
        Object[] parameters = null;
        if (parameterTypes.length > 0) {
            parameters = new Object[parameterTypes.length];
            int paramCount = params == null ? 0 : params.length;
            if (isVarArgs) {
                int varArgIndex = parameterTypes.length - 1;

                // First argCount-1 parameters are standard
                for (int i = 0; (i < varArgIndex && i < paramCount); i++) {
                    parameters[i] = context.convertToType(params[i], parameterTypes[i]);
                }

                // Last parameter is the varargs
                if (parameterTypes.length == paramCount && parameterTypes[varArgIndex] == params[varArgIndex].getClass()) {
                    parameters[varArgIndex] = params[varArgIndex];
                } else {
                    Class<?> varArgClass = parameterTypes[varArgIndex].getComponentType();
                    final Object varargs = Array.newInstance(varArgClass, (paramCount - varArgIndex));
                    for (int i = (varArgIndex); i < paramCount; i++) {
                        Array.set(varargs, i - varArgIndex, context.convertToType(params[i], varArgClass));
                    }
                    parameters[varArgIndex] = varargs;
                }
            } else {
                for (int i = 0; i < parameterTypes.length && i < paramCount; i++) {
                    parameters[i] = context.convertToType(params[i], parameterTypes[i]);
                }
            }
        }

        return parameters;
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private abstract static class Wrapper {

        public static List<Wrapper> wrap(Method[] methods, String name) {
            List<Wrapper> result = new ArrayList<>();
            for (Method method : methods) {
                if (method.getName().equals(name)) {
                    result.add(new MethodWrapper(method));
                }
            }

            return result;
        }

        public abstract Object unWrap();

        public abstract Class<?>[] getParameterTypes();

        public abstract boolean isVarArgs();

        public abstract boolean isBridge();
    }

    /*
     * This method duplicates code in jakarta.el.ELUtil. When making changes keep the code in sync.
     */
    private static class MethodWrapper extends Wrapper {
        private final Method method;

        public MethodWrapper(Method method) {
            this.method = method;
        }

        @Override
        public Object unWrap() {
            return method;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return method.getParameterTypes();
        }

        @Override
        public boolean isVarArgs() {
            return method.isVarArgs();
        }

        @Override
        public boolean isBridge() {
            return method.isBridge();
        }
    }

}
