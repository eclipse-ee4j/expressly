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

import org.glassfish.expressly.lang.EvaluationContext;

import jakarta.el.ELException;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public final class AstDynamicExpression extends SimpleNode {
    public AstDynamicExpression(int id) {
        super(id);
    }

    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        return children[0].getType(ctx);
    }

    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {
        return children[0].getValue(ctx);
    }

    @Override
    public boolean isReadOnly(EvaluationContext ctx) throws ELException {
        return children[0].isReadOnly(ctx);
    }

    @Override
    public void setValue(EvaluationContext ctx, Object value) throws ELException {
        children[0].setValue(ctx, value);
    }
}
