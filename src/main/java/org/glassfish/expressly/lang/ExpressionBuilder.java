/*
 * Copyright (c) 2022, 2022 Contributors to the Eclipse Foundation.
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates and others.
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

import java.io.StringReader;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import org.glassfish.expressly.MethodExpressionImpl;
import org.glassfish.expressly.MethodExpressionLiteral;
import org.glassfish.expressly.ValueExpressionImpl;
import org.glassfish.expressly.parser.AstCompositeExpression;
import org.glassfish.expressly.parser.AstDeferredExpression;
import org.glassfish.expressly.parser.AstDynamicExpression;
import org.glassfish.expressly.parser.AstFunction;
import org.glassfish.expressly.parser.AstIdentifier;
import org.glassfish.expressly.parser.AstLiteralExpression;
import org.glassfish.expressly.parser.AstMethodArguments;
import org.glassfish.expressly.parser.AstValue;
import org.glassfish.expressly.parser.ELParser;
import org.glassfish.expressly.parser.ELParserTokenManager;
import org.glassfish.expressly.parser.Node;
import org.glassfish.expressly.parser.NodeVisitor;
import org.glassfish.expressly.parser.ParseException;
import org.glassfish.expressly.parser.SimpleCharStream;
import org.glassfish.expressly.util.MessageFactory;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.FunctionMapper;
import jakarta.el.MethodExpression;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @author Kin-man Chung // EL cache
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public final class ExpressionBuilder implements NodeVisitor {

    static private class NodeSoftReference extends SoftReference<Node> {
        final String key;

        NodeSoftReference(String key, Node node, ReferenceQueue<Node> refQ) {
            super(node, refQ);
            this.key = key;
        }
    }

    static private class SoftConcurrentHashMap extends ConcurrentHashMap<String, Node> {

        private static final long serialVersionUID = 1L;
        private static final int CACHE_INIT_SIZE = 256;
        private ConcurrentHashMap<String, NodeSoftReference> map = new ConcurrentHashMap<>(CACHE_INIT_SIZE);
        private ReferenceQueue<Node> refQ = new ReferenceQueue<>();

        // Remove map entries that have been placed on the queue by GC.
        private void cleanup() {
            NodeSoftReference nodeRef = null;
            while ((nodeRef = (NodeSoftReference) refQ.poll()) != null) {
                map.remove(nodeRef.key);
            }
        }

        @Override
        public Node put(String key, Node value) {
            cleanup();
            NodeSoftReference prev = map.put(key, new NodeSoftReference(key, value, refQ));
            return prev == null ? null : prev.get();
        }

        @Override
        public Node putIfAbsent(String key, Node value) {
            cleanup();
            NodeSoftReference prev = map.putIfAbsent(key, new NodeSoftReference(key, value, refQ));
            return prev == null ? null : prev.get();
        }

        @Override
        public Node get(Object key) {
            cleanup();
            NodeSoftReference nodeRef = map.get(key);
            if (nodeRef == null) {
                return null;
            }
            if (nodeRef.get() == null) {
                // value has been garbage collected, remove entry in map
                map.remove(key);
                return null;
            }
            return nodeRef.get();
        }
    }

    private static final SoftConcurrentHashMap cache = new SoftConcurrentHashMap();
    private FunctionMapper fnMapper;
    private VariableMapper varMapper;
    private String expression;

    /**
     *
     */
    public ExpressionBuilder(String expression, ELContext ctx) throws ELException {
        this.expression = expression;

        FunctionMapper ctxFn = ctx.getFunctionMapper();
        VariableMapper ctxVar = ctx.getVariableMapper();

        if (ctxFn != null) {
            this.fnMapper = new FunctionMapperFactory(ctxFn);
        }
        if (ctxVar != null) {
            this.varMapper = new VariableMapperFactory(ctxVar);
        }
    }

    public static Node createNode(String expr) throws ELException {
        Node n = createNodeInternal(expr);
        return n;
    }

    private static Node createNodeInternal(String expr) throws ELException {
        if (expr == null) {
            throw new ELException(MessageFactory.get("error.null"));
        }

        Node node = cache.get(expr);
        if (node == null) {
            try {
                node = (new ELParser(
                        new ELParserTokenManager(new SimpleCharStream(new StringReader(expr), 1, 1, expr.length() + 1))))
                                .CompositeExpression();

                // validate composite expression
                if (node instanceof AstCompositeExpression) {
                    int numChildren = node.jjtGetNumChildren();
                    if (numChildren == 1) {
                        node = node.jjtGetChild(0);
                    } else {
                        Class<?> type = null;
                        Node child = null;
                        for (int i = 0; i < numChildren; i++) {
                            child = node.jjtGetChild(i);
                            if (child instanceof AstLiteralExpression) {
                                continue;
                            }

                            if (type == null) {
                                type = child.getClass();
                            } else {
                                if (!type.equals(child.getClass())) {
                                    throw new ELException(MessageFactory.get("error.mixed", expr));
                                }
                            }
                        }
                    }
                }
                if (node instanceof AstDeferredExpression || node instanceof AstDynamicExpression) {
                    node = node.jjtGetChild(0);
                }
                cache.putIfAbsent(expr, node);
            } catch (ParseException pe) {
                throw new ELException("Error Parsing: " + expr, pe);
            }
        }

        return node;
    }

    /**
     * Scan the expression nodes and captures the functions and variables used in this expression. This ensures that any
     * changes to the functions or variables mappings during the expression will not affect the evaluation of this
     * expression, as the functions and variables are bound and resolved at parse time, as specified in the spec.
     */
    private void prepare(Node node) throws ELException {
        node.accept(this);
        if (fnMapper instanceof FunctionMapperFactory) {
            fnMapper = ((FunctionMapperFactory) fnMapper).create();
        }
        if (varMapper instanceof VariableMapperFactory) {
            varMapper = ((VariableMapperFactory) varMapper).create();
        }
    }

    private Node build() throws ELException {
        Node node = createNodeInternal(this.expression);
        prepare(node);
        if (node instanceof AstDeferredExpression || node instanceof AstDynamicExpression) {
            node = node.jjtGetChild(0);
        }

        return node;
    }

    @Override
    public void visit(Node node) throws ELException {
        if (node instanceof AstFunction) {
            AstFunction funcNode = (AstFunction) node;
            if ((funcNode.getPrefix().length() == 0)
                    && (fnMapper == null || fnMapper.resolveFunction(funcNode.getPrefix(), funcNode.getLocalName()) == null)) {
                // This can be a call to a LambdaExpression. The target
                // of the call is a bean or an Jakarta Expression variable. Capture
                // the variable name in the variable mapper if it is an
                // variable. The decision to invoke the static method or
                // the LambdaExpression will be made at runtime.
                if (varMapper != null) {
                    varMapper.resolveVariable(funcNode.getLocalName());
                }

                return;
            }

            if (this.fnMapper == null) {
                throw new ELException(MessageFactory.get("error.fnMapper.null"));
            }

            Method functionMethod = fnMapper.resolveFunction(funcNode.getPrefix(), funcNode.getLocalName());
            if (functionMethod == null) {
                throw new ELException(MessageFactory.get("error.fnMapper.method", funcNode.getOutputName()));
            }

            int parameterCount = functionMethod.getParameterCount();
            int argumentCount = ((AstMethodArguments) node.jjtGetChild(0)).getParameterCount();
            if (argumentCount != parameterCount) {
                throw new ELException(MessageFactory.get("error.fnMapper.paramcount", funcNode.getOutputName(), parameterCount, argumentCount));
            }
        } else if (node instanceof AstIdentifier && varMapper != null) {
            String variable = ((AstIdentifier) node).getImage();

            // Simply capture it
            varMapper.resolveVariable(variable);
        }
    }

    public ValueExpression createValueExpression(Class<?> expectedType) throws ELException {
        return new ValueExpressionImpl(expression, build(), fnMapper, varMapper, expectedType);
    }

    public MethodExpression createMethodExpression(Class<?> expectedReturnType, Class<?>[] expectedParamTypes) throws ELException {
        Node node = build();

        if (node instanceof AstValue || node instanceof AstIdentifier) {
            return new MethodExpressionImpl(expression, node, fnMapper, varMapper, expectedReturnType, expectedParamTypes);
        }

        if (node instanceof AstLiteralExpression) {
            return new MethodExpressionLiteral(expression, expectedReturnType, expectedParamTypes);
        }

        throw new ELException("Not a Valid Method Expression: " + expression);
    }
}
