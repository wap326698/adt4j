/*
 * Copyright (c) 2016, Victor Nazarov &lt;asviraspossible@gmail.com&gt;
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation and/or
 *     other materials provided with the distribution.
 *
 *  3. Neither the name of the copyright holder nor the names of its contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.github.sviperll.codemold;

import com.github.sviperll.codemold.render.Renderer;
import com.github.sviperll.codemold.render.RendererContext;
import java.util.Iterator;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 *
 * @author Victor Nazarov &lt;asviraspossible@gmail.com&gt;
 */
@ParametersAreNonnullByDefault
public abstract class ConstructorDefinition extends ExecutableDefinition<ConstructorType, ConstructorDefinition> {

    ConstructorDefinition(ExecutableDefinition.Implementation<ConstructorType, ConstructorDefinition> implementation) {
        super(implementation);
    }

    @Override
    final ConstructorType createType(ExecutableType.Implementation<ConstructorType, ConstructorDefinition> implementation) {
        return new ConstructorType(implementation);
    }

    @Override
    final ConstructorDefinition fromGenericDefinition() {
        return this;
    }

    @Override
    public Renderer createRenderer(final RendererContext context) {
        return new Renderer() {
            @Override
            public void render() {
                context.appendRenderable(nesting());
                context.appendWhiteSpace();
                context.appendWhiteSpace();
                context.appendRenderable(typeParameters());
                context.appendWhiteSpace();
                context.appendText(nesting().parent().simpleTypeName());
                context.appendText("(");
                Iterator<? extends VariableDeclaration> parameters = parameters().iterator();
                if (parameters.hasNext()) {
                    VariableDeclaration parameter = parameters.next();
                    context.appendRenderable(parameter);
                    while (parameters.hasNext()) {
                        context.appendText(", ");
                        parameter = parameters.next();
                        context.appendRenderable(parameter);
                    }
                }
                context.appendText(")");
                Iterator<? extends AnyType> throwsExceptions = throwsList().iterator();
                if (throwsExceptions.hasNext()) {
                    AnyType exceptionType = throwsExceptions.next();
                    context.appendWhiteSpace();
                    context.appendText("throws");
                    context.appendWhiteSpace();
                    context.appendRenderable(exceptionType);
                    while (throwsExceptions.hasNext()) {
                        exceptionType = throwsExceptions.next();
                        context.appendText(", ");
                        context.appendRenderable(exceptionType);
                    }
                }
                context.appendWhiteSpace();
                context.appendRenderable(body());
                context.appendLineBreak();
            }
        };
    }
}