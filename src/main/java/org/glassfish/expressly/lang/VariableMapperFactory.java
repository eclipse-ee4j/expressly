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

import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

/**
 * Creates a VariableMapper for the variables used in the expression.
 */
public class VariableMapperFactory extends VariableMapper {

    private final VariableMapper target;
    private VariableMapper momento;

    public VariableMapperFactory(VariableMapper target) {
        if (target == null) {
            throw new NullPointerException("Target VariableMapper cannot be null");
        }
        this.target = target;
    }

    public VariableMapper create() {
        return momento;
    }

    @Override
    public ValueExpression resolveVariable(String variable) {
        ValueExpression valueExpression = target.resolveVariable(variable);
        if (valueExpression != null) {
            if (momento == null) {
                momento = new VariableMapperImpl();
            }
            momento.setVariable(variable, valueExpression);
        }

        return valueExpression;
    }

    @Override
    public ValueExpression setVariable(String variable, ValueExpression expression) {
        throw new UnsupportedOperationException("Cannot Set Variables on Factory");
    }
}
