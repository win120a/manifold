/*
 * Copyright (c) 2021 - Manifold Systems LLC
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

package manifold.ext.props;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import manifold.ext.props.rt.api.*;
import manifold.util.ReflectUtil;

import javax.tools.JavaFileObject;
import java.lang.annotation.Annotation;
import java.util.function.Function;

import static java.lang.reflect.Modifier.*;

public class Util {
    static boolean sameAccess(Symbol sym1, Symbol sym2) {
        return sameAccess((int) sym1.flags_field, (int) sym2.flags_field);
    }

    static boolean sameAccess(int flags1, int flags2) {
        return getAccess(flags1) == getAccess(flags2);
    }

    static int getAccess(Symbol classSym) {
        return getAccess((int) classSym.flags_field);
    }

    static int getAccess(int flags) {
        return flags & (PUBLIC | PROTECTED | PRIVATE);
    }

    static int getAccess(JCTree.JCClassDecl classDecl, int flags) {
        return isPublic(flags)
                ? PUBLIC
                : isProtected(flags)
                ? PROTECTED
                : isPrivate(flags)
                ? PRIVATE
                : isInterface(classDecl) ? PUBLIC : 0;
    }

    static boolean isInterface(JCTree.JCClassDecl classDecl) {
        return classDecl.getKind() == Tree.Kind.INTERFACE;
    }


    static int weakest(int acc1, int acc2) {
        return
                acc1 == PUBLIC
                        ? PUBLIC
                        : acc2 == PUBLIC
                        ? PUBLIC
                        : acc1 == PROTECTED
                        ? PROTECTED
                        : acc2 == PROTECTED
                        ? PROTECTED
                        : acc1 != PRIVATE
                        ? 0
                        : acc2 != PRIVATE
                        ? 0
                        : PRIVATE;
    }

    static boolean isAbstract(JCTree.JCClassDecl classDecl, JCTree.JCVariableDecl propField) {
        if (isInterface(classDecl) && !isStatic(propField) && propField.init == null) {
            // non-static, non-default method is abstract in interface
            return true;
        } else {
            // abstract class can have abstract methods
            return getAnnotation(propField, Abstract.class) != null;
        }
    }

    static PropOption getAccess(JCTree.JCClassDecl classDecl, List<JCTree.JCExpression> args) {
        if (isInterface(classDecl)) {
            // generated methods are always public in interfaces
            return PropOption.Public;
        }
        return hasOption(args, PropOption.Public)
                ? PropOption.Public
                : hasOption(args, PropOption.Protected)
                ? PropOption.Protected
                : hasOption(args, PropOption.Package)
                ? PropOption.Package
                : hasOption(args, PropOption.Private)
                ? PropOption.Private
                : null;
    }

    static boolean hasOption(List<JCTree.JCExpression> args, PropOption option) {
        if (args == null) {
            return false;
        }
        return args.stream().anyMatch(e -> isOption(option, e));
    }

    static boolean isOption(PropOption option, JCTree.JCExpression e) {
        if (e instanceof JCTree.JCLiteral) {
            return ((JCTree.JCLiteral) e).getValue() == option;
        }
        // whatever, it works
        return e.toString().contains(option.name());
    }

    static JCTree.JCAnnotation getAnnotation(JCTree.JCVariableDecl field, Class<? extends Annotation> cls) {
        for (JCTree.JCAnnotation jcAnno : field.getModifiers().getAnnotations()) {
            if (cls.getSimpleName().equals(jcAnno.annotationType.toString())) {
                return jcAnno;
            } else if (cls.getTypeName().equals(jcAnno.annotationType.toString())) {
                return jcAnno;
            }
        }
        return null;
    }

    static boolean isPropertyField(Symbol sym) {
        return sym != null &&
                (getAnnotationMirror(sym, var.class) != null ||
                        getAnnotationMirror(sym, val.class) != null ||
                        getAnnotationMirror(sym, get.class) != null ||
                        getAnnotationMirror(sym, set.class) != null);
    }

    static boolean isReadableProperty(Symbol sym) {
        return sym != null &&
                (getAnnotationMirror(sym, var.class) != null ||
                        getAnnotationMirror(sym, val.class) != null ||
                        getAnnotationMirror(sym, get.class) != null);
    }

    static boolean isWritableProperty(Symbol sym) {
        return sym != null &&
                (getAnnotationMirror(sym, var.class) != null ||
                        getAnnotationMirror(sym, set.class) != null);
    }

    static Attribute.Compound getAnnotationMirror(Symbol sym, Class<? extends Annotation> annoClass) {
        for (Attribute.Compound anno : sym.getAnnotationMirrors()) {
            if (annoClass.getTypeName().equals(anno.type.tsym.getQualifiedName().toString())) {
                return anno;
            }
        }
        return null;
    }


    static boolean isStatic(JCTree.JCVariableDecl propField) {
        long flags = propField.getModifiers().flags;
        return (flags & STATIC) != 0;
    }

    static long getFlags(Attribute.Compound anno) {
        for (Symbol.MethodSymbol methSym : anno.getElementValues().keySet()) {
            if (methSym.getSimpleName().toString().equals("flags")) {
                return ((Number) anno.getElementValues().get(methSym).getValue()).longValue();
            }
        }
        throw new IllegalStateException();
    }

    static int getDeclaredAccess(Attribute.Compound anno) {
        for (Symbol.MethodSymbol methSym : anno.getElementValues().keySet()) {
            if (methSym.getSimpleName().toString().equals("declaredAccess")) {
                return ((Number) anno.getElementValues().get(methSym).getValue()).intValue();
            }
        }
        return -1;
    }

    public static JavaFileObject getFile(Tree node, Function<Tree, Tree> parentOf) {
        JCTree.JCClassDecl classDecl = getClassDecl(node, parentOf);
        if (classDecl == null) {
            ReflectUtil.LiveFieldRef symField = ReflectUtil.WithNull.field(node, "sym");
            Symbol sym = symField == null ? null : (Symbol) symField.get();
            while (sym != null) {
                Symbol owner = sym.owner;
                if (owner instanceof Symbol.ClassSymbol) {
                    return ((Symbol.ClassSymbol) owner).sourcefile;
                }
                sym = owner;
            }
        }
        return classDecl == null ? null : classDecl.sym.sourcefile;
    }

    public static JCTree.JCClassDecl getClassDecl(Tree node, Function<Tree, Tree> parentOf) {
        if (node == null || node instanceof JCTree.JCCompilationUnit) {
            return null;
        }

        if (node instanceof JCTree.JCClassDecl) {
            return (JCTree.JCClassDecl) node;
        }

        return getClassDecl(parentOf.apply(node), parentOf);
    }

}
