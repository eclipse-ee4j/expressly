/*
 * Copyright (c) 2022, 2022 Contributors to the Eclipse Foundation.
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
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

import static org.glassfish.expressly.util.ReflectionUtil.buildParameters;
import static org.glassfish.expressly.util.ReflectionUtil.findMethod;
import static org.glassfish.expressly.util.ReflectionUtil.getTypesFromValues;
import static org.glassfish.expressly.util.ReflectionUtil.invokeMethod;

import java.lang.reflect.Method;

import org.glassfish.expressly.lang.ELSupport;
import org.glassfish.expressly.lang.EvaluationContext;
import org.glassfish.expressly.util.MessageFactory;
import org.glassfish.expressly.util.ReflectionUtil;

import jakarta.el.ELClass;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.ImportHandler;
import jakarta.el.MethodInfo;
import jakarta.el.MethodReference;
import jakarta.el.PropertyNotFoundException;
import jakarta.el.PropertyNotWritableException;
import jakarta.el.ValueReference;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @author Kin-man Chung
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public final class AstValue extends SimpleNode {

    protected static class Target {
        private final Object base;

        private final Node suffixNode;
        private final EvaluationContext ctx;

        Target(Object base, Node suffixNode, EvaluationContext ctx) {
            this.base = base;
            this.suffixNode = suffixNode;
            this.ctx = ctx;
        }

        public Object getBase() {
            return base;
        }

        public String getMethodName() {
            return getProperty().toString();
        }

        public Object getProperty() {
            return suffixNode.getValue(ctx);
        }

        boolean isMethodCall() {
            return getArguments(suffixNode) != null;
        }

        Object[] getParamValues() {
            AstMethodArguments arguments = getArguments(suffixNode);
            if (arguments == null) {
                return null;
            }

            return arguments.getParameters(ctx);
        }

        Class<?>[] getFormalParamTypes() {
            AstMethodArguments arguments = getArguments(suffixNode);
            if (arguments == null) {
                return null;
            }

            return arguments.getParamTypes();
        }


        Class<?>[] getActualParamTypes() {
            Object[] values = getParamValues();
            if (values == null) {
                return null;
            }

            return getTypesFromValues(values);
        }
    }

    public AstValue(int id) {
        super(id);
    }

    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        Target target = getTarget(ctx);
        if (target.isMethodCall()) {
            return null;
        }

        Object property = target.getProperty();
        ctx.setPropertyResolved(false);
        Class<?> type = ctx.getELResolver().getType(ctx, target.getBase(), property);
        if (!ctx.isPropertyResolved()) {
            ELSupport.throwUnhandled(target.base, property);
        }

        return type;
    }

    @Override
    public ValueReference getValueReference(EvaluationContext ctx) throws ELException {
        Target target = getTarget(ctx);
        if (target.isMethodCall()) {
            return null;
        }

        return new ValueReference(target.getBase(), target.getProperty());
    }


    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {
        Object value = getBase(ctx);
        int propCount = jjtGetNumChildren();
        int i = 1;
        while (value != null && i < propCount) {
            value = getValue(value, children[i], ctx);
            i++;
        }

        return value;
    }

    @Override
    public boolean isReadOnly(EvaluationContext ctx) throws ELException {
        Target target = getTarget(ctx);
        if (target.isMethodCall()) {
            return true;
        }

        Object property = target.getProperty();
        ctx.setPropertyResolved(false);
        boolean isReadOnly = ctx.getELResolver().isReadOnly(ctx, target.getBase(), property);
        if (!ctx.isPropertyResolved()) {
            ELSupport.throwUnhandled(target.getBase(), property);
        }

        return isReadOnly;
    }

    @Override
    public void setValue(EvaluationContext ctx, Object value) throws ELException {
        Target target = getTarget(ctx);
        if (target.isMethodCall()) {
            throw new PropertyNotWritableException(MessageFactory.get("error.syntax.set"));
        }
        Object property = target.getProperty();
        ELResolver elResolver = ctx.getELResolver();

        /*
         * Note by kchung 10/2013 The spec does not say if the value should be cocerced to the target type before setting the
         * value to the target. The conversion is kept here to be backward compatible.
         */
        ctx.setPropertyResolved(false);
        Class<?> targetType = elResolver.getType(ctx, target.getBase(), property);
        if (ctx.isPropertyResolved()) {
            ctx.setPropertyResolved(false);
            Object targetValue = elResolver.convertToType(ctx, value, targetType);

            if (ctx.isPropertyResolved()) {
                value = targetValue;
            } else {
                if (value != null || (targetType != null && targetType.isPrimitive())) {
                    value = ELSupport.coerceToType(ctx.getELContext(), value, targetType);
                }
            }
        }

        ctx.setPropertyResolved(false);
        elResolver.setValue(ctx, target.getBase(), property, value);
        if (!ctx.isPropertyResolved()) {
            ELSupport.throwUnhandled(target.getBase(), property);
        }
    }

    @Override
    public MethodInfo getMethodInfo(EvaluationContext ctx, Class<?>[] paramTypes) throws ELException {
        Target target = getTarget(ctx);

        Method method = findMethod(
                            target.getBase().getClass(),
                            target.getMethodName(),
                            paramTypes,
                            target.getParamValues());

        return new MethodInfo(method.getName(), method.getReturnType(), method.getParameterTypes());
    }

    @Override
    public MethodReference getMethodReference(EvaluationContext ctx) {
        Target target = getTarget(ctx);

        Method method = ReflectionUtil.findMethod(
                target.getBase().getClass(),
                target.getMethodName(),
                target.getActualParamTypes(),
                target.getParamValues());

        return new MethodReference(
            target.getBase(),
            getMethodInfo(ctx, target.getActualParamTypes()),
            method.getAnnotations(),
            buildParameters(
                ctx.getELContext(),
                target.getActualParamTypes(),
                method.isVarArgs(),
                target.getParamValues()));
    }


    @Override
    public Object invoke(EvaluationContext ctx, Class<?>[] paramTypes, Object[] paramValues) throws ELException {
        Target target = getTarget(ctx);

        if (target.isMethodCall()) {
            ctx.setPropertyResolved(false);

            return ctx.getELResolver().invoke(
                ctx,
                target.getBase(),
                target.getMethodName(),
                target.getFormalParamTypes(), // Use the param types in expression, and ignore those from elsewhere, e.g. TLD
                target.getParamValues());
        }

        return invokeMethod(
                ctx,
                findMethod(
                    target.getBase().getClass(),
                    target.getProperty().toString(),
                    paramTypes, paramValues),
                target.getBase(),
                paramValues);
    }

    @Override
    public boolean isParametersProvided() {
        return getArguments(this.children[this.jjtGetNumChildren() - 1]) != null;
    }



    // ### Private methods

    private static AstMethodArguments getArguments(Node node) {
        if (node instanceof AstDotSuffix && node.jjtGetNumChildren() > 0) {
            return (AstMethodArguments) node.jjtGetChild(0);
        }

        if (node instanceof AstBracketSuffix && node.jjtGetNumChildren() > 1) {
            return (AstMethodArguments) node.jjtGetChild(1);
        }

        return null;
    }

    private Object getValue(Object base, Node child, EvaluationContext ctx) throws ELException {
        Object value = null;
        ELResolver resolver = ctx.getELResolver();
        Object property = child.getValue(ctx);
        AstMethodArguments args = getArguments(child);
        if (args != null) {
            // This is a method call
            if (!(property instanceof String)) {
                throw new ELException(MessageFactory.get("error.method.name", property));
            }
            Class<?>[] paramTypes = args.getParamTypes();
            Object[] params = args.getParameters(ctx);

            ctx.setPropertyResolved(false);
            value = resolver.invoke(ctx, base, property, paramTypes, params);
        } else {
            if (property != null) {
                ctx.setPropertyResolved(false);
                value = resolver.getValue(ctx, base, property);
                if (!ctx.isPropertyResolved()) {
                    ELSupport.throwUnhandled(base, property);
                }
            }
        }

        return value;
    }

    private Object getBase(EvaluationContext ctx) {
        try {
            return children[0].getValue(ctx);
        } catch (PropertyNotFoundException ex) {
            // Next check if the base is an imported class
            if (children[0] instanceof AstIdentifier) {
                String name = ((AstIdentifier) children[0]).image;
                ImportHandler importHandler = ctx.getImportHandler();
                if (importHandler != null) {
                    Class<?> resolvedClass = importHandler.resolveClass(name);
                    if (resolvedClass != null) {
                        return new ELClass(resolvedClass);
                    }
                }
            }

            throw ex;
        }
    }

    private Target getTarget(EvaluationContext ctx) throws ELException {
        // Evaluate expr-a to value-a
        Object base = getBase(ctx);

        // If our base is null (we know there are more properties to evaluate)
        if (base == null) {
            throw new PropertyNotFoundException(MessageFactory.get("error.unreachable.base", children[0].getImage()));
        }

        // Set up our start/end
        Object property = null;
        int propCount = this.jjtGetNumChildren() - 1;
        int i = 1;

        // Evaluate any properties before our target
        if (propCount > 1) {
            while (base != null && i < propCount) {
                base = getValue(base, this.children[i], ctx);
                i++;
            }

            // If we are in this block, we have more properties to resolve,
            // but our base was null
            if (base == null) {
                throw new PropertyNotFoundException(MessageFactory.get("error.unreachable.property", property));
            }
        }

        return new Target(base, children[propCount], ctx);
    }

}
