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

package org.glassfish.expressly.lang;

import static org.glassfish.expressly.util.ReflectionUtil.toTypeArray;
import static org.glassfish.expressly.util.ReflectionUtil.toTypeNameArray;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import jakarta.el.FunctionMapper;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @author kchung
 */
public class FunctionMapperImpl extends FunctionMapper implements Externalizable {

    private static final long serialVersionUID = 1L;

    protected Map<String, Function> functions;

    @Override
    public Method resolveFunction(String prefix, String localName) {
        if (functions == null) {
            return null;
        }

        return functions.get(prefix + ":" + localName).getMethod();
    }

    public void addFunction(String prefix, String localName, Method method) {
        if (functions == null) {
            functions = new HashMap<>();
        }

        Function function = new Function(prefix, localName, method);
        synchronized (this) {
            functions.put(prefix + ":" + localName, function);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(functions);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        functions = (Map<String, Function>) in.readObject();
    }

    public static class Function implements Externalizable {

        protected transient Method method;
        protected String owner;
        protected String name;
        protected String[] types;
        protected String prefix;
        protected String localName;

        public Function(String prefix, String localName, Method method) {
            if (localName == null) {
                throw new NullPointerException("LocalName cannot be null");
            }

            if (method == null) {
                throw new NullPointerException("Method cannot be null");
            }
            this.prefix = prefix;
            this.localName = localName;
            this.method = method;
        }

        public Function() {
            // for serialization
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF((prefix != null) ? prefix : "");
            out.writeUTF(localName);

            if (owner != null) {
                out.writeUTF(owner);
            } else {
                out.writeUTF(method.getDeclaringClass().getName());
            }
            if (name != null) {
                out.writeUTF(name);
            } else {
                out.writeUTF(method.getName());
            }
            if (types != null) {
                out.writeObject(types);
            } else {
                out.writeObject(toTypeNameArray(method.getParameterTypes()));
            }
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            prefix = in.readUTF();
            if ("".equals(prefix)) {
                prefix = null;
            }
            localName = in.readUTF();
            owner = in.readUTF();
            name = in.readUTF();
            types = (String[]) in.readObject();
        }

        public Method getMethod() {
            if (method == null) {
                try {
                    this.method = Class.forName(owner, false, Thread.currentThread().getContextClassLoader())
                                       .getMethod(name, toTypeArray(types));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return method;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Function) {
                return this.hashCode() == obj.hashCode();
            }

            return false;
        }

        @Override
        public int hashCode() {
            return (prefix + localName).hashCode();
        }
    }

}
