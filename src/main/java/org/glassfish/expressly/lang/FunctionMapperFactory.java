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

import java.lang.reflect.Method;

import jakarta.el.FunctionMapper;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @author kchung
 */
public class FunctionMapperFactory extends FunctionMapper {

    protected FunctionMapperImpl memento;
    protected FunctionMapper target;

    public FunctionMapperFactory(FunctionMapper mapper) {
        if (mapper == null) {
            throw new NullPointerException("FunctionMapper target cannot be null");
        }
        this.target = mapper;
    }

    @Override
    public Method resolveFunction(String prefix, String localName) {
        if (memento == null) {
            memento = new FunctionMapperImpl();
        }
        Method functionMethod = target.resolveFunction(prefix, localName);
        if (functionMethod != null) {
            memento.addFunction(prefix, localName, functionMethod);
        }

        return functionMethod;
    }

    public FunctionMapper create() {
        return memento;
    }

}
