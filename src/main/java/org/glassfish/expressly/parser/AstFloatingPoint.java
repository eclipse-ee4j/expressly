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

import java.math.BigDecimal;

import org.glassfish.expressly.lang.EvaluationContext;

import jakarta.el.ELException;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public final class AstFloatingPoint extends SimpleNode {
    public AstFloatingPoint(int id) {
        super(id);
    }

    private Number number;

    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        return getFloatingPoint().getClass();
    }

    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {
        return getFloatingPoint();
    }

    public Number getFloatingPoint() {
        if (number == null) {
            try {
                number = Double.valueOf(image);
            } catch (ArithmeticException e0) {
                number = new BigDecimal(image);
            }
        }

        return number;
    }

}
