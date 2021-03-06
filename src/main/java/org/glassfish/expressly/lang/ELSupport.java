/*
 * Copyright (c) 2022, 2022 Contributors to the Eclipse Foundation.
 * Copyright (c) 1997, 2021 Oracle and/or its affiliates and others.
 * All rights reserved.
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

package org.glassfish.expressly.lang;

import static java.lang.reflect.Modifier.isAbstract;
import static org.glassfish.expressly.lang.ELArithmetic.isNumberType;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.function.BiConsumer;

import org.glassfish.expressly.util.MessageFactory;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.LambdaExpression;
import jakarta.el.PropertyNotFoundException;

/**
 * A helper class that implements the Jakarta Expression Specification
 *
 * @author Jacob Hookom [jacob@hookom.net]
 * @author Kin-man Chung
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public class ELSupport {

    private final static Long ZERO = Long.valueOf(0L);

    public final static void throwUnhandled(Object base, Object property) throws ELException {
        if (base == null) {
            throw new PropertyNotFoundException(MessageFactory.get("error.resolver.unhandled.null", property));
        }

        throw new PropertyNotFoundException(MessageFactory.get("error.resolver.unhandled", base.getClass(), property));
    }

    /**
     * @param obj0 First object to be compared
     * @param obj1 Second object to be compared
     * @return The result (an int with values -1, 0, or 1) of the comparison
     * @throws ELException when something goes wrong
     */
    public final static int compare(final Object obj0, final Object obj1) throws ELException {
        if (obj0 == obj1 || equals(obj0, obj1)) {
            return 0;
        }

        if (isBigDecimalOp(obj0, obj1)) {
            BigDecimal bd0 = (BigDecimal) coerceToNumber(obj0, BigDecimal.class);
            BigDecimal bd1 = (BigDecimal) coerceToNumber(obj1, BigDecimal.class);
            return bd0.compareTo(bd1);
        }
        if (isDoubleOp(obj0, obj1)) {
            Double d0 = (Double) coerceToNumber(obj0, Double.class);
            Double d1 = (Double) coerceToNumber(obj1, Double.class);
            return d0.compareTo(d1);
        }
        if (isBigIntegerOp(obj0, obj1)) {
            BigInteger bi0 = (BigInteger) coerceToNumber(obj0, BigInteger.class);
            BigInteger bi1 = (BigInteger) coerceToNumber(obj1, BigInteger.class);
            return bi0.compareTo(bi1);
        }
        if (isLongOp(obj0, obj1)) {
            Long l0 = (Long) coerceToNumber(obj0, Long.class);
            Long l1 = (Long) coerceToNumber(obj1, Long.class);
            return l0.compareTo(l1);
        }
        if (obj0 instanceof String || obj1 instanceof String) {
            return coerceToString(obj0).compareTo(coerceToString(obj1));
        }
        if (obj0 instanceof Comparable) {
            // Safe cast
            @SuppressWarnings("unchecked")
            Comparable<Object> cobj0 = (Comparable<Object>) obj0;
            return (obj1 != null) ? cobj0.compareTo(obj1) : 1;
        }
        if (obj1 instanceof Comparable) {
            // Safe cast
            @SuppressWarnings("unchecked")
            Comparable<Object> cobj1 = (Comparable<Object>) obj1;
            return (obj0 != null) ? -(cobj1.compareTo(obj0)) : -1;
        }

        throw new ELException(MessageFactory.get("error.compare", obj0, obj1));
    }

    /**
     * @param obj0 Fisrt object to be compared
     * @param obj1 Second object to be compared
     * @return true if the objects compared equal
     * @throws ELException when something goes wrong
     */
    public final static boolean equals(final Object obj0, final Object obj1) throws ELException {
        if (obj0 == obj1) {
            return true;
        }
        if (obj0 == null || obj1 == null) {
            return false;
        }
        if (isBigDecimalOp(obj0, obj1)) {
            BigDecimal bd0 = (BigDecimal) coerceToNumber(obj0, BigDecimal.class);
            BigDecimal bd1 = (BigDecimal) coerceToNumber(obj1, BigDecimal.class);
            return bd0.equals(bd1);
        }
        if (isDoubleOp(obj0, obj1)) {
            Double d0 = (Double) coerceToNumber(obj0, Double.class);
            Double d1 = (Double) coerceToNumber(obj1, Double.class);
            return d0.equals(d1);
        }
        if (isBigIntegerOp(obj0, obj1)) {
            BigInteger bi0 = (BigInteger) coerceToNumber(obj0, BigInteger.class);
            BigInteger bi1 = (BigInteger) coerceToNumber(obj1, BigInteger.class);
            return bi0.equals(bi1);
        }
        if (isLongOp(obj0, obj1)) {
            Long l0 = (Long) coerceToNumber(obj0, Long.class);
            Long l1 = (Long) coerceToNumber(obj1, Long.class);
            return l0.equals(l1);
        }
        if (obj0 instanceof Boolean || obj1 instanceof Boolean) {
            return coerceToBoolean(obj0).equals(coerceToBoolean(obj1));
        }
        if (obj0.getClass().isEnum()) {
            return obj0.equals(coerceToEnum(obj1, obj0.getClass()));
        }
        if (obj1.getClass().isEnum()) {
            return obj1.equals(coerceToEnum(obj0, obj1.getClass()));
        }
        if (obj0 instanceof String || obj1 instanceof String) {
            return coerceToString(obj0).equals(coerceToString(obj1));
        }
        return obj0.equals(obj1);
    }

    /**
     * @param obj Object to be coerced
     * @return The result of coercion
     */
    public final static Boolean coerceToBoolean(final Object obj) throws IllegalArgumentException {
        if (obj == null || "".equals(obj)) {
            return Boolean.FALSE;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.valueOf((String) obj);
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert", obj, obj.getClass(), Boolean.class));
    }

    // Enum types are hard construct. We can declare this as
    // <T extends Enum<T>> T coerceToEnum(Object, Class<T> type)
    // but this makes it harder to get the calls right.
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public final static Enum coerceToEnum(final Object obj, Class type) {
        if (obj == null || "".equals(obj)) {
            return null;
        }
        if (obj.getClass().isEnum()) {
            return (Enum) obj;
        }

        return Enum.valueOf(type, obj.toString());
    }

    private static Object coerceToArray(ELContext elContext, final Object sourceArray, final Class<?> type) {
        int arrayLength = Array.getLength(sourceArray);
        Class<?> arrayComponentType = type.getComponentType();

        Object coercedArray = Array.newInstance(arrayComponentType, arrayLength);

        for (int i = 0; i < arrayLength; i++) {
            Array.set(coercedArray, i, coerceToType(elContext, Array.get(sourceArray, i), arrayComponentType));
        }

        return coercedArray;
    }

    @SuppressWarnings("unchecked")
    private static <T> T coerceToFunctionalInterface(final ELContext ctx, final LambdaExpression lambdaExpression, final Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[] { type, BiConsumer.class },
                new LambdaHandler(ctx, lambdaExpression));
    }

    public static class LambdaHandler implements InvocationHandler {

        private ELContext elContext;
        private LambdaExpression lambdaExpression;

        public LambdaHandler(ELContext elContext, LambdaExpression lambdaExpression) {
           this.elContext = elContext;
           this.lambdaExpression = lambdaExpression;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (isElContextSetter(method, args)) {
                setElContext((ELContext) args[1]);
                return null;
            }

            if (!isAbstract(method.getModifiers())) {
                return method.invoke(proxy, args);
            }

            return lambdaExpression.invoke(elContext, args);
        }

        public void setElContext(ELContext elContext) {
            this.elContext = elContext;
        }

        public ELContext getElContext() {
            return elContext;
        }

        private boolean isElContextSetter(Method method, Object[] args) {
            return
                method.getDeclaringClass().equals(BiConsumer.class) &&
                method.getName().equals("accept") &&
                args[0] instanceof String && args[0].toString().equals("org.glassfish.expressly.setElContext") &&
                args[1] instanceof ELContext
                ;
        }
    }


    public final static Character coerceToCharacter(final Object obj) throws IllegalArgumentException {
        if (obj == null || "".equals(obj)) {
            return Character.valueOf((char) 0);
        }
        if (obj instanceof String) {
            return Character.valueOf(((String) obj).charAt(0));
        }
        if (ELArithmetic.isNumber(obj)) {
            return Character.valueOf((char) ((Number) obj).shortValue());
        }

        Class<?> objType = obj.getClass();
        if (obj instanceof Character) {
            return (Character) obj;
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert", obj, objType, Character.class));
    }

    public final static Number coerceToNumber(final Object obj) {
        if (obj == null) {
            return ZERO;
        }
        if (obj instanceof Number) {
            return (Number) obj;
        }

        String str = coerceToString(obj);
        if (isStringFloat(str)) {
            return toFloat(str);
        }

        return toNumber(str);
    }

    protected final static Number coerceToNumber(final Number number, final Class<?> type) throws IllegalArgumentException {
        if (Long.TYPE == type || Long.class.equals(type)) {
            return Long.valueOf(number.longValue());
        }
        if (Double.TYPE == type || Double.class.equals(type)) {
            return Double.valueOf(number.doubleValue());
        }
        if (Integer.TYPE == type || Integer.class.equals(type)) {
            return Integer.valueOf(number.intValue());
        }
        if (BigInteger.class.equals(type)) {
            if (number instanceof BigDecimal) {
                return ((BigDecimal) number).toBigInteger();
            }
            if (number instanceof BigInteger) {
                return number;
            }
            return BigInteger.valueOf(number.longValue());
        }
        if (BigDecimal.class.equals(type)) {
            if (number instanceof BigDecimal) {
                return number;
            }
            if (number instanceof BigInteger) {
                return new BigDecimal((BigInteger) number);
            }
            if (number instanceof Long) {
                return new BigDecimal((Long) number);
            }
            return new BigDecimal(number.doubleValue());
        }
        if (Byte.TYPE == type || Byte.class.equals(type)) {
            return Byte.valueOf(number.byteValue());
        }
        if (Short.TYPE == type || Short.class.equals(type)) {
            return Short.valueOf(number.shortValue());
        }
        if (Float.TYPE == type || Float.class.equals(type)) {
            return Float.valueOf(number.floatValue());
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert", number, number.getClass(), type));
    }

    public final static Number coerceToNumber(final Object obj, final Class<?> type) throws IllegalArgumentException {
        if (obj == null || "".equals(obj)) {
            return coerceToNumber(ZERO, type);
        }
        if (obj instanceof String) {
            return coerceToNumber((String) obj, type);
        }
        if (ELArithmetic.isNumber(obj)) {
            if (obj.getClass().equals(type)) {
                return (Number) obj;
            }
            return coerceToNumber((Number) obj, type);
        }

        if (obj instanceof Character) {
            return coerceToNumber(Short.valueOf((short) ((Character) obj).charValue()), type);
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert", obj, obj.getClass(), type));
    }

    protected final static Number coerceToNumber(final String val, final Class<?> type) throws IllegalArgumentException {
        if (Long.TYPE == type || Long.class.equals(type)) {
            return Long.valueOf(val);
        }
        if (Integer.TYPE == type || Integer.class.equals(type)) {
            return Integer.valueOf(val);
        }
        if (Double.TYPE == type || Double.class.equals(type)) {
            return Double.valueOf(val);
        }
        if (BigInteger.class.equals(type)) {
            return new BigInteger(val);
        }
        if (BigDecimal.class.equals(type)) {
            return new BigDecimal(val);
        }
        if (Byte.TYPE == type || Byte.class.equals(type)) {
            return Byte.valueOf(val);
        }
        if (Short.TYPE == type || Short.class.equals(type)) {
            return Short.valueOf(val);
        }
        if (Float.TYPE == type || Float.class.equals(type)) {
            return Float.valueOf(val);
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert", val, String.class, type));
    }

    /**
     * @param obj Object to be coerced
     * @return The result of coercion
     */
    @SuppressWarnings("rawtypes")
    public final static String coerceToString(final Object obj) {
        if (obj == null) {
            return "";
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof Enum) {
            return ((Enum) obj).name();
        }

        return obj.toString();
    }

    public final static void checkType(final Object obj, final Class<?> type) throws IllegalArgumentException {
        if (String.class.equals(type)) {
            coerceToString(obj);
        }
        if (isNumberType(type)) {
            coerceToNumber(obj, type);
        }
        if (Character.class.equals(type) || Character.TYPE == type) {
            coerceToCharacter(obj);
        }
        if (Boolean.class.equals(type) || Boolean.TYPE == type) {
            coerceToBoolean(obj);
        }
        if (type.isEnum()) {
            coerceToEnum(obj, type);
        }
    }

    public final static <T> T coerceToType(ELContext elContext, final Object obj, final Class<T> type) throws IllegalArgumentException {
        return coerceToType(elContext, obj, type, false);
    }

    @SuppressWarnings("unchecked")
    public final static <T> T coerceToType(ELContext elContext, final Object obj, final Class<T> type, boolean isEL22Compatible) throws IllegalArgumentException {
        if (type == null || Object.class.equals(type) || (obj != null && type.isAssignableFrom(obj.getClass()))) {
            return (T) obj;
        }

        // New in 3.0
        if (!isEL22Compatible && obj == null && !type.isPrimitive() && !String.class.equals(type)) {
            return null;
        }

        if (String.class.equals(type)) {
            return (T) coerceToString(obj);
        }

        if (ELArithmetic.isNumberType(type)) {
            return (T) coerceToNumber(obj, type);
        }

        if (Character.class.equals(type) || type == Character.TYPE) {
            return (T) coerceToCharacter(obj);
        }

        if (Boolean.class.equals(type) || type == Boolean.TYPE) {
            return (T) coerceToBoolean(obj);
        }

        if (type.isEnum()) {
            return (T) coerceToEnum(obj, type);
        }

        if (obj == null) {
            return null;
        }

        if (obj instanceof String) {
            if ("".equals(obj)) {
                return null;
            }
            PropertyEditor editor = PropertyEditorManager.findEditor(type);
            if (editor != null) {
                editor.setAsText((String) obj);
                return (T) editor.getValue();
            }
        }

        // New in 5.0
        if (type.isArray()) {
            return (T) coerceToArray(elContext, obj, type);
        }

        if (obj instanceof LambdaExpression && isFunctionalInterface(type)) {
            T result = coerceToFunctionalInterface(elContext, (LambdaExpression) obj, type);
            return result;
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert", obj, obj.getClass(), type));
    }

    /**
     * @param obj An array of objects
     * @return true if the array contains a null, false otherwise
     */
    public final static boolean containsNulls(final Object[] obj) {
        for (int i = 0; i < obj.length; i++) {
            if (obj[0] == null) {
                return true;
            }
        }
        return false;
    }

    public final static boolean isBigDecimalOp(final Object obj0, final Object obj1) {
        return (obj0 instanceof BigDecimal || obj1 instanceof BigDecimal);
    }

    public final static boolean isBigIntegerOp(final Object obj0, final Object obj1) {
        return (obj0 instanceof BigInteger || obj1 instanceof BigInteger);
    }

    public final static boolean isDoubleOp(final Object obj0, final Object obj1) {
        return (obj0 instanceof Double || obj1 instanceof Double || obj0 instanceof Float || obj1 instanceof Float);
    }

    public final static boolean isDoubleStringOp(final Object obj0, final Object obj1) {
        return (isDoubleOp(obj0, obj1) || (obj0 instanceof String && isStringFloat((String) obj0)) || (obj1 instanceof String && isStringFloat((String) obj1)));
    }

    public final static boolean isLongOp(final Object obj0, final Object obj1) {
        return (obj0 instanceof Long || obj1 instanceof Long || obj0 instanceof Integer || obj1 instanceof Integer || obj0 instanceof Character
                || obj1 instanceof Character || obj0 instanceof Short || obj1 instanceof Short || obj0 instanceof Byte || obj1 instanceof Byte);
    }

    public final static boolean isStringFloat(final String str) {
        int len = str.length();
        if (len > 1) {
            for (int i = 0; i < len; i++) {
                switch (str.charAt(i)) {
                case 'E':
                    return true;
                case 'e':
                    return true;
                case '.':
                    return true;
                }
            }
        }
        return false;
    }

    public final static Number toFloat(final String value) {
        try {
            if (Double.parseDouble(value) > Double.MAX_VALUE) {
                return new BigDecimal(value);
            }

            return Double.valueOf(value);

        } catch (NumberFormatException e0) {
            return new BigDecimal(value);
        }
    }

    public final static Number toNumber(final String value) {
        try {
            return Integer.valueOf(Integer.parseInt(value));
        } catch (NumberFormatException e0) {
            try {
                return Long.valueOf(Long.parseLong(value));
            } catch (NumberFormatException e1) {
                return new BigInteger(value);
            }
        }
    }

    private static boolean isFunctionalInterface(Class<?> type) {
        return
            type.isInterface() &&
            Arrays.stream(type.getMethods())
                  .filter(method -> isAbstract(method.getModifiers()) && !isInObject(method))
                  .count() == 1;
    }


    private static boolean isInObject(Method method) {
        switch (method.getName()) {
            case "equals":
                return
                        method.getParameterCount() == 1 &&
                        method.getParameterTypes()[0].equals(Object.class) &&
                        method.getReturnType().equals(boolean.class);
            case "hashCode":
                return
                        method.getParameterCount() == 0 &&
                        method.getReturnType().equals(int.class);
            case "toString":
                return
                        method.getParameterCount() == 0 &&
                        method.getReturnType().equals(String.class);
            default:
                return false;
        }
    }



    public ELSupport() {
        super();
    }
}
