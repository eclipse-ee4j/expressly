/*
 * Copyright (c) 2022, 2022 Contributors to the Eclipse Foundation.
 * Copyright (c) 2012, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.expressly.stream;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;

/*
 * This ELResolver intercepts method calls to a Collections, to provide
 * support for collection operations.
 */

public class StreamELResolver extends ELResolver {

    @Override
    public Object invoke(final ELContext context, final Object base, final Object method, final Class<?>[] paramTypes, final Object[] params) {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<Object> collection = (Collection<Object>) base;
            if ("stream".equals(method) && params.length == 0) {
                context.setPropertyResolved(true);
                return new Stream(collection.iterator());
            }
        }

        if (base.getClass().isArray()) {
            if ("stream".equals(method) && params.length == 0) {
                context.setPropertyResolved(true);
                return new Stream(arrayIterator(base));
            }
        }

        return null;
    }

    private static Iterator<Object> arrayIterator(final Object base) {
        final int size = Array.getLength(base);
        return new Iterator<Object>() {
            int index = 0;
            boolean yielded;
            Object current;

            @Override
            public boolean hasNext() {
                if ((!yielded) && index < size) {
                    current = Array.get(base, index++);
                    yielded = true;
                }
                return yielded;
            }

            @Override
            public Object next() {
                yielded = false;
                return current;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return false;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return String.class;
    }
}
