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

package org.glassfish.expressly;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.glassfish.expressly.lang.ELSupport;
import org.glassfish.expressly.lang.ExpressionBuilder;
import org.glassfish.expressly.stream.StreamELResolver;
import org.glassfish.expressly.util.MessageFactory;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.MethodExpression;
import jakarta.el.ValueExpression;

/**
 * @see ExpressionFactory
 *
 * @author Jacob Hookom [jacob@hookom.net]
 * @author Kin-man Chung
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public class ExpressionFactoryImpl extends ExpressionFactory {

    private Properties properties;
    private boolean isBackwardCompatible22;

    public ExpressionFactoryImpl() {
        super();
    }

    public ExpressionFactoryImpl(Properties properties) {
        super();
        this.properties = properties;
        this.isBackwardCompatible22 = "true".equals(getProperty("jakarta.el.bc2.2"));
    }

    /**
     * Coerces an object to a specific type according to the Jakarta Expression Language type conversion rules. The custom
     * type conversions in the <code>ELResolver</code>s are not considered.
     *
     * Jakarta Expression Language version 2.2 backward compatibility conversion rules apply if ExpressionFactoryImpl was created with property
     * "jakarta.el.bc2.2" set to true.
     */
    @Override
    public <T> T coerceToType(Object obj, Class<T> type) {
        try {
            return ELSupport.coerceToType(null, obj, type, isBackwardCompatible22);
        } catch (IllegalArgumentException ex) {
            throw new ELException(ex);
        }
    }

    @Override
    public MethodExpression createMethodExpression(ELContext context, String expression, Class<?> expectedReturnType, Class<?>[] expectedParamTypes) {
        MethodExpression methodExpression =
                new ExpressionBuilder(expression, context)
                    .createMethodExpression(expectedReturnType, expectedParamTypes);

        if (expectedParamTypes == null && !methodExpression.isParametersProvided()) {
            throw new NullPointerException(MessageFactory.get("error.method.nullParms"));
        }

        return methodExpression;
    }

    @Override
    public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
        if (expectedType == null) {
            throw new NullPointerException(MessageFactory.get("error.value.expectedType"));
        }

        return new ExpressionBuilder(expression, context).createValueExpression(expectedType);
    }

    @Override
    public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
        if (expectedType == null) {
            throw new NullPointerException(MessageFactory.get("error.value.expectedType"));
        }

        return new ValueExpressionLiteral(instance, expectedType);
    }

    public String getProperty(String key) {
        if (properties == null) {
            return null;
        }

        return properties.getProperty(key);
    }

    @Override
    public ELResolver getStreamELResolver() {
        return new StreamELResolver();
    }

    @Override
    public Map<String, Method> getInitFunctionMap() {
        return new HashMap<String, Method>();
    }
}
