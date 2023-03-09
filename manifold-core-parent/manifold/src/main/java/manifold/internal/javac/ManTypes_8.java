/*
 * Copyright (c) 2019 - Manifold Systems LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package manifold.internal.javac;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.DeferredAttr;
import com.sun.tools.javac.comp.Flow;
import com.sun.tools.javac.comp.Infer;
import com.sun.tools.javac.comp.LambdaToMethod;
import com.sun.tools.javac.comp.Lower;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.comp.TransTypes;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import java.util.ArrayList;
import java.util.stream.Collectors;

import manifold.rt.api.anno.any;
import manifold.util.JreUtil;
import manifold.util.ReflectUtil;

public class ManTypes_8 extends Types {
    private static final String TYPES_FIELD = "types";
    private static final String SELF_TYPE_NAME = "manifold.ext.rt.api.Self";

    private final Symtab _syms;
    private final Attr _attr;
    private final ManTransTypes _transTypes;

    private int _overrideCount;

    public static Types instance(Context ctx) {
        Types types = ctx.get(typesKey);
        if (!(types instanceof ManTypes_8)) {
            ctx.put(typesKey, (Types) null);
            types = new ManTypes_8(ctx);
        }

        return types;
    }

    private ManTypes_8(Context ctx) {
        super(ctx);

        _attr = Attr.instance(ctx);
        _syms = Symtab.instance(ctx);
        _transTypes = (ManTransTypes) TransTypes.instance(ctx);
        if (JreUtil.isJava8()) {
            reassignEarlyHolders8(ctx);
        } else {
            reassignEarlyHolders(ctx);
        }
    }

    private void reassignEarlyHolders8(Context context) {
        ReflectUtil.field(Annotate.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Attr.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Check.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(DeferredAttr.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Flow.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Gen.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Infer.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(JavaCompiler.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(JavacTrees.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(JavacTypes.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(JavacElements.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(LambdaToMethod.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Lower.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(ManResolve.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(MemberEnter.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(RichDiagnosticFormatter.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(TransTypes.instance(context), TYPES_FIELD).set(this);
    }

    private void reassignEarlyHolders(Context context) {
        ReflectUtil.field(
                ReflectUtil.method(ReflectUtil.type("com.sun.tools.javac.comp.Analyzer"), "instance", Context.class)
                        .invokeStatic(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Annotate.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Attr.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Check.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(DeferredAttr.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Flow.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Gen.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Infer.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(JavaCompiler.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(JavacElements.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(JavacProcessingEnvironment.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(JavacTrees.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(JavacTypes.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(LambdaToMethod.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(Lower.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(ManResolve.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(MemberEnter.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(
                ReflectUtil.method(ReflectUtil.type("com.sun.tools.javac.comp.Modules"), "instance", Context.class)
                        .invokeStatic(context), TYPES_FIELD).set(this);
        ReflectUtil.field(
                ReflectUtil.method(ReflectUtil.type("com.sun.tools.javac.comp.Operators"), "instance", Context.class)
                        .invokeStatic(context), TYPES_FIELD).set(this);
        //noinspection ConstantConditions
        ReflectUtil.field(
                ReflectUtil.method(ReflectUtil.type("com.sun.tools.javac.jvm.StringConcat"), "instance", Context.class)
                        .invokeStatic(context), TYPES_FIELD).set(this);
        ReflectUtil.field(RichDiagnosticFormatter.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(TransTypes.instance(context), TYPES_FIELD).set(this);
        ReflectUtil.field(
                ReflectUtil.method(ReflectUtil.type("com.sun.tools.javac.comp.TypeEnter"), "instance", Context.class)
                        .invokeStatic(context), TYPES_FIELD).set(this);
        ReflectUtil.field(TreeMaker.instance(context), TYPES_FIELD).set(this);
    }

    @Override
    public boolean isAssignable(Type from, Type to, Warner warn) {
        if (isAssignableToAnyAnnotation(from, to)) {
            return true;
        }
        return super.isAssignable(from, to, warn);
    }

    public boolean isAssignableToAnyAnnotation(Type from, Type to) {
        return isAnnotation(from) &&
                any.class.getTypeName().equals(to.tsym.getQualifiedName().toString());
    }

    private boolean isAnnotation(Type from) {
        return from.tsym.isInterface() && (from.tsym.flags_field & Flags.ANNOTATION) != 0;
    }

    @Override
    public Type memberType(Type qualifier, Symbol memberSym) {
        Type memberType = super.memberType(qualifier, memberSym);

        // note, static is ok for methods intended to be transformed e.g., for the ICompilerComponent stuff e.g., query api
        if (memberType.tsym.type instanceof Type.ErrorType) {
            return memberType;
        }

        if (_overrideCount > 0 || _transTypes.isTranslating()) {
            return memberType;
        }

        JCTree.JCMethodDecl methodDef = ((ManAttr) _attr).peekMethodDef();
        if (isSameMethodSym(memberSym, methodDef)) {
            return memberType;
        }

        java.util.List<TypeAnnotationPosition> selfPos = findSelfAnnotationLocation(memberSym);
        if (selfPos != null) {
            if (qualifier instanceof Type.ArrayType && isSelfComponentType(memberSym)) {
//        while( qualifier instanceof Type.ArrayType )
//        {
                qualifier = ((Type.ArrayType) qualifier).getComponentType();
//        }
            }
            // Replace self type with qualifier type
            memberType = replaceSelfTypesWithQualifier(qualifier, memberType, selfPos);
        }
        return memberType;
    }

    private boolean isSameMethodSym(Symbol memberSym, JCTree.JCMethodDecl methodDef) {
        return methodDef != null && methodDef.sym != null &&
                isSameType(erasure(methodDef.sym.type), erasure(memberSym.type));
    }

    private java.util.List<TypeAnnotationPosition> findSelfAnnotationLocation(Symbol sym) {
        if (sym == null) {
            return null;
        }

        SymbolMetadata metadata = sym.getMetadata();
        if (metadata == null || metadata.isTypesEmpty()) {
            return null;
        }

        List<Attribute.TypeCompound> typeAttributes = metadata.getTypeAttributes();
        if (typeAttributes.isEmpty()) {
            return null;
        }

        java.util.List<TypeAnnotationPosition> positions = typeAttributes.stream()
                .filter(attr -> attr.type.toString().equals(SELF_TYPE_NAME))
                .map(Attribute.TypeCompound::getPosition)
                .collect(Collectors.toList());
        return positions.isEmpty() ? null : positions;
    }

    private boolean isSelfComponentType(Symbol sym) {
        if (sym == null) {
            return false;
        }

        SymbolMetadata metadata = sym.getMetadata();
        if (metadata == null || metadata.isTypesEmpty()) {
            return false;
        }

        List<Attribute.TypeCompound> typeAttributes = metadata.getTypeAttributes();
        if (typeAttributes.isEmpty()) {
            return false;
        }

        return typeAttributes.stream()
                .anyMatch(attr ->
                        attr.type.toString().equals(SELF_TYPE_NAME) &&
                                !attr.values.isEmpty() &&
                                (boolean) attr.values.head.snd.getValue());
    }

    private Type replaceSelfTypesWithQualifier(Type receiverType, Type type, java.util.List<TypeAnnotationPosition> selfPosList) {
        if (JreUtil.isJava8()) {
            if (type.getClass().getTypeName().equals("com.sun.tools.javac.code.Type.AnnotatedType") ||
                    type.getClass().getTypeName().equals("com.sun.tools.javac.code.Type$AnnotatedType")) {
                Type unannotatedType = (Type) ReflectUtil.method(type, "unannotatedType").invoke();
                for (Attribute.TypeCompound anno : type.getAnnotationMirrors()) {
                    if (anno.type.toString().equals(SELF_TYPE_NAME)) {
                        Type newType;
                        if (unannotatedType instanceof Type.ArrayType) {
                            newType = makeArray(unannotatedType, receiverType);
                        } else {
                            newType = receiverType;
                        }
                        return newType;
                    }
                }
                return replaceSelfTypesWithQualifier(receiverType, unannotatedType, selfPosList);
            }
        }

        if (type instanceof Type.ArrayType) {
            if (hasSelfType(type) || selfPosList != null) {
                Type componentType = ((Type.ArrayType) type).getComponentType();
                if (componentType instanceof Type.ClassType) {
                    return new Type.ArrayType(receiverType, _syms.arrayClass);
                }
                return new Type.ArrayType(
                        replaceSelfTypesWithQualifier(receiverType, componentType, selfPosList), _syms.arrayClass);
            }
        }

        if (type instanceof Type.ClassType) {
            if (selfPosList.isEmpty()) {
                return type;
            }

            TypeAnnotationPosition selfPos = selfPosList.remove(0);
            if (selfPos.location == null || selfPos.location.isEmpty()) {
                return receiverType;
            }

            List<TypeAnnotationPosition.TypePathEntry> selfLocation = selfPos.location;
            TypeAnnotationPosition.TypePathEntry loc = selfLocation.get(0);
            List<TypeAnnotationPosition.TypePathEntry> selfLocationCopy = List.from(selfLocation.subList(1, selfLocation.size()));

            if (loc == TypeAnnotationPosition.TypePathEntry.INNER_TYPE) {
                return receiverType;
            }

            boolean replaced = false;
            ArrayList<Type> newParams = new ArrayList<>();
            List<Type> typeArguments = type.getTypeArguments();
            for (int i = 0; i < typeArguments.size(); i++) {
                Type typeParam = typeArguments.get(i);
                if (i == loc.arg) {
                    if (selfLocationCopy.isEmpty()) {
                        receiverType = boxedTypeOrType(receiverType); // type params cannot be primitive
                        typeParam = receiverType;
                    } else {
                        TypeAnnotationPosition posCopy = SrcClassUtil.getTypeAnnotationPosition(selfLocationCopy);
                        posCopy.location = selfLocationCopy;
                        typeParam = replaceSelfTypesWithQualifier(receiverType, typeParam, singleMutable(posCopy));
                    }
                    replaced = true;
                }
                newParams.add(typeParam);
            }
            if (replaced) {
                return replaceSelfTypesWithQualifier(receiverType,
                        new Type.ClassType(type.getEnclosingType(), List.from(newParams), type.tsym),
                        selfPosList);
            }
        }

        if (type instanceof Type.MethodType || type instanceof Type.ForAll) {
            if (selfPosList.isEmpty()) {
                return type;
            }

            TypeAnnotationPosition selfPos = selfPosList.remove(0);
            if (selfPos.type == TargetType.METHOD_FORMAL_PARAMETER) {
                List<TypeAnnotationPosition.TypePathEntry> selfLocation = selfPos.location;
                List<TypeAnnotationPosition.TypePathEntry> selfLocationCopy = selfLocation == null || selfLocation.isEmpty() ? null : List.from(selfLocation.subList(0, selfLocation.size()));

                boolean replacedParams = false;
                ArrayList<Type> newParams = new ArrayList<>();
                List<Type> paramTypes = type.getParameterTypes();
                for (int i = 0; i < paramTypes.size(); i++) {
                    Type paramType = paramTypes.get(i);
                    if (i == selfPos.parameter_index) {
                        if (selfLocationCopy == null || selfLocationCopy.isEmpty()) {
                            paramType = receiverType;
                        } else {
                            TypeAnnotationPosition posCopy = SrcClassUtil.getTypeAnnotationPosition(selfLocationCopy);
                            posCopy.location = selfLocationCopy;
                            paramType = replaceSelfTypesWithQualifier(receiverType, paramType, singleMutable(posCopy));
                        }
                        replacedParams = true;
                    }
                    newParams.add(paramType);
                }
                if (replacedParams) {
                    if (type instanceof Type.ForAll) {
                        return replaceSelfTypesWithQualifier(receiverType,
                                new Type.ForAll(((Type.ForAll) type).tvars, new Type.MethodType(List.from(newParams), type.getReturnType(), type.getThrownTypes(), type.tsym)),
                                selfPosList);
                    }
                    return replaceSelfTypesWithQualifier(receiverType,
                            new Type.MethodType(List.from(newParams), type.getReturnType(), type.getThrownTypes(), type.tsym),
                            selfPosList);
                }
            } else if (selfPos.type == TargetType.METHOD_RETURN) {
                Type retType = type.getReturnType();
                Type newRetType = replaceSelfTypesWithQualifier(receiverType, retType, singleMutable(selfPos));
                if (newRetType != retType) {
                    if (type instanceof Type.ForAll) {
                        return replaceSelfTypesWithQualifier(receiverType,
                                new Type.ForAll(((Type.ForAll) type).tvars, new Type.MethodType(type.getParameterTypes(), newRetType, type.getThrownTypes(), type.tsym)),
                                selfPosList);
                    }
                    return replaceSelfTypesWithQualifier(receiverType,
                            new Type.MethodType(type.getParameterTypes(), newRetType, type.getThrownTypes(), type.tsym),
                            selfPosList);
                }
            }
        }

        if (type instanceof Type.WildcardType) {
            if (selfPosList.isEmpty()) {
                return type;
            }

            TypeAnnotationPosition selfPos = selfPosList.remove(0);

            List<TypeAnnotationPosition.TypePathEntry> selfLocationCopy = List.from(selfPos.location.subList(1, selfPos.location.size()));
            TypeAnnotationPosition posCopy = SrcClassUtil.getTypeAnnotationPosition(selfLocationCopy);
            posCopy.location = selfLocationCopy;
            Type newType = replaceSelfTypesWithQualifier(receiverType, ((Type.WildcardType) type).type, singleMutable(posCopy));
            return replaceSelfTypesWithQualifier(receiverType,
                    new Type.WildcardType(newType, ((Type.WildcardType) type).kind, _syms.boundClass),
                    selfPosList);
        }

        return type;
    }

    private java.util.List<TypeAnnotationPosition> singleMutable(TypeAnnotationPosition posCopy) {
        ArrayList<TypeAnnotationPosition> single = new ArrayList<>();
        single.add(posCopy);
        return single;
    }

    private boolean hasSelfType(Type type) {
        for (Attribute.TypeCompound anno : type.getAnnotationMirrors()) {
            if (anno.type.toString().equals(SELF_TYPE_NAME)) {
                return true;
            }
        }

        if (type instanceof Type.ArrayType) {
            return hasSelfType(((Type.ArrayType) type).getComponentType());
        }

        for (Type typeParam : type.getTypeArguments()) {
            if (hasSelfType(typeParam)) {
                return true;
            }
        }

        if (type instanceof Type.IntersectionClassType) {
            for (Type compType : ((Type.IntersectionClassType) type).getComponents()) {
                if (hasSelfType(compType)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Type makeArray(Type unannotatedType, Type receiverType) {
        if (unannotatedType instanceof Type.ArrayType) {
            return makeArray(((Type.ArrayType) unannotatedType).getComponentType(), new Type.ArrayType(receiverType, _syms.arrayClass));
        }
        return receiverType;
    }

    @Override
    public boolean returnTypeSubstitutable(Type r1,
                                           Type r2, Type r2res,
                                           Warner warner) {
        if (ManAttr.AUTO_TYPE.equals(r1.getReturnType().tsym.getQualifiedName().toString())) {
            return true;
        }
        if (ManAttr.AUTO_TYPE.equals(r2res.tsym.getQualifiedName().toString())) {
            return true;
        }

        return super.returnTypeSubstitutable(r1, r2, r2res, warner);
    }

    @Override
    public boolean resultSubtype(Type t, Type s, Warner warner) {
        if (ManAttr.AUTO_TYPE.equals(t.getReturnType().tsym.getQualifiedName().toString())) {
            return true;
        }
        return super.resultSubtype(t, s, warner);
    }

    public boolean isConvertible(Type t, Type s, Warner warn) {
        if (t != null && t.tsym != null && ManAttr.AUTO_TYPE.equals(t.tsym.getQualifiedName().toString())) {
            return true;
        }
        if (s != null && s.tsym != null && ManAttr.AUTO_TYPE.equals(s.tsym.getQualifiedName().toString())) {
            return true;
        }
        return super.isConvertible(t, s, warn);
    }

    /**
     * Override to keep track of when/if implementation() is in scope, if ManTypes#memberType() should not try to
     * substitute the qualifier type for @Self because the qualifier is not really a call site, rather it is the
     * declaring class of the method being checked for override etc.  Thus we need to let the normal signature flow
     * through.
     */
    @Override
    public Symbol.MethodSymbol implementation(Symbol.MethodSymbol ms, Symbol.TypeSymbol origin, boolean checkResult, Filter<Symbol> implFilter) {
        _overrideCount++;
        try {
            return super.implementation(ms, origin, checkResult, implFilter);
        } finally {
            _overrideCount--;
        }
    }
}
