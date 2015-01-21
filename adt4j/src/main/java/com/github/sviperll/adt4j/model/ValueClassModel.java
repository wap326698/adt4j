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

import com.github.sviperll.adt4j.model.util.Types;
import com.github.sviperll.adt4j.model.util.ValueVisitorInterfaceModel;
import com.github.sviperll.adt4j.model.util.Serialization;
import com.github.sviperll.adt4j.model.util.VariableNameSource;
import com.github.sviperll.adt4j.model.util.SourceException;
import com.github.sviperll.adt4j.GeneratePredicate;
import com.github.sviperll.adt4j.Getter;
import com.github.sviperll.adt4j.Updater;
import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.AbstractJType;
import com.helger.jcodemodel.EClassType;
import com.helger.jcodemodel.JAnnotationArrayMember;
import com.helger.jcodemodel.JAnnotationUse;
import com.helger.jcodemodel.JBlock;
import com.helger.jcodemodel.JClassAlreadyExistsException;
import com.helger.jcodemodel.JConditional;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JExpr;
import com.helger.jcodemodel.JFieldVar;
import com.helger.jcodemodel.JInvocation;
import com.helger.jcodemodel.JMethod;
import com.helger.jcodemodel.JMod;
import com.helger.jcodemodel.JTypeVar;
import com.helger.jcodemodel.JVar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class ValueClassModel {
    private static String capitalize(String s) {
        if (s.length() >= 2
            && Character.isHighSurrogate(s.charAt(0))
            && Character.isLowSurrogate(s.charAt(1))) {
            return s.substring(0, 2).toUpperCase(Locale.US) + s.substring(2);
        } else {
            return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
        }
    }

    private static String decapitalize(String s) {
        if (s.length() >= 2
            && Character.isHighSurrogate(s.charAt(0))
            && Character.isLowSurrogate(s.charAt(1))) {
            return s.substring(0, 2).toLowerCase(Locale.US) + s.substring(2);
        } else {
            return s.substring(0, 1).toLowerCase(Locale.US) + s.substring(1);
        }
    }

    private static boolean isNullable(JVar param) throws SourceException {
        boolean hasNonnull = false;
        boolean hasNullable = false;
        for (JAnnotationUse annotationUse: param.annotations()) {
            if (annotationUse.getAnnotationClass().fullName().equals("javax.annotation.Nonnull")) {
                hasNonnull = true;
            }
            if (annotationUse.getAnnotationClass().fullName().equals("javax.annotation.Nullable")) {
                hasNullable = true;
            }
        }
        if (hasNonnull && hasNullable)
            throw new SourceException("Parameter " + param.name() + " is declared as both @Nullable and @Nonnull");
        if (!param.type().isReference() && hasNullable)
            throw new SourceException("Parameter " + param.name() + " is non-reference, but declared as @Nullable");
        return hasNullable;
    }

    private final JDefinedClass valueClass;
    private final JDefinedClass acceptingInterface;
    private final ValueVisitorInterfaceModel visitorInterface;
    private final Types types;

    ValueClassModel(JDefinedClass valueClass, JDefinedClass acceptingInterface, ValueVisitorInterfaceModel visitorInterface, Types modelTypes) {
        this.valueClass = valueClass;
        this.acceptingInterface = acceptingInterface;
        this.visitorInterface = visitorInterface;
        this.types = modelTypes;
    }

    JDefinedClass getJDefinedClass() {
        return valueClass;
    }

    MethodBuilder createMethodBuilder(Serialization serialization) throws JClassAlreadyExistsException {
        JFieldVar acceptorField = buildAcceptorField();
        Map<String, JDefinedClass> caseClasses = buildCaseClasses(serialization);
        return new MethodBuilder(caseClasses, acceptorField);
    }

    private JFieldVar buildAcceptorField() {
        AbstractJType usedAcceptingInterfaceType = acceptingInterface.narrow(valueClass.typeParams());
        return valueClass.field(JMod.PRIVATE | JMod.FINAL, usedAcceptingInterfaceType, "acceptor");
    }

    JMethod buildFactory(Map<String, JMethod> constructorMethods) throws JClassAlreadyExistsException {
        JDefinedClass factory = buildFactoryClass(constructorMethods);

        JFieldVar factoryField = valueClass.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL, factory, "FACTORY");
        JAnnotationUse fieldAnnotationUse = factoryField.annotate(SuppressWarnings.class);
        JAnnotationArrayMember paramArray = fieldAnnotationUse.paramArray("value");
        paramArray.param("unchecked");
        paramArray.param("rawtypes");

        factoryField.init(JExpr._new(factory));
        JMethod factoryMethod = valueClass.method(JMod.PUBLIC | JMod.STATIC, types._void, "factory");
        factoryMethod.annotate(Nonnull.class);
        JAnnotationUse methodAnnotationUse = factoryMethod.annotate(SuppressWarnings.class);
        methodAnnotationUse.param("value", "unchecked");
        for (JTypeVar visitorTypeParameter: visitorInterface.getValueTypeParameters()) {
            Types.generifyWithBoundsFrom(factoryMethod, visitorTypeParameter.name(), visitorTypeParameter);
        }
        AbstractJClass usedValueClassType = valueClass.narrow(factoryMethod.typeParams());
        factoryMethod.type(visitorInterface.narrowed(usedValueClassType, usedValueClassType, types._RuntimeException));
        factoryMethod.body()._return(factoryField);
        return factoryMethod;
    }

    private JDefinedClass buildFactoryClass(Map<String, JMethod> constructorMethods) throws JClassAlreadyExistsException {
        JDefinedClass factoryClass = valueClass._class(JMod.PRIVATE | JMod.STATIC, valueClass.name() + "Factory", EClassType.CLASS);
        for (JTypeVar visitorTypeParameter: visitorInterface.getValueTypeParameters()) {
            Types.generifyWithBoundsFrom(factoryClass, visitorTypeParameter.name(), visitorTypeParameter);
        }
        AbstractJClass usedValueClassType = valueClass.narrow(factoryClass.typeParams());
        factoryClass._implements(visitorInterface.narrowed(usedValueClassType, usedValueClassType, types._RuntimeException));
        for (JMethod interfaceMethod: visitorInterface.methods()) {
            JMethod factoryMethod = factoryClass.method(interfaceMethod.mods().getValue() & ~JMod.ABSTRACT, usedValueClassType, interfaceMethod.name());
            factoryMethod.annotate(Nonnull.class);
            factoryMethod.annotate(Override.class);

            JMethod constructorMethod = constructorMethods.get(interfaceMethod.name());
            JInvocation staticInvoke = valueClass.staticInvoke(constructorMethod);
            for (JTypeVar typeArgument: factoryClass.typeParams())
                staticInvoke.narrow(typeArgument);
            for (JVar param: interfaceMethod.params()) {
                AbstractJType argumentType = visitorInterface.substituteSpecialType(param.type(), usedValueClassType, usedValueClassType, types._RuntimeException);
                JVar argument = factoryMethod.param(param.mods().getValue(), argumentType, param.name());
                staticInvoke.arg(argument);
            }
            JVar param = interfaceMethod.listVarParam();
            if (param != null) {
                AbstractJType argumentType = visitorInterface.substituteSpecialType(param.type().elementType(), usedValueClassType, usedValueClassType, types._RuntimeException);
                JVar argument = factoryMethod.varParam(param.mods().getValue(), argumentType, param.name());
                staticInvoke.arg(argument);
            }
            factoryMethod.body()._return(staticInvoke);
        }
        return factoryClass;
    }

    private Map<String, JDefinedClass> buildCaseClasses(Serialization serialization) throws JClassAlreadyExistsException {
        Map<String, JDefinedClass> caseClasses = new TreeMap<String, JDefinedClass>();
        for (JMethod interfaceMethod: visitorInterface.methods()) {
            JDefinedClass caseClass = buildCaseClass(interfaceMethod, serialization);
            caseClasses.put(interfaceMethod.name(), caseClass);
        }
        return caseClasses;
    }

    private JDefinedClass buildCaseClass(JMethod interfaceMethod, Serialization serialization) throws JClassAlreadyExistsException {
        JDefinedClass caseClass = valueClass._class(JMod.PRIVATE | JMod.STATIC, capitalize(interfaceMethod.name()) + "Case" + acceptingInterface.name());
        for (JTypeVar visitorTypeParameter: acceptingInterface.typeParams()) {
            Types.generifyWithBoundsFrom(caseClass, visitorTypeParameter.name(), visitorTypeParameter);
        }

        AbstractJClass usedAcceptingInterfaceType = acceptingInterface.narrow(caseClass.typeParams());
        AbstractJClass usedValueClassType = valueClass.narrow(caseClass.typeParams());
        caseClass._implements(usedAcceptingInterfaceType);

        if (serialization.isSerializable()) {
            caseClass._implements(types._Serializable);
            caseClass.field(JMod.PRIVATE | JMod.FINAL | JMod.STATIC, types._long, "serialVersionUID", JExpr.lit(serialization.serialVersionUID()));
        }

        JMethod constructor = caseClass.constructor(JMod.NONE);
        for (JVar param: interfaceMethod.params()) {
            AbstractJType paramType = visitorInterface.substituteSpecialType(param.type(), usedValueClassType, usedValueClassType, types._RuntimeException);
            JFieldVar field = caseClass.field(JMod.PRIVATE | JMod.FINAL, paramType, param.name());
            JVar argument = constructor.param(paramType, param.name());
            constructor.body().assign(JExpr._this().ref(field), argument);
        }
        JVar param = interfaceMethod.listVarParam();
        if (param != null) {
            AbstractJType paramType = visitorInterface.substituteSpecialType(param.type().elementType(), usedValueClassType, usedValueClassType, types._RuntimeException);
            JFieldVar field = caseClass.field(JMod.PRIVATE | JMod.FINAL, paramType.array(), param.name());
            JVar argument = constructor.varParam(paramType, param.name());
            constructor.body().assign(JExpr._this().ref(field), argument);
        }

        JMethod acceptMethod = caseClass.method(JMod.PUBLIC, types._void, "accept");
        acceptMethod.annotate(Override.class);

        JTypeVar visitorResultType = visitorInterface.getResultTypeParameter();
        JTypeVar resultType = Types.generifyWithBoundsFrom(acceptMethod, visitorResultType.name(), visitorResultType);
        acceptMethod.type(resultType);

        JTypeVar visitorExceptionType = visitorInterface.getExceptionTypeParameter();
        JTypeVar exceptionType = null;
        if (visitorExceptionType != null) {
            exceptionType = Types.generifyWithBoundsFrom(acceptMethod, visitorExceptionType.name(), visitorExceptionType);
            acceptMethod._throws(exceptionType);
        }

        AbstractJClass usedVisitorType = visitorInterface.narrowed(usedValueClassType, resultType, exceptionType);
        acceptMethod.param(usedVisitorType, "visitor");
        JInvocation invocation = JExpr.invoke(JExpr.ref("visitor"), interfaceMethod.name());
        for (JVar param1: interfaceMethod.params()) {
            invocation.arg(JExpr._this().ref(param1.name()));
        }
        JVar param1 = interfaceMethod.listVarParam();
        if (param1 != null) {
            invocation.arg(JExpr._this().ref(param1.name()));
        }
        acceptMethod.body()._return(invocation);

        return caseClass;
    }

    Map<String, FieldConfiguration> getGettersConfigutation() throws SourceException {
        AbstractJClass usedValueClassType = valueClass.narrow(valueClass.typeParams());
        Map<String, FieldConfiguration> gettersMap = new TreeMap<String, FieldConfiguration>();
        for (JMethod interfaceMethod: visitorInterface.methods()) {
            for (JVar param: interfaceMethod.params()) {
                for (JAnnotationUse annotationUsage: param.annotations()) {
                    if (annotationUsage.getAnnotationClass().fullName().equals(Getter.class.getName())) {
                        AbstractJType paramType = visitorInterface.substituteSpecialType(param.type(), usedValueClassType, visitorInterface.getResultTypeParameter(), types._RuntimeException);
                        @SuppressWarnings("null") String getterName = (String)annotationUsage.getConstantParam("value").nativeValue();
                        boolean isNullable = isNullable(param);
                        FieldConfiguration configuration = gettersMap.get(getterName);
                        if (configuration == null) {
                            configuration = new FieldConfiguration(getterName, paramType);
                            gettersMap.put(getterName, configuration);
                        }
                        configuration.put(paramType, interfaceMethod, param.name(), new FieldFlags(isNullable, false));
                    }
                }
            }
            JVar param = interfaceMethod.listVarParam();
            if (param != null) {
                for (JAnnotationUse annotationUsage: param.annotations()) {
                    if (annotationUsage.getAnnotationClass().fullName().equals(Getter.class.getName())) {
                        AbstractJType paramType = visitorInterface.substituteSpecialType(param.type(), usedValueClassType, visitorInterface.getResultTypeParameter(), types._RuntimeException);
                        @SuppressWarnings("null") String getterName = (String)annotationUsage.getConstantParam("value").nativeValue();
                        boolean isNullable = isNullable(param);
                        FieldConfiguration configuration = gettersMap.get(getterName);
                        if (configuration == null) {
                            configuration = new FieldConfiguration(getterName, paramType);
                            gettersMap.put(getterName, configuration);
                        }
                        configuration.put(paramType, interfaceMethod, param.name(), new FieldFlags(isNullable, true));
                    }
                }
            }
        }
        return gettersMap;
    }

    Map<String, FieldConfiguration> getUpdatersConfiguration() throws SourceException {
        AbstractJClass usedValueClassType = valueClass.narrow(valueClass.typeParams());
        Map<String, FieldConfiguration> updatersMap = new TreeMap<String, FieldConfiguration>();
        for (JMethod interfaceMethod: visitorInterface.methods()) {
            for (JVar param: interfaceMethod.params()) {
                for (JAnnotationUse annotationUsage: param.annotations()) {
                    if (annotationUsage.getAnnotationClass().fullName().equals(Updater.class.getName())) {
                        AbstractJType paramType = visitorInterface.substituteSpecialType(param.type(), usedValueClassType, visitorInterface.getResultTypeParameter(), types._RuntimeException);
                        @SuppressWarnings("null") String updaterName = (String)annotationUsage.getConstantParam("value").nativeValue();
                        boolean isNullable = isNullable(param);
                        FieldConfiguration configuration = updatersMap.get(updaterName);
                        if (configuration == null) {
                            configuration = new FieldConfiguration(updaterName, paramType);
                            updatersMap.put(updaterName, configuration);
                        }
                        configuration.put(paramType, interfaceMethod, param.name(), new FieldFlags(isNullable, false));
                    }
                }
            }
            JVar param = interfaceMethod.listVarParam();
            if (param != null) {
                for (JAnnotationUse annotationUsage: param.annotations()) {
                    if (annotationUsage.getAnnotationClass().fullName().equals(Updater.class.getName())) {
                        AbstractJType paramType = visitorInterface.substituteSpecialType(param.type(), usedValueClassType, visitorInterface.getResultTypeParameter(), types._RuntimeException);
                        @SuppressWarnings("null") String updaterName = (String)annotationUsage.getConstantParam("value").nativeValue();
                        boolean isNullable = isNullable(param);
                        FieldConfiguration configuration = updatersMap.get(updaterName);
                        if (configuration == null) {
                            configuration = new FieldConfiguration(updaterName, paramType);
                            updatersMap.put(updaterName, configuration);
                        }
                        configuration.put(paramType, interfaceMethod, param.name(), new FieldFlags(isNullable, true));
                    }
                }
            }
        }
        return updatersMap;
    }

    Set<String> getPredicates() throws SourceException {
        Set<String> predicates = new TreeSet<String>();
        for (JMethod interfaceMethod: visitorInterface.methods()) {
            for (JAnnotationUse annotationUsage: interfaceMethod.annotations()) {
                if (annotationUsage.getAnnotationClass().fullName().equals(GeneratePredicate.class.getName())) {
                    @SuppressWarnings("null") String predicateName = (String)annotationUsage.getConstantParam("value").nativeValue();
                    predicates.add(predicateName);
                }
            }
        }
        return predicates;
    }

    class MethodBuilder {
        private final Map<String, JDefinedClass> caseClasses;
        private final JFieldVar acceptorField;

        private MethodBuilder(Map<String, JDefinedClass> caseClasses, JFieldVar acceptorField) {
            this.caseClasses = caseClasses;
            this.acceptorField = acceptorField;
        }

        void buildPrivateConstructor() {
            JMethod constructor = valueClass.constructor(JMod.PRIVATE);
            constructor.param(acceptorField.type(), acceptorField.name());
            constructor.body().assign(JExpr.refthis(acceptorField.name()), JExpr.ref(acceptorField.name()));
        }

        void buildProtectedConstructor(Serialization serialization) throws JClassAlreadyExistsException {
            JMethod constructor = valueClass.constructor(JMod.PROTECTED);
            JAnnotationUse annotation = constructor.annotate(SuppressWarnings.class);
            annotation.paramArray("value", "null");
            AbstractJClass usedValueClassType = valueClass.narrow(valueClass.typeParams());
            JVar param = constructor.param(usedValueClassType, "implementation");
            param.annotate(Nonnull.class);
            JConditional nullCheck = constructor.body()._if(JExpr.ref("implementation").eq(JExpr._null()));
            JInvocation nullPointerExceptionConstruction = JExpr._new(types._NullPointerException);
            nullPointerExceptionConstruction.arg(JExpr.lit("Argument shouldn't be null: 'implementation' argument in class constructor invocation: " + valueClass.fullName()));
            nullCheck._then()._throw(nullPointerExceptionConstruction);

            constructor.body().assign(JExpr.refthis(acceptorField), param.ref(acceptorField));
        }

        void buildAcceptMethod() {
            JMethod acceptMethod = valueClass.method(JMod.PUBLIC | JMod.FINAL, types._void, "accept");

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
            JInvocation invocation = acceptorField.invoke("accept");
            invocation.arg(JExpr.ref("visitor"));
            acceptMethod.body()._return(invocation);
        }

        Map<String, JMethod> buildConstructorMethods(Serialization serialization) throws JClassAlreadyExistsException, SourceException {
            Map<String, JMethod> constructorMethods = new TreeMap<String, JMethod>();
            for (JMethod interfaceMethod: visitorInterface.methods()) {
                JMethod constructorMethod = valueClass.method(interfaceMethod.mods().getValue() & ~JMod.ABSTRACT | JMod.STATIC, types._void, interfaceMethod.name());
                constructorMethod.annotate(Nonnull.class);
                for (JTypeVar visitorTypeParameter: visitorInterface.getValueTypeParameters()) {
                    Types.generifyWithBoundsFrom(constructorMethod, visitorTypeParameter.name(), visitorTypeParameter);
                }
                AbstractJClass usedValueClassType = valueClass.narrow(constructorMethod.typeParams());
                constructorMethod.type(usedValueClassType);
                for (JVar param: interfaceMethod.params()) {
                    AbstractJType paramType = visitorInterface.substituteSpecialType(param.type(), usedValueClassType, usedValueClassType, types._RuntimeException);
                    JVar constructorMethodParam = constructorMethod.param(param.mods().getValue(), paramType, param.name());
                    if (param.type().isReference())
                        constructorMethodParam.annotate(isNullable(param) ? Nullable.class : Nonnull.class);
                }
                JVar param = interfaceMethod.listVarParam();
                if (param != null) {
                    AbstractJType paramType = visitorInterface.substituteSpecialType(param.type().elementType(), usedValueClassType, usedValueClassType, types._RuntimeException);
                    JVar constructorMethodParam = constructorMethod.varParam(param.mods().getValue(), paramType, param.name());
                    if (param.type().isReference())
                        constructorMethodParam.annotate(isNullable(param) ? Nullable.class : Nonnull.class);
                }

                AbstractJClass usedCaseClassType = caseClasses.get(interfaceMethod.name()).narrow(constructorMethod.typeParams());
                if (!interfaceMethod.params().isEmpty() || interfaceMethod.hasVarArgs()) {
                    boolean hasNullChecks = false;
                    for (JVar param1: interfaceMethod.params()) {
                        if (param1.type().isReference() && !isNullable(param1)) {
                            JConditional nullCheck = constructorMethod.body()._if(JExpr.ref(param1.name()).eq(JExpr._null()));
                            JInvocation nullPointerExceptionConstruction = JExpr._new(types._NullPointerException);
                            nullPointerExceptionConstruction.arg(JExpr.lit("Argument shouldn't be null: '" + param1.name() + "' argument in static method invocation: '" + constructorMethod.name() + "' in class " + valueClass.fullName()));
                            nullCheck._then()._throw(nullPointerExceptionConstruction);
                            hasNullChecks = true;
                        }
                    }
                    JVar param1 = interfaceMethod.listVarParam();
                    if (param1 != null) {
                        if (param1.type().isReference() && !isNullable(param1)) {
                            JConditional nullCheck = constructorMethod.body()._if(JExpr.ref(param1.name()).eq(JExpr._null()));
                            JInvocation nullPointerExceptionConstruction = JExpr._new(types._NullPointerException);
                            nullPointerExceptionConstruction.arg(JExpr.lit("Argument shouldn't be null: '" + param1.name() + "' argument in static method invocation: '" + constructorMethod.name() + "' in class " + valueClass.fullName()));
                            nullCheck._then()._throw(nullPointerExceptionConstruction);
                            hasNullChecks = true;
                        }
                    }
                    if (hasNullChecks) {
                        JAnnotationUse annotation = constructorMethod.annotate(SuppressWarnings.class);
                        annotation.paramArray("value", "null");
                    }

                    JInvocation caseClassConstructorInvocation = JExpr._new(usedCaseClassType);
                    for (JVar param2: interfaceMethod.params()) {
                        caseClassConstructorInvocation.arg(JExpr.ref(param2.name()));
                    }
                    JVar param2 = interfaceMethod.listVarParam();
                    if (param2 != null) {
                        caseClassConstructorInvocation.arg(JExpr.ref(param2.name()));
                    }
                    JInvocation constructorInvocation = JExpr._new(usedValueClassType);
                    constructorInvocation.arg(caseClassConstructorInvocation);
                    constructorMethod.body()._return(constructorInvocation);
                } else {
                    JInvocation caseClassConstructorInvocation = JExpr._new(usedCaseClassType.erasure());
                    JInvocation initializer = JExpr._new(usedValueClassType.erasure());
                    initializer.arg(caseClassConstructorInvocation);
                    JFieldVar singletonInstanceField = valueClass.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL,
                                                                        usedValueClassType.erasure(),
                                                                        interfaceMethod.name().toUpperCase(Locale.US),
                                                                        initializer);
                    JAnnotationUse fieldAnnotationUse = singletonInstanceField.annotate(SuppressWarnings.class);
                    JAnnotationArrayMember paramArray = fieldAnnotationUse.paramArray("value");
                    paramArray.param("unchecked");
                    paramArray.param("rawtypes");

                    JAnnotationUse methodAnnotationUse = constructorMethod.annotate(SuppressWarnings.class);
                    methodAnnotationUse.param("value", "unchecked");
                    constructorMethod.body()._return(singletonInstanceField);
                }
                constructorMethods.put(interfaceMethod.name(), constructorMethod);
            }
            return constructorMethods;
        }

        void buildHashCodeMethod(int hashCodeBase) throws SourceException {
            String hashCodeMethodName = decapitalize(valueClass.name()) + "HashCode";
            JMethod hashCodeMethod = valueClass.method(JMod.PUBLIC | JMod.FINAL, types._int, "hashCode");
            hashCodeMethod.annotate(Override.class);
            JInvocation invocation = JExpr.refthis(acceptorField).invoke(hashCodeMethodName);
            hashCodeMethod.body()._return(invocation);

            acceptingInterface.method(JMod.PUBLIC, types._int, hashCodeMethodName);

            int tag = 1;
            for (JMethod interfaceMethod1: visitorInterface.methods()) {
                JDefinedClass caseClass = caseClasses.get(interfaceMethod1.name());
                JMethod caseHashCodeMethod = caseClass.method(JMod.PUBLIC | JMod.FINAL, types._int, hashCodeMethodName);
                caseHashCodeMethod.annotate(Override.class);

                VariableNameSource nameSource = new VariableNameSource();
                List<JFieldVar> arguments = new ArrayList<JFieldVar>();
                JFieldVar varArgument = null;
                for (JVar param: interfaceMethod1.params()) {
                    arguments.add(caseClass.fields().get(param.name()));
                }
                JVar param = interfaceMethod1.listVarParam();
                if (param != null) {
                    varArgument = caseClass.fields().get(param.name());
                }

                HashCodeMethod methodModel = new HashCodeMethod(types, hashCodeBase, caseHashCodeMethod.body(), nameSource);
                HashCodeMethod.Body body = methodModel.createBody(tag);
                for (int i = 0; i < arguments.size(); i++) {
                    param = interfaceMethod1.params().get(i);
                    JFieldVar argument = arguments.get(i);
                    if (isNullable(param))
                        body.appendNullableValue(argument.type(), JExpr.refthis(argument));
                    else
                        body.appendNotNullValue(argument.type(), JExpr.refthis(argument));
                }
                if (varArgument != null) {
                    if (isNullable(param))
                        body.appendNullableValue(varArgument.type(), JExpr.refthis(varArgument));
                    else
                        body.appendNotNullValue(varArgument.type(), JExpr.refthis(varArgument));
                }
                caseHashCodeMethod.body()._return(body.result());
                tag++;
            }
        }

        void buildToStringMethod() throws SourceException {
            JMethod toStringMethod = valueClass.method(JMod.PUBLIC | JMod.FINAL, types._String, "toString");
            toStringMethod.annotate(Override.class);
            toStringMethod.annotate(Nonnull.class);
            JInvocation invocation1 = JExpr.refthis(acceptorField).invoke("toString");
            toStringMethod.body()._return(invocation1);

            for (JMethod interfaceMethod1: visitorInterface.methods()) {
                JDefinedClass caseClass = caseClasses.get(interfaceMethod1.name());
                JMethod caseToStringMethod = caseClass.method(JMod.PUBLIC | JMod.FINAL, types._String, "toString");
                caseToStringMethod.annotate(Override.class);
                caseToStringMethod.annotate(Nonnull.class);

                VariableNameSource nameSource = new VariableNameSource();
                List<JFieldVar> arguments = new ArrayList<JFieldVar>();
                JFieldVar varArgument = null;
                for (JVar param: interfaceMethod1.params()) {
                    arguments.add(caseClass.fields().get(param.name()));
                }
                JVar param = interfaceMethod1.listVarParam();
                if (param != null) {
                    varArgument = caseClass.fields().get(param.name());
                }

                JVar result = caseToStringMethod.body().decl(types._StringBuilder, nameSource.get("result"), JExpr._new(types._StringBuilder));
                JInvocation invocation = caseToStringMethod.body().invoke(result, "append");
                invocation.arg(valueClass.name() + "." + capitalize(interfaceMethod1.name()) + "{");
                ToStringMethodBody body = new ToStringMethodBody(caseToStringMethod.body(), result);
                if (!arguments.isEmpty()) {
                    JFieldVar argument = arguments.get(0);
                    body.appendParam(argument.type(), interfaceMethod1.params().get(0).name(), JExpr.refthis(argument));
                    for (int i = 1; i < arguments.size(); i++) {
                        invocation = caseToStringMethod.body().invoke(result, "append");
                        invocation.arg(", ");
                        argument = arguments.get(i);
                        body.appendParam(argument.type(), interfaceMethod1.params().get(i).name(), JExpr.refthis(argument));
                    }
                }
                if (varArgument != null) {
                    if (!arguments.isEmpty()) {
                        invocation = caseToStringMethod.body().invoke(result, "append");
                        invocation.arg(", ");
                    }
                    body.appendParam(varArgument.type(), interfaceMethod1.listVarParam().name(), JExpr.refthis(varArgument));
                }
                invocation = caseToStringMethod.body().invoke(result, "append");
                invocation.arg("}");
                caseToStringMethod.body()._return(result.invoke("toString"));
            }
        }

        void generateGetter(FieldConfiguration configuration) {
            String getterName = configuration.name();
            JMethod getterMethod = acceptingInterface.method(JMod.PUBLIC, configuration.type(), getterName);
            if (configuration.type().isReference()) {
                if (configuration.flags().isNullable())
                    getterMethod.annotate(Nullable.class);
                else
                    getterMethod.annotate(Nonnull.class);
            }

            getterMethod = valueClass.method(JMod.PUBLIC | JMod.FINAL, configuration.type(), getterName);
            if (configuration.type().isReference()) {
                if (configuration.flags().isNullable())
                    getterMethod.annotate(Nullable.class);
                else
                    getterMethod.annotate(Nonnull.class);
            }
            JInvocation invocation1 = JExpr.refthis(acceptorField).invoke(getterName);
            getterMethod.body()._return(invocation1);

            for (JMethod interfaceMethod1: visitorInterface.methods()) {
                JDefinedClass caseClass = caseClasses.get(interfaceMethod1.name());
                JMethod geterMethod = caseClass.method(JMod.PUBLIC | JMod.FINAL, configuration.type(), getterName);
                geterMethod.annotate(Override.class);
                if (configuration.type().isReference()) {
                    if (configuration.flags().isNullable())
                        geterMethod.annotate(Nullable.class);
                    else
                        geterMethod.annotate(Nonnull.class);
                }
                boolean isGettable = false;
                for (JVar param: interfaceMethod1.params()) {
                    JFieldVar field = caseClass.fields().get(param.name());
                    if (configuration.isFieldValue(interfaceMethod1, param.name())) {
                        geterMethod.body()._return(field);
                        isGettable = true;
                    }
                }
                JVar param = interfaceMethod1.listVarParam();
                if (param != null) {
                    JFieldVar field = caseClass.fields().get(param.name());
                    if (configuration.isFieldValue(interfaceMethod1, param.name())) {
                        geterMethod.body()._return(field);
                        isGettable = true;
                    }
                }
                if (!isGettable) {
                    JInvocation exceptionInvocation = JExpr._new(types._IllegalStateException);
                    exceptionInvocation.arg(configuration.name() + " is not accessible in this case: " + interfaceMethod1.name());
                    geterMethod.body()._throw(exceptionInvocation);
                }
            }
        }

        void generateUpdater(FieldConfiguration configuration) throws SourceException {
            VariableNameSource nameSource = new VariableNameSource();
            String updaterName = configuration.name();
            AbstractJClass usedValueClassType = valueClass.narrow(valueClass.typeParams());

            JMethod updaterMethod = acceptingInterface.method(JMod.PUBLIC, usedValueClassType, updaterName);
            updaterMethod.annotate(Nonnull.class);
            JVar newValueParam;
            if (configuration.flags().isVarArg())
                newValueParam = updaterMethod.varParam(configuration.type().elementType(), nameSource.get("newValue"));
            else
                newValueParam = updaterMethod.param(configuration.type(), nameSource.get("newValue"));
            if (configuration.type().isReference()) {
                if (configuration.flags().isNullable()) {
                    newValueParam.annotate(Nullable.class);
                } else {
                    newValueParam.annotate(Nonnull.class);
                }
            }

            nameSource = new VariableNameSource();
            updaterMethod = valueClass.method(JMod.PUBLIC | JMod.FINAL, usedValueClassType, updaterName);
            updaterMethod.annotate(Nonnull.class);
            JVar newValue;
            if (configuration.flags().isVarArg())
                newValue = updaterMethod.varParam(configuration.type().elementType(), nameSource.get("newValue"));
            else
                newValue = updaterMethod.param(configuration.type(), nameSource.get("newValue"));
            if (configuration.type().isReference()) {
                if (configuration.flags().isNullable()) {
                    newValue.annotate(Nullable.class);
                } else {
                    newValue.annotate(Nonnull.class);
                }
            }
            JInvocation invocation1 = JExpr.refthis(acceptorField).invoke(updaterName);
            invocation1.arg(newValue);
            updaterMethod.body()._return(invocation1);

            for (JMethod interfaceMethod1: visitorInterface.methods()) {
                JDefinedClass caseClass = caseClasses.get(interfaceMethod1.name());
                nameSource = new VariableNameSource();
                updaterMethod = caseClass.method(JMod.PUBLIC | JMod.FINAL, usedValueClassType, updaterName);
                updaterMethod.annotate(Nonnull.class);
                updaterMethod.annotate(Override.class);
                if (configuration.flags().isVarArg())
                    newValue = updaterMethod.varParam(configuration.type().elementType(), nameSource.get("newValue"));
                else
                    newValue = updaterMethod.param(configuration.type(), nameSource.get("newValue"));
                if (configuration.type().isReference()) {
                    if (configuration.flags().isNullable()) {
                        newValue.annotate(Nullable.class);
                    } else {
                        newValue.annotate(Nonnull.class);
                    }
                }
                JInvocation invocation = valueClass.staticInvoke(interfaceMethod1.name());
                for (JTypeVar typeArgument: valueClass.typeParams())
                    invocation.narrow(typeArgument);
                for (JVar param: interfaceMethod1.params()) {
                    JFieldVar argument = caseClass.fields().get(param.name());
                    if (configuration.isFieldValue(interfaceMethod1, param.name())) {
                        invocation.arg(newValue);
                    } else {
                        invocation.arg(JExpr.refthis(argument));
                    }
                }
                JVar param = interfaceMethod1.listVarParam();
                if (param != null) {
                    JFieldVar argument = caseClass.fields().get(param.name());
                    if (configuration.isFieldValue(interfaceMethod1, param.name())) {
                        invocation.arg(newValue);
                    } else {
                        invocation.arg(JExpr.refthis(argument));
                    }
                }
                updaterMethod.body()._return(invocation);
            }
        }

        void generatePredicate(String name) {
            acceptingInterface.method(JMod.PUBLIC, types._boolean, name);

            JMethod predicateMethod = valueClass.method(JMod.PUBLIC | JMod.FINAL, types._boolean, name);
            predicateMethod.body()._return(JExpr.refthis(acceptorField).invoke(name));

            for (JMethod interfaceMethod1: visitorInterface.methods()) {
                JDefinedClass caseClass = caseClasses.get(interfaceMethod1.name());
                predicateMethod = caseClass.method(JMod.PUBLIC | JMod.FINAL, types._boolean, name);
                predicateMethod.annotate(Override.class);

                boolean result = false;
                for (JAnnotationUse annotationUsage: interfaceMethod1.annotations()) {
                    if (annotationUsage.getAnnotationClass().fullName().equals(GeneratePredicate.class.getName())) {
                        @SuppressWarnings("null") String predicateName = (String)annotationUsage.getConstantParam("value").nativeValue();
                        if (predicateName.equals(name)) {
                            result = true;
                            break;
                        }
                    }
                }
                predicateMethod.body()._return(JExpr.lit(result));
            }

        }

        void buildEqualsMethod() throws SourceException, JClassAlreadyExistsException {
            AbstractJClass usedValueClassType = valueClass.narrow(valueClass.typeParams());
            String equalsMethodName = decapitalize(valueClass.name()) + "Equals";
            JMethod equalsMethod = acceptingInterface.method(JMod.PUBLIC, types._boolean, equalsMethodName);
            VariableNameSource nameSource = new VariableNameSource();
            equalsMethod.param(usedValueClassType, nameSource.get("that"));

            equalsMethod = valueClass.method(JMod.PUBLIC | JMod.FINAL, types._boolean, "equals");
            nameSource = new VariableNameSource();
            equalsMethod.annotate(Override.class);
            JAnnotationUse annotationUse = equalsMethod.annotate(SuppressWarnings.class);
            annotationUse.param("value", "unchecked");
            JVar thatObject = equalsMethod.param(types._Object, nameSource.get("thatObject"));
            JConditional _if = equalsMethod.body()._if(JExpr._this().eq(thatObject));
            _if._then()._return(JExpr.TRUE);
            JConditional elseif = _if._elseif(thatObject._instanceof(valueClass).not());
            elseif._then()._return(JExpr.FALSE);
            JBlock _else = elseif._else();
            JVar that = _else.decl(usedValueClassType, nameSource.get("that"), JExpr.cast(usedValueClassType, thatObject));
            JInvocation invocation1 = JExpr.refthis(acceptorField).invoke(equalsMethodName);
            invocation1.arg(that);
            _else._return(invocation1);

            AbstractJClass visitorType = visitorInterface.narrowed(usedValueClassType, types._Boolean, types._RuntimeException);
            for (JMethod interfaceMethod1: visitorInterface.methods()) {
                JDefinedClass caseClass = caseClasses.get(interfaceMethod1.name());
                equalsMethod = caseClass.method(JMod.PUBLIC | JMod.FINAL, types._boolean, equalsMethodName);
                equalsMethod.annotate(Override.class);
                nameSource = new VariableNameSource();
                equalsMethod.param(usedValueClassType, nameSource.get("that"));

                List<JFieldVar> arguments1 = new ArrayList<JFieldVar>();
                JFieldVar varArgument1 = null;
                for (JVar param1: interfaceMethod1.params()) {
                    arguments1.add(caseClass.fields().get(param1.name()));
                }
                JVar param1 = interfaceMethod1.listVarParam();
                if (param1 != null) {
                    varArgument1 = caseClass.fields().get(param1.name());
                }

                JDefinedClass equalsVisitorClass2 = caseClass._class(JMod.PRIVATE, caseClass.name() + "EqualsVisitor");
                JFieldVar equalsVisitorField = caseClass.field(JMod.PRIVATE | JMod.FINAL, visitorType, "equalsVisitor", JExpr._new(equalsVisitorClass2));
                equalsVisitorClass2._implements(visitorType);
                JInvocation invocation2 = that.invoke("accept");
                invocation2.arg(JExpr.refthis(equalsVisitorField));
                equalsMethod.body()._return(invocation2);
                for (JMethod interfaceMethod2: visitorInterface.methods()) {
                    JMethod visitorMethod2 = equalsVisitorClass2.method(interfaceMethod1.mods().getValue() & ~JMod.ABSTRACT, types._Boolean, interfaceMethod2.name());
                    visitorMethod2.annotate(Nonnull.class);
                    visitorMethod2.annotate(Override.class);

                    VariableNameSource nameSource2 = nameSource.forBlock();
                    List<JVar> arguments2 = new ArrayList<JVar>();
                    JVar varArgument2 = null;
                    for (JVar param2: interfaceMethod2.params()) {
                        AbstractJType argumentType = visitorInterface.substituteSpecialType(param2.type(), usedValueClassType, types._Boolean, types._RuntimeException);
                        JVar argument2 = visitorMethod2.param(param2.mods().getValue(), argumentType, nameSource2.get(param2.name()));
                        arguments2.add(argument2);
                    }
                    JVar param2 = interfaceMethod2.listVarParam();
                    if (param2 != null) {
                        AbstractJType argumentType = visitorInterface.substituteSpecialType(param2.type().elementType(), usedValueClassType, types._Boolean, types._RuntimeException);
                        JVar argument2 = visitorMethod2.varParam(param2.mods().getValue(), argumentType, nameSource2.get(param2.name()));
                        varArgument2 = argument2;
                    }
                    if (!interfaceMethod1.name().equals(interfaceMethod2.name()))
                        visitorMethod2.body()._return(JExpr.FALSE);
                    else {
                        EqualsMethod body = new EqualsMethod(types, visitorMethod2.body(), nameSource2);
                        for (int i = 0; i < arguments1.size(); i++) {
                            JFieldVar field1 = arguments1.get(i);
                            JVar field2 = arguments2.get(i);
                            JVar param = interfaceMethod1.params().get(i);
                            if (isNullable(param))
                                body.appendNullableValue(field1.type(), caseClass.staticRef("this").ref(field1), field2);
                            else
                                body.appendNotNullValue(field1.type(), caseClass.staticRef("this").ref(field1), field2);
                        }
                        if (varArgument1 != null) {
                            JVar param = interfaceMethod1.listVarParam();
                            if (isNullable(param))
                                body.appendNullableValue(varArgument1.type(), caseClass.staticRef("this").ref(varArgument1), varArgument2);
                            else
                                body.appendNotNullValue(varArgument1.type(), caseClass.staticRef("this").ref(varArgument1), varArgument2);
                        }
                        visitorMethod2.body()._return(JExpr.TRUE);
                    }
                }
            }
        }

        void buildCompareTo() throws SourceException, JClassAlreadyExistsException {
            AbstractJClass usedValueClassType = valueClass.narrow(valueClass.typeParams());
            JMethod compareToMethod = acceptingInterface.method(JMod.PUBLIC, types._int, "compareTo");
            VariableNameSource nameSource = new VariableNameSource();
            compareToMethod.param(usedValueClassType, nameSource.get("that"));

            compareToMethod = valueClass.method(JMod.PUBLIC | JMod.FINAL, types._int, "compareTo");
            compareToMethod.annotate(Override.class);
            nameSource = new VariableNameSource();
            JVar that = compareToMethod.param(usedValueClassType, nameSource.get("that"));
            JInvocation invocation1 = JExpr.refthis(acceptorField).invoke("compareTo");
            invocation1.arg(that);
            compareToMethod.body()._return(invocation1);

            AbstractJClass visitorType = visitorInterface.narrowed(usedValueClassType, types._Integer, types._RuntimeException);

            JMethod[] methods = visitorInterface.methods().toArray(new JMethod[visitorInterface.methods().size()]);
            for (int interfaceMethodIndex1 = 0; interfaceMethodIndex1 < methods.length; interfaceMethodIndex1++) {
                JMethod interfaceMethod1 = methods[interfaceMethodIndex1];
                JDefinedClass caseClass = caseClasses.get(interfaceMethod1.name());
                compareToMethod = caseClass.method(JMod.PUBLIC | JMod.FINAL, types._int, "compareTo");
                compareToMethod.annotate(Override.class);
                nameSource = new VariableNameSource();
                that = compareToMethod.param(usedValueClassType, nameSource.get("that"));

                List<JFieldVar> arguments1 = new ArrayList<JFieldVar>();
                JFieldVar varArgument1 = null;
                for (JVar param1: interfaceMethod1.params()) {
                    arguments1.add(caseClass.fields().get(param1.name()));
                }
                JVar param1 = interfaceMethod1.listVarParam();
                if (param1 != null) {
                    varArgument1 = caseClass.fields().get(param1.name());
                }

                JDefinedClass compareVisitorClass = caseClass._class(JMod.PRIVATE, caseClass.name() + "CompareVisitor");
                compareVisitorClass._implements(visitorType);
                JFieldVar field = caseClass.field(JMod.PRIVATE | JMod.FINAL, visitorType, "compareToVisitor", JExpr._new(compareVisitorClass));
                JInvocation invocation2 = that.invoke("accept");
                invocation2.arg(JExpr.refthis(field));
                compareToMethod.body()._return(invocation2);
                for (int interfaceMethodIndex2 = 0; interfaceMethodIndex2 < methods.length; interfaceMethodIndex2++) {
                    JMethod interfaceMethod2 = methods[interfaceMethodIndex2];
                    JMethod visitorMethod2 = compareVisitorClass.method(interfaceMethod2.mods().getValue() & ~JMod.ABSTRACT, types._Integer, interfaceMethod2.name());
                    visitorMethod2.annotate(Override.class);
                    visitorMethod2.annotate(Nonnull.class);

                    VariableNameSource nameSource2 = nameSource.forBlock();
                    List<JVar> arguments2 = new ArrayList<JVar>();
                    JVar varArgument2 = null;
                    for (JVar param2: interfaceMethod2.params()) {
                        AbstractJType argumentType = visitorInterface.substituteSpecialType(param2.type(), usedValueClassType, types._Integer, types._RuntimeException);
                        JVar argument2 = visitorMethod2.param(param2.mods().getValue(), argumentType, nameSource2.get(param2.name()));
                        arguments2.add(argument2);
                    }
                    JVar param2 = interfaceMethod2.listVarParam();
                    if (param2 != null) {
                        AbstractJType argumentType = visitorInterface.substituteSpecialType(param2.type().elementType(), usedValueClassType, types._Integer, types._RuntimeException);
                        JVar argument2 = visitorMethod2.varParam(param2.mods().getValue(), argumentType, nameSource2.get(param2.name()));
                        varArgument2 = argument2;
                    }

                    if (!interfaceMethod1.name().equals(interfaceMethod2.name())) {
                        int result = (interfaceMethodIndex1 < interfaceMethodIndex2 ? -1 : (interfaceMethodIndex1 == interfaceMethodIndex2 ? 0 : 1));
                        visitorMethod2.body()._return(JExpr.lit(result));
                    } else {
                        if (!interfaceMethod1.params().isEmpty() || interfaceMethod1.hasVarArgs()) {
                            CompareToMethod compareToMethodModel = new CompareToMethod(types, visitorMethod2.body(), nameSource2);
                            CompareToMethod.Body body = compareToMethodModel.createBody();
                            for (int i = 0; i < interfaceMethod1.params().size(); i++) {
                                JVar argument1 = arguments1.get(i);
                                JVar argument2 = arguments2.get(i);
                                JVar param = interfaceMethod1.params().get(i);
                                if (isNullable(param))
                                    body.appendNullableValue(argument1.type(), caseClass.staticRef("this").ref(argument1), argument2);
                                else
                                    body.appendNotNullValue(argument1.type(), caseClass.staticRef("this").ref(argument1), argument2);
                            }
                            if (varArgument1 != null) {
                                JVar param = interfaceMethod1.listVarParam();
                                if (isNullable(param))
                                    body.appendNullableValue(varArgument1.type(), caseClass.staticRef("this").ref(varArgument1), varArgument2);
                                else
                                    body.appendNotNullValue(varArgument1.type(), caseClass.staticRef("this").ref(varArgument1), varArgument2);
                            }
                        }
                        visitorMethod2.body()._return(JExpr.lit(0));
                    }
                }
            }
        }

    }
}
