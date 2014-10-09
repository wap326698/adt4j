/*
 * Copyright (c) 2014, Victor Nazarov <asviraspossible@gmail.com>
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
package com.github.sviperll.adt4j.model;

import com.github.sviperll.adt4j.GenerateValueClassForVisitor;
import com.github.sviperll.adt4j.GenerateValueClassForVisitorProcessor;
import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.AbstractJType;
import com.helger.jcodemodel.EClassType;
import com.helger.jcodemodel.JAnnotationUse;
import com.helger.jcodemodel.JClassAlreadyExistsException;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JExpr;
import com.helger.jcodemodel.JFieldVar;
import com.helger.jcodemodel.JMethod;
import com.helger.jcodemodel.JMod;
import com.helger.jcodemodel.JPackage;
import com.helger.jcodemodel.JTypeVar;
import com.helger.jcodemodel.JVar;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Generated;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

public class ValueClassModelFactory {
    private static final String VISITOR_SUFFIX = "Visitor";
    private static final String VALUE_SUFFIX = "Value";

    public static JDefinedClass createValueClass(JDefinedClass jVisitorModel, GenerateValueClassForVisitor annotation) throws SourceException, CodeGenerationException, ErrorTypeFound {
        ValueVisitorTypeParameters typeParameters = createValueVisitorTypeParameters(jVisitorModel, annotation);
        String valueClassName = valueClassName(jVisitorModel, annotation);
        Map<String, JMethod> methods = createMethodMap(jVisitorModel, typeParameters);
        ValueVisitorInterfaceModel visitorModel = new ValueVisitorInterfaceModel(jVisitorModel, typeParameters, methods);
        Serialization serialization = serialization(annotation);
        ValueClassModelFactory factory = new ValueClassModelFactory(jVisitorModel._package(), valueClassName, serialization, annotation);
        ValueClassModel valueClassModel = factory.createValueClass(visitorModel);
        return valueClassModel.getJDefinedClass();
    }

    private static ValueVisitorTypeParameters createValueVisitorTypeParameters(JDefinedClass jVisitorModel,
                                                                               GenerateValueClassForVisitor annotation)
            throws SourceException {
        JTypeVar resultType = null;
        @Nullable JTypeVar exceptionType = null;
        @Nullable JTypeVar selfType = null;
        List<JTypeVar> valueClassTypeParameters = new ArrayList<JTypeVar>();
        for (JTypeVar typeVariable: jVisitorModel.typeParams()) {
            if (typeVariable.name().equals(annotation.resultVariableName()))
                resultType = typeVariable;
            else if (typeVariable.name().equals(annotation.selfReferenceVariableName()))
                selfType = typeVariable;
            else if (typeVariable.name().equals(annotation.exceptionVariableName()))
                exceptionType = typeVariable;
            else
                valueClassTypeParameters.add(typeVariable);
        }
        if (resultType == null) {
            throw new SourceException("Result type-variable is not found for " + jVisitorModel + " visitor, expecting: " + annotation.resultVariableName());
        }
        if (exceptionType == null && !annotation.exceptionVariableName().equals(":none")) {
            throw new SourceException("Exception type-variable is not found for " + jVisitorModel + " visitor, expecting: " + annotation.exceptionVariableName());
        }
        if (selfType == null && !annotation.selfReferenceVariableName().equals(":none")) {
            throw new SourceException("Self reference type-variable is not found for " + jVisitorModel + " visitor, expecting: " + annotation.selfReferenceVariableName());
        }
        return new ValueVisitorTypeParameters(resultType, exceptionType, selfType, valueClassTypeParameters);
    }

    private static String valueClassName(JDefinedClass jVisitorModel, GenerateValueClassForVisitor annotation) {
        if (!annotation.valueClassName().equals(":auto")) {
            return annotation.valueClassName();
        } else {
            String visitorName = jVisitorModel.name();
            if (visitorName.endsWith(VISITOR_SUFFIX))
                return visitorName.substring(0, visitorName.length() - VISITOR_SUFFIX.length());
            else
                return visitorName + VALUE_SUFFIX;
        }
    }

    private static Serialization serialization(GenerateValueClassForVisitor annotation) {
        if (!annotation.valueClassIsSerializable())
            return Serialization.notSerializable();
        else
            return Serialization.serializable(annotation.valueClassSerialVersionUID());
    }

    private static Map<String, JMethod> createMethodMap(JDefinedClass jVisitorModel,
                                                        ValueVisitorTypeParameters typeParameters) throws
                                                                                                          SourceException {
        Map<String, JMethod> methods = new TreeMap<String, JMethod>();
        for (JMethod method: jVisitorModel.methods()) {
            if (!typeParameters.isResult(method.type())) {
                throw new SourceException("Visitor methods are only allowed to return type"
                                          + " declared as a result type of visitor: " + method.name()
                                          + ": expecting " + typeParameters.getResultTypeParameter()
                                          + ", found: " + method.type());
            }

            Collection<AbstractJClass> exceptions = method.getThrows();
            if (exceptions.size() > 1)
                throw new SourceException("Visitor methods are allowed to throw no exceptions or throw single exception, declared as type-variable: " + method.name());
            else if (exceptions.size() == 1) {
                AbstractJClass exception = exceptions.iterator().next();
                if (!typeParameters.isException(exception))
                    throw new SourceException("Visitor methods throws exception, not declared as type-variable: " + method.name() + ": " + exception);
            }

            JMethod exitingValue = methods.put(method.name(), method);
            if (exitingValue != null) {
                throw new SourceException("Method overloading is not supported for visitor interfaces: two methods with the same name: " + method.name());
            }
        }
        return methods;
    }

    private final Serialization serialization;
    private final GenerateValueClassForVisitor annotation;
    private final JPackage jpackage;
    private final String className;

    ValueClassModelFactory(JPackage jpackage, String className, Serialization serialization, GenerateValueClassForVisitor annotation) {
        this.serialization = serialization;
        this.annotation = annotation;
        this.jpackage = jpackage;
        this.className = className;
    }

    private JDefinedClass createAcceptingInterface(JDefinedClass valueClass,
                                                   ValueVisitorInterfaceModel visitorInterface,
                                                   Types types) throws JClassAlreadyExistsException {

        JDefinedClass acceptingInterface = valueClass._class(JMod.PUBLIC, valueClass.name() + "Acceptor", EClassType.INTERFACE);

        // Hack to overcome bug in codeModel. We want private interface!!! Not public.
        acceptingInterface.mods().setPrivate();

        for (JTypeVar visitorTypeParameter: visitorInterface.getValueTypeParameters()) {
            Types.generifyWithBoundsFrom(acceptingInterface, visitorTypeParameter.name(), visitorTypeParameter);
        }

        JMethod acceptMethod = acceptingInterface.method(JMod.PUBLIC, types._void(), "accept");

        JTypeVar visitorResultType = visitorInterface.getResultTypeParameter();
        JTypeVar resultType = Types.generifyWithBoundsFrom(acceptMethod, visitorResultType.name(), visitorResultType);
        acceptMethod.type(resultType);

        JTypeVar visitorExceptionType = visitorInterface.getExceptionTypeParameter();
        JTypeVar exceptionType = null;
        if (visitorExceptionType != null) {
            exceptionType = Types.generifyWithBoundsFrom(acceptMethod, visitorExceptionType.name(), visitorExceptionType);
            acceptMethod._throws(exceptionType);
        }

        AbstractJClass usedValueClassType = valueClass.narrow(valueClass.typeParams());
        AbstractJClass usedVisitorType = visitorInterface.narrowed(usedValueClassType, resultType, exceptionType);
        acceptMethod.param(usedVisitorType, "visitor");

        return acceptingInterface;
    }

    ValueClassModel createValueClass(ValueVisitorInterfaceModel visitorInterface) throws SourceException, CodeGenerationException {
        try {
            Types types = Types.createInstance(jpackage.owner());
            if (annotation.valueClassIsSerializable()) {
                for (JMethod interfaceMethod: visitorInterface.methods()) {
                    for (JVar param: interfaceMethod.params()) {
                        AbstractJType type = param.type();
                        if (!visitorInterface.isSelf(type) && !types.isSerializable(type))
                            throw new SourceException("Value class can't be serializable: " + param.name() + " parameter in " + interfaceMethod.name() + " method is not serializable");
                    }
                }
            }

            if (annotation.valueClassIsComparable()) {
                for (JMethod interfaceMethod: visitorInterface.methods()) {
                    for (JVar param: interfaceMethod.params()) {
                        AbstractJType type = param.type();
                        if (!visitorInterface.isSelf(type) && !types.isComparable(type))
                            throw new SourceException("Value class can't be comparable: " + param.name() + " parameter in " + interfaceMethod.name() + " method is not comparable");
                    }
                }
            }

            int mods = annotation.valueClassIsPublic() ? JMod.PUBLIC: JMod.NONE;
            JDefinedClass valueClass = jpackage._class(mods, className, EClassType.CLASS);
            JAnnotationUse generatedAnnotation = valueClass.annotate(Generated.class);
            generatedAnnotation.param("value", GenerateValueClassForVisitorProcessor.class.getName());
            valueClass.annotate(ParametersAreNonnullByDefault.class);
            for (JTypeVar visitorTypeParameter: visitorInterface.getValueTypeParameters()) {
                Types.generifyWithBoundsFrom(valueClass, visitorTypeParameter.name(), visitorTypeParameter);
            }
            if (annotation.valueClassIsSerializable()) {
                valueClass._implements(types._Serializable());
                valueClass.field(JMod.PRIVATE | JMod.FINAL | JMod.STATIC, types._long(), "serialVersionUID", JExpr.lit(annotation.valueClassSerialVersionUID()));
            }

            if (annotation.valueClassIsComparable()) {
                valueClass._implements(types._Comparable().narrow(valueClass.narrow(valueClass.typeParams())));
            }

            JDefinedClass acceptingInterface = createAcceptingInterface(valueClass, visitorInterface, types);
            if (annotation.valueClassIsSerializable()) {
                acceptingInterface._extends(types._Serializable());
            }

            ValueClassModel result = new ValueClassModel(valueClass, acceptingInterface, visitorInterface, types);
            Map<String, JMethod> constructorMethods = result.buildConstructorMethods(serialization);
            JFieldVar acceptorField = result.buildAcceptorField();
            result.buildPrivateConstructor(acceptorField);
            result.buildProtectedConstructor(acceptorField, serialization);
            result.buildAcceptMethod(acceptorField);
            result.buildGetters();
            result.buildUpdaters();
            result.buildPredicates();
            if (annotation.valueClassIsComparable()) {
                result.buildCompareTo();
            }
            result.buildEqualsMethod();
            result.buildHashCodeMethod(annotation.valueClassHashCodeBase());
            result.buildToStringMethod();
            result.buildFactory(constructorMethods);

            return result;
        } catch (JClassAlreadyExistsException ex) {
            throw new CodeGenerationException(ex);
        }
    }


}