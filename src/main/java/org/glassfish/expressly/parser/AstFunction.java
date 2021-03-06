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

package org.glassfish.expressly.parser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.glassfish.expressly.lang.EvaluationContext;
import org.glassfish.expressly.util.MessageFactory;

import jakarta.el.ELClass;
import jakarta.el.ELException;
import jakarta.el.FunctionMapper;
import jakarta.el.LambdaExpression;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public final class AstFunction extends SimpleNode {

    protected String localName = "";

    protected String prefix = "";

    public AstFunction(int id) {
        super(id);
    }

    public String getLocalName() {
        return localName;
    }

    public String getOutputName() {
        if (this.prefix.length() == 0) {
            return this.localName;
        } else {
            return this.prefix + ":" + this.localName;
        }
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        FunctionMapper functionMapper = ctx.getFunctionMapper();

        // quickly validate again for this request
        if (functionMapper == null) {
            throw new ELException(MessageFactory.get("error.fnMapper.null"));
        }

        Method functionMethod = functionMapper.resolveFunction(this.prefix, this.localName);
        if (functionMethod == null) {
            throw new ELException(MessageFactory.get("error.fnMapper.method", this.getOutputName()));
        }

        return functionMethod.getReturnType();
    }

    /*
     * Find the object associated with the given name. Return null if the there is no such object.
     */
    private Object findValue(EvaluationContext ctx, String name) {
        // First check if this is a Lambda argument
        if (ctx.isLambdaArgument(name)) {
            return ctx.getLambdaArgument(name);
        }

        // Next check if this an Jakarta Expression variable
        VariableMapper varMapper = ctx.getVariableMapper();
        if (varMapper != null) {
            ValueExpression expr = varMapper.resolveVariable(name);
            if (expr != null) {
                return expr.getValue(ctx.getELContext());
            }
        }

        // Check if this is resolvable by an ELResolver
        ctx.setPropertyResolved(false);
        Object ret = ctx.getELResolver().getValue(ctx, null, name);
        if (ctx.isPropertyResolved()) {
            return ret;
        }
        return null;
    }

    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {

        // Check to see if a function is a bean that is a Lambdaexpression.
        // If so, invoke it. Also allow for the case that a Lambda expression
        // can return another Lambda expression.
        if (prefix.length() == 0) {
            Object val = findValue(ctx, this.localName);
            // Check the case of repeated lambda invocation, such as f()()()

            if ((val != null) && (val instanceof LambdaExpression)) {
                for (int i = 0; i < this.children.length; i++) {
                    Object[] params = ((AstMethodArguments) this.children[i]).getParameters(ctx);
                    if (!(val instanceof LambdaExpression)) {
                        throw new ELException(MessageFactory.get("error.function.syntax", getOutputName()));
                    }
                    val = ((LambdaExpression) val).invoke(ctx, params);
                }
                return val;
            }
        }

        FunctionMapper functionMapper = ctx.getFunctionMapper();

        Method functionMethod = null;
        if (functionMapper != null) {
            functionMethod = functionMapper.resolveFunction(this.prefix, this.localName);
        }

        if (functionMethod == null) {
            if (this.prefix.length() == 0 && ctx.getImportHandler() != null) {

                // Check if this is a constructor call for an imported class
                Class<?> resolvedClass = ctx.getImportHandler().resolveClass(this.localName);
                String methodName = null;
                if (resolvedClass != null) {
                    methodName = "<init>";
                } else {
                    // Check if this is a imported static method
                    resolvedClass = ctx.getImportHandler().resolveStatic(this.localName);
                    methodName = this.localName;
                    ;
                }

                if (resolvedClass != null) {
                    // Use StaticFieldELResolver to invoke the constructor or the
                    // static method.
                    Object[] params = ((AstMethodArguments) this.children[0]).getParameters(ctx);
                    return ctx.getELResolver().invoke(ctx, new ELClass(resolvedClass), methodName, null, params);
                }
            }

            // quickly validate for this request
            if (functionMapper == null) {
                throw new ELException(MessageFactory.get("error.fnMapper.null"));
            }

            throw new ELException(MessageFactory.get("error.fnMapper.method", this.getOutputName()));
        }

        Class<?>[] paramTypes = functionMethod.getParameterTypes();
        Object[] params = ((AstMethodArguments) this.children[0]).getParameters(ctx);
        Object result = null;
        for (int i = 0; i < params.length; i++) {
            try {
                params[i] = ctx.convertToType(params[i], paramTypes[i]);
            } catch (ELException ele) {
                throw new ELException(MessageFactory.get("error.function", this.getOutputName()), ele);
            }
        }

        try {
            result = functionMethod.invoke(null, params);
        } catch (IllegalAccessException iae) {
            throw new ELException(MessageFactory.get("error.function", this.getOutputName()), iae);
        } catch (InvocationTargetException ite) {
            throw new ELException(MessageFactory.get("error.function", this.getOutputName()), ite.getCause());
        }

        return result;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String toString() {
        return ELParserTreeConstants.jjtNodeName[id] + "[" + this.getOutputName() + "]";
    }
}
