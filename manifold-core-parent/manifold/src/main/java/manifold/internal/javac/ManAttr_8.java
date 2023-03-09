/*
 * Copyright (c) 2018 - Manifold Systems LLC
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

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.OperatorSymbol;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import manifold.api.host.IModule;
import manifold.api.type.ITypeManifold;
import manifold.api.util.IssueMsg;
import manifold.rt.api.FragmentValue;
import manifold.api.type.ISelfCompiledFile;
import manifold.rt.api.util.ManClassUtil;
import manifold.rt.api.util.Stack;
import manifold.util.JreUtil;
import manifold.util.ReflectUtil;


import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static com.sun.tools.javac.code.Kinds.MTH;
import static com.sun.tools.javac.code.Kinds.VAL;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import static manifold.internal.javac.HostKind.DOUBLE_QUOTE_LITERAL;
import static manifold.internal.javac.HostKind.TEXT_BLOCK_LITERAL;

public class ManAttr_8 extends Attr implements ManAttr {
    private final ManLog_8 _manLog;
    private final Symtab _syms;
    private final Stack<JCTree.JCFieldAccess> _selects;
    private final Stack<JCTree.JCAnnotatedType> _annotatedTypes;
    private final Stack<JCTree.JCMethodDecl> _methodDefs;
    private final Set<JCTree.JCMethodInvocation> _visitedAutoMethodCalls = new HashSet<>();

    public static ManAttr_8 instance(Context ctx) {
        Attr attr = ctx.get(attrKey);
        if (!(attr instanceof ManAttr_8)) {
            ctx.put(attrKey, (Attr) null);
            attr = new ManAttr_8(ctx);
        }

        return (ManAttr_8) attr;
    }

    private ManAttr_8(Context ctx) {
        super(ctx);
        _selects = new Stack<>();
        _annotatedTypes = new Stack<>();
        _methodDefs = new Stack<>();
        _syms = Symtab.instance(ctx);

        // Override logger to handle final field assignment for @Jailbreak
        _manLog = (ManLog_8) ManLog_8.instance(ctx);
        ReflectUtil.field(this, "log").set(_manLog);
        ReflectUtil.field(this, "rs").set(ManResolve.instance(ctx));
        reassignAllEarlyHolders(ctx);
    }

    private void reassignAllEarlyHolders(Context ctx) {
        Object[] earlyAttrHolders = {
                Resolve.instance(ctx),
                DeferredAttr.instance(ctx),
                MemberEnter.instance(ctx),
                Lower.instance(ctx),
                TransTypes.instance(ctx),
                Annotate.instance(ctx),
                TypeAnnotations.instance(ctx),
                JavacTrees.instance(ctx),
                JavaCompiler.instance(ctx),
        };
        for (Object instance : earlyAttrHolders) {
            ReflectUtil.LiveFieldRef attr = ReflectUtil.WithNull.field(instance, "attr");
            if (attr != null) {
                attr.set(this);
            }
        }
    }

    /**
     * Handle properties in interfaces, which are non-static unless explicitly static.
     * This is necessary so that a non-static property can reference type variables in its type:  @var T element;
     */
    @Override
    public Type attribType(JCTree tree, Env<AttrContext> env) {
        ManAttr.super.handleNonStaticInterfaceProperty(env);
        return super.attribType(tree, env);
    }

    /**
     * Facilitates @Jailbreak. ManResolve#isAccessible() needs to know the JCFieldAccess in context.
     */
    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        // record JCFieldAccess trees as they are visited so we can access them elsewhere while in context
        _selects.push(tree);
        try {
            super.visitSelect(tree);
            patchAutoFieldType(tree);
        } finally {
            _selects.pop();
        }
    }

    /**
     * Handle the LetExpr, which is normally used after the attribution phase. We use it during parse to transform
     * AssignOp to normal Assign so that other manifold features are easier to implement (operator overloading,
     * properties, etc.)
     */
    @Override
    public void visitLetExpr(JCTree.LetExpr tree) {
        Env env = getEnv();
        Env localEnv = env.dup(tree, ReflectUtil.method(env.info, "dup").invoke());
        for (JCTree.JCVariableDecl def : tree.defs) {
            attribStat(def, localEnv);
            def.type = def.init.type;
            def.vartype.type = def.type;
            def.sym.type = def.type;
        }
        ReflectUtil.field(this, "result").set(attribExpr(tree.expr, localEnv));
        tree.type = tree.expr.type;
    }

    private boolean shouldCheckSuperType(Type type) {
        return _shouldCheckSuperType(type, true);
    }

    private boolean _shouldCheckSuperType(Type type, boolean checkSuper) {
        return
                type instanceof Type.ClassType &&
                        type != Type.noType &&
                        !(type instanceof Type.ErrorType) &&
                        !type.toString().equals(Object.class.getTypeName()) &&
                        (!checkSuper || _shouldCheckSuperType(((Symbol.ClassSymbol) type.tsym).getSuperclass(), false));
    }

    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        JCTree returnType = tree.getReturnType();
        if (isAutoTypeAssigned(returnType)) {
            // already attributed method returning 'auto'
            return;
        }

        _methodDefs.push(tree);
        try {
            super.visitMethodDef(tree);
            handleIntersectionAutoReturnType(tree);
        } finally {
            _methodDefs.pop();
        }
    }

    /**
     * Since intersection types are not supported in bytecode (method signatures) we make an attempt at
     * selecting the most relevant type in the intersection as the method's return type.
     * <p>
     * todo: consider an alternative that utilizes intersection types indirectly
     * For instance:
     * - add a runtime annotation to preserve the intersection return type in the method's bytecode
     * - in the ClassReader reassign the method's return type accordingly
     */
    private void handleIntersectionAutoReturnType(JCTree.JCMethodDecl tree) {
        if (tree.restype != null && tree.restype.type.isCompound()) {
            Type retType = tree.restype.type;
            retType = (Type) ReflectUtil.field(retType, "supertype_field").get();
            //noinspection unchecked
            List<Type> interfaces = (List<Type>) ReflectUtil.field(tree.restype.type, "interfaces_field").get();

            if (!interfaces.isEmpty() && types().isSameType(syms().objectType, retType)) {
                // Since an interface implicitly has Object's members, it is more relevant than Object.
                // Choose the one with the most members as a simple way to find the "best" one.

                int maxMemberCount = -1;
                for (Type t : interfaces) {
                    int[] memberCount = {0};
                    IDynamicJdk.instance().getMembers((Symbol.ClassSymbol) t.tsym).forEach(m -> memberCount[0]++);
                    if (maxMemberCount < memberCount[0]) {
                        maxMemberCount = memberCount[0];
                        retType = t;
                    }
                }
            }
            assignMethodReturnType(retType, tree);
        }
    }

    private boolean isAutoTypeAssigned(JCTree returnType) {
        return returnType != null &&
                (returnType.toString().equals(ManClassUtil.getShortClassName(AUTO_TYPE)) || returnType.toString().equals(AUTO_TYPE)) &&
                !AUTO_TYPE.equals(returnType.type.tsym.flatName().toString());
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        super.visitVarDef(tree);

        inferAutoLocalVar(tree);
    }

    @Override
    public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) {
        Env<AttrContext> env = getEnv();
        Env<AttrContext> loopEnv =
                env.dup(env.tree, (AttrContext) ReflectUtil.method(env.info, "dup", Scope.class).invoke(ReflectUtil.method(ReflectUtil.field(env.info, "scope").get(), "dup").invoke()));
        try {
            //the Formal Parameter of a for-each loop is not in the scope when
            //attributing the for-each expression; we mimick this by attributing
            //the for-each expression first (against original scope).
            Type exprType = types().cvarUpperBound(attribExpr(tree.expr, loopEnv));
            attribStat(tree.var, loopEnv);
            ReflectUtil.method(chk(), "checkNonVoid", DiagnosticPosition.class, Type.class).invoke(tree.pos(), exprType);
            Type elemtype = types().elemtype(exprType); // perhaps expr is an array?
            if (elemtype == null) {
                // or perhaps expr implements Iterable<T>?
                Type base = types().asSuper(exprType, syms().iterableType.tsym);
                if (base == null) {
                    getLogger().error(tree.expr.pos(),
                            "foreach.not.applicable.to.type",
                            exprType,
                            ReflectUtil.method(ReflectUtil.field(this, "diags").get(), "fragment", String.class).invoke("type.req.array.or.iterable"));
                    elemtype = types().createErrorType(exprType);
                } else {
                    List<Type> iterableParams = base.allparams();
                    elemtype = iterableParams.isEmpty()
                            ? syms().objectType
                            : types().wildUpperBound(iterableParams.head);
                }
            }
            if (AUTO_TYPE.equals(tree.var.type.tsym.getQualifiedName().toString())) {
                tree.var.type = elemtype;
                tree.var.sym.type = elemtype;
            }
            ReflectUtil.method(chk(), "checkType", DiagnosticPosition.class, Type.class, Type.class)
                    .invoke(tree.expr.pos(), elemtype, tree.var.sym.type);
            loopEnv.tree = tree; // before, we were not in loop!
            attribStat(tree.body, loopEnv);
            ReflectUtil.field(this, "result").set(null);
        } finally {
            ReflectUtil.method(ReflectUtil.field(loopEnv.info, "scope").get(), "leave").invoke();
        }
    }

    private void inferAutoLocalVar(JCTree.JCVariableDecl tree) {
        if (!isAutoType(tree.type)) {
            // not 'auto' variable
            return;
        }

//    if( ((Scope)ReflectUtil.field( getEnv().info, "scope" ).get()).owner.kind != MTH )
//    {
//      // not a local var
//      return;
//    }

        JCTree.JCExpression initializer = tree.getInitializer();
        if (initializer == null) {
            // no initializer, no type inference

            Tree parent = JavacPlugin.instance().getTypeProcessor().getParent(tree, getEnv().toplevel);
            if (!(parent instanceof JCTree.JCEnhancedForLoop)) {
                IDynamicJdk.instance().logError(Log.instance(JavacPlugin.instance().getContext()), tree.getType().pos(),
                        "proc.messager", IssueMsg.MSG_AUTO_CANNOT_INFER_WO_INIT.get());
            }
            return;
        }

        if (initializer.type == syms().botType) {
            IDynamicJdk.instance().logError(Log.instance(JavacPlugin.instance().getContext()), tree.getType().pos(),
                    "proc.messager", IssueMsg.MSG_AUTO_CANNOT_INFER_FROM_NULL.get());
            return;
        }

        tree.type = initializer.type;
        tree.sym.type = initializer.type;
    }

    @Override
    public void visitReturn(JCTree.JCReturn tree) {
        boolean isAutoMethod = isAutoMethod();
        if (isAutoMethod) {
            Object resultInfo = ReflectUtil.field(getEnv().info, "returnResult").get();
            if (resultInfo != null) {
                ReflectUtil.field(resultInfo, "pt").set(Type.noType);
            }
        }

        super.visitReturn(tree);

        if (isAutoMethod) {
            reassignAutoMethodReturnTypeToInferredType(tree);
        }
    }

    private boolean isAutoMethod() {
        JCTree.JCMethodDecl enclMethod = getEnv().enclMethod;
        return enclMethod != null && enclMethod.getReturnType() != null &&
                "auto".equals(enclMethod.getReturnType().toString());
    }

    private void reassignAutoMethodReturnTypeToInferredType(JCTree.JCReturn tree) {
        if (tree.expr == null) {
            return;
        }

        if (_methodDefs.isEmpty()) {
            return;
        }

        Type returnExprType = tree.expr.type;
        if (returnExprType.isErroneous()) {
            return;
        }

        if (!isAutoType(returnExprType)) {
            JCTree.JCMethodDecl meth = _methodDefs.peek();

            // remove the constant type e.g., if derived from a constant return value such as "Foo"
            returnExprType = returnExprType.baseType();

            // compute LUB of the previous method return type assignment and this return expr type
            returnExprType = isAutoType(meth.restype.type)
                    ? returnExprType
                    : lub(tree, meth.restype.type, returnExprType).baseType();

            // now assign the computed type to the method's return type
            assignMethodReturnType(returnExprType, meth);
        } else {
            JCTree.JCExpression returnExpr = tree.expr;
            while (returnExpr instanceof JCTree.JCParens) {
                returnExpr = ((JCTree.JCParens) returnExpr).expr;
            }
            if (!(returnExpr instanceof JCTree.JCMethodInvocation)) {
                IDynamicJdk.instance().logError(Log.instance(JavacPlugin.instance().getContext()), tree.expr.pos(),
                        "proc.messager", IssueMsg.MSG_AUTO_RETURN_MORE_SPECIFIC_TYPE.get());
            }
        }
    }

    private void assignMethodReturnType(Type returnExprType, JCTree.JCMethodDecl meth) {
        ((Type.MethodType) meth.sym.type).restype = returnExprType;
        if (meth.restype instanceof JCTree.JCIdent) {
            ((JCTree.JCIdent) meth.restype).sym = returnExprType.tsym;
        } else if (meth.restype instanceof JCTree.JCFieldAccess) {
            ((JCTree.JCFieldAccess) meth.restype).sym = returnExprType.tsym;
        }
        meth.restype.type = returnExprType;

        // cause the msym's erasure field to reset with the new return type
        meth.sym.erasure_field = null;
        types().memberType(getEnv().enclClass.sym.type, meth.sym);
    }

    private Type lub(JCTree.JCReturn tree, Type type, Type returnExprType) {
        return (Type) ReflectUtil.method(this, "condType", DiagnosticPosition.class, Type.class, Type.class)
                .invoke(tree, type, returnExprType);
    }

    public JCTree.JCMethodDecl peekMethodDef() {
        return _methodDefs.isEmpty() ? null : _methodDefs.peek();
    }

    /**
     * Facilitates @Jailbreak. ManResolve#isAccessible() needs to know the JCAnnotatedType in context.
     */
    @Override
    public void visitAnnotatedType(JCTree.JCAnnotatedType tree) {
        _annotatedTypes.push(tree);
        try {
            super.visitAnnotatedType(tree);
        } finally {
            _annotatedTypes.pop();
        }
    }

    public JCTree.JCFieldAccess peekSelect() {
        return _selects.isEmpty() ? null : _selects.peek();
    }

    public JCTree.JCAnnotatedType peekAnnotatedType() {
        return _annotatedTypes.isEmpty() ? null : _annotatedTypes.peek();
    }

    @Override
    public void visitIdent(JCTree.JCIdent tree) {
        super.visitIdent(tree);
        patchAutoFieldType(tree);
    }

    /**
     * Handles @Jailbreak, unit expressions, 'auto'
     */
    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        if (!(tree.meth instanceof JCTree.JCFieldAccess)) {
            if (!handleTupleType(tree)) {
                super.visitApply(tree);
                patchMethodType(tree, _visitedAutoMethodCalls);
            }
            return;
        }

        if (JAILBREAK_PRIVATE_FROM_SUPERS) {
            _manLog.pushSuspendIssues(tree); // since method-calls can be nested, we need a tree of stacks TreeNode(JCTree.JCFieldAccess, Stack<JCDiagnostic>>)
        }

        JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) tree.meth;
        try {
            super.visitApply(tree);
            patchMethodType(tree, _visitedAutoMethodCalls);

            if (JAILBREAK_PRIVATE_FROM_SUPERS) {
                if (fieldAccess.type instanceof Type.ErrorType) {
                    if (shouldCheckSuperType(fieldAccess.selected.type) && _manLog.isJailbreakSelect(fieldAccess)) {
                        // set qualifier type to supertype to handle private methods
                        Type.ClassType oldType = (Type.ClassType) fieldAccess.selected.type;
                        fieldAccess.selected.type = ((Symbol.ClassSymbol) oldType.tsym).getSuperclass();
                        ((JCTree.JCIdent) fieldAccess.selected).sym.type = fieldAccess.selected.type;
                        fieldAccess.type = null;
                        fieldAccess.sym = null;
                        tree.type = null;

                        // retry with supertype
                        visitApply(tree);

                        // restore original type
                        fieldAccess.selected.type = oldType;
                        ((JCTree.JCIdent) fieldAccess.selected).sym.type = fieldAccess.selected.type;
                    }
                } else {
                    // apply any issues logged for the found method (only the top of the suspend stack)
                    _manLog.recordRecentSuspendedIssuesAndRemoveOthers(tree);
                }
            }
        } finally {
            if (JAILBREAK_PRIVATE_FROM_SUPERS) {
                _manLog.popSuspendIssues(tree);
            }
        }
    }

    private boolean handleTupleType(JCTree.JCMethodInvocation tree) {
        if (!(tree.meth instanceof JCTree.JCIdent) ||
                !((JCTree.JCIdent) tree.meth).name.toString().equals("$manifold_tuple")) {
            return false;
        }

        Env<AttrContext> localEnv = getEnv().dup(tree, ReflectUtil.method(getEnv().info, "dup").invoke());
//    ListBuffer<Type> argtypesBuf = new ListBuffer<>();
        List<JCTree.JCExpression> argsNoLabels = removeLabels(tree.args);
//    ReflectUtil.method( this, "attribArgs", int.class, List.class, Env.class, ListBuffer.class ).
//      invoke( VAL, argsNoLabels, localEnv, argtypesBuf );
        ReflectUtil.method(this, "attribExprs", List.class, Env.class, Type.class).
                invoke(argsNoLabels, localEnv, Type.noType);
        Map<JCTree.JCExpression, String> namesByArg = new HashMap<>();
        Map<String, String> fieldMap = makeTupleFieldMap(tree.args, namesByArg);
        // sort alphabetically
        argsNoLabels = List.from(argsNoLabels.stream().sorted(Comparator.comparing(namesByArg::get)).collect(Collectors.toList()));
        String pkg = findPackageForTuple();
        String tupleTypeName = ITupleTypeProvider.INSTANCE.get().makeType(pkg, fieldMap);
        Tree parent = JavacPlugin.instance().getTypeProcessor().getParent(tree, getEnv().toplevel);
        Symbol.ClassSymbol tupleTypeSym = findTupleClassSymbol(tupleTypeName);
        if (tupleTypeSym == null) {
            //todo: compile error?
            return false;
        }

        JCTree.JCNewClass newTuple = makeNewTupleClass(tupleTypeSym.type, tree, argsNoLabels);
        if (parent instanceof JCTree.JCReturn) {
            ((JCTree.JCReturn) parent).expr = newTuple;
        } else if (parent instanceof JCTree.JCParens) {
            ((JCTree.JCParens) parent).expr = newTuple;
        }
        ReflectUtil.field(this, "result").set(tupleTypeSym.type);
        return true;
    }

    private void addEnclosingClassOnTupleType(String fqn) {
        Set<ITypeManifold> typeManifolds = JavacPlugin.instance().getHost().getSingleModule().findTypeManifoldsFor(fqn);
        ITypeManifold tm = typeManifolds.stream().findFirst().orElse(null);
        ReflectUtil.method(tm, "addEnclosingSourceFile", String.class, URI.class)
                .invoke(fqn, getEnv().enclClass.sym.sourcefile.toUri());
    }

    // if method overrides another method, use package of overridden method for tuples defined in override method
    private String findPackageForTuple() {
        JCTree.JCMethodDecl enclMethod = getEnv().enclMethod;
        if (enclMethod == null) {
            return getEnv().toplevel.packge.fullname.toString();
        }
        Set<Symbol.MethodSymbol> overriddenMethods = JavacTypes.instance(JavacPlugin.instance().getContext())
                .getOverriddenMethods(enclMethod.sym);
        if (overriddenMethods.isEmpty()) {
            return getEnv().toplevel.packge.fullname.toString();
        }
        Symbol.MethodSymbol overridden = overriddenMethods.iterator().next();
        return overridden.owner.packge().fullname.toString();
    }

    private Symbol.ClassSymbol findTupleClassSymbol(String tupleTypeName) {
        addEnclosingClassOnTupleType(tupleTypeName);

        // First, try to load the class the normal way via FileManager#list()

        Context ctx = JavacPlugin.instance().getContext();
        Symbol.ClassSymbol sym = IDynamicJdk.instance().getTypeElement(ctx, getEnv().toplevel, tupleTypeName);
        if (sym != null) {
            return sym;
        }

        // Next, since tuples are not files and are therefore not known in advance for #list() to work, we force the
        // compiler to load it via ClassReader/Finder#includeClassFile()

        IModule compilingModule = JavacPlugin.instance().getHost().getSingleModule();
        if (compilingModule == null) {
            return null;
        }
        String pkg = ManClassUtil.getPackage(tupleTypeName);
        Symbol.PackageSymbol pkgSym;
        if (JreUtil.isJava8()) {
            pkgSym = JavacElements.instance(ctx).getPackageElement(pkg);
        } else {
            Object moduleSym = ReflectUtil.field(getEnv().toplevel, "modle").get();
            pkgSym = (Symbol.PackageSymbol) ReflectUtil.method(JavacElements.instance(ctx), "getPackageElement",
                    ReflectUtil.type("javax.lang.model.element.ModuleElement"), String.class).invoke(moduleSym, pkg);
        }
        IssueReporter<JavaFileObject> issueReporter = new IssueReporter<>(() -> ctx);
        String fqn = tupleTypeName.replace('$', '.');
        ManifoldJavaFileManager fm = JavacPlugin.instance().getManifoldFileManager();
        JavaFileObject file = fm.findGeneratedFile(fqn, StandardLocation.CLASS_PATH, compilingModule, issueReporter);
        Object classReader = JreUtil.isJava8()
                ? ClassReader.instance(ctx)
                : ReflectUtil.method("com.sun.tools.javac.code.ClassFinder", "instance", Context.class).invokeStatic(ctx);
        ReflectUtil.method(classReader, "includeClassFile", Symbol.PackageSymbol.class, JavaFileObject.class)
                .invoke(pkgSym, file);
        return IDynamicJdk.instance().getTypeElement(ctx, getEnv().toplevel, tupleTypeName);
    }

    private List<JCTree.JCExpression> removeLabels(List<JCTree.JCExpression> args) {
        List<JCTree.JCExpression> filtered = List.nil();
        for (JCTree.JCExpression arg : args) {
            if (arg instanceof JCTree.JCMethodInvocation &&
                    ((JCTree.JCMethodInvocation) arg).meth instanceof JCTree.JCIdent) {
                JCTree.JCIdent ident = (JCTree.JCIdent) ((JCTree.JCMethodInvocation) arg).meth;
                if ("$manifold_label".equals(ident.name.toString())) {
                    continue;
                }
            }
            filtered = filtered.append(arg);
        }
        return filtered;
    }

    JCTree.JCNewClass makeNewTupleClass(Type tupleType, JCTree.JCExpression treePos, List<JCTree.JCExpression> args) {
        TreeMaker make = TreeMaker.instance(JavacPlugin.instance().getContext());
        JCTree.JCNewClass tree = make.NewClass(null,
                null, make.QualIdent(tupleType.tsym), args, null);
        Resolve rs = (Resolve) ReflectUtil.field(this, "rs").get();
        tree.constructor = (Symbol) ReflectUtil.method(rs, "resolveConstructor",
                DiagnosticPosition.class, Env.class, Type.class, List.class, List.class).invoke(
                treePos.pos(), getEnv(), tupleType, TreeInfo.types(args), List.<Type>nil());
        tree.constructorType = tree.constructor.type;
        tree.type = tupleType;
        tree.pos = treePos.pos;
        return tree;
    }

    private Map<String, String> makeTupleFieldMap(List<JCTree.JCExpression> args, Map<JCTree.JCExpression, String> argsByName) {
        Map<String, String> map = new LinkedHashMap<>();
        int nullNameCount = 0;
        for (int j = 0, argsSize = args.size(); j < argsSize; j++) {
            JCTree.JCExpression arg = args.get(j);

            String name = null;

            if (arg instanceof JCTree.JCMethodInvocation &&
                    ((JCTree.JCMethodInvocation) arg).meth instanceof JCTree.JCIdent) {
                JCTree.JCIdent ident = (JCTree.JCIdent) ((JCTree.JCMethodInvocation) arg).meth;
                if ("$manifold_label".equals(ident.name.toString())) {
                    JCTree.JCIdent labelArg = (JCTree.JCIdent) ((JCTree.JCMethodInvocation) arg).args.get(0);
                    name = labelArg.name.toString();
                    if (++j < args.size()) {
                        arg = args.get(j);
                    } else {
                        break;
                    }
                }
            }

            if (name == null) {
                if (arg instanceof JCTree.JCIdent) {
                    name = ((JCTree.JCIdent) arg).name.toString();
                } else if (arg instanceof JCTree.JCFieldAccess) {
                    name = ((JCTree.JCFieldAccess) arg).name.toString();
                } else if (arg instanceof JCTree.JCMethodInvocation) {
                    JCTree.JCExpression meth = ((JCTree.JCMethodInvocation) arg).meth;
                    if (meth instanceof JCTree.JCIdent) {
                        name = getFieldNameFromMethodName(((JCTree.JCIdent) meth).name.toString());
                    } else if (meth instanceof JCTree.JCFieldAccess) {
                        name = getFieldNameFromMethodName(((JCTree.JCFieldAccess) meth).name.toString());
                    }
                }
            }
            String item = name == null ? "item" : name;
            if (name == null) {
                item += ++nullNameCount;
            }
            name = item;
            for (int i = 2; map.containsKey(item); i++) {
                item = name + '_' + i;
            }
            Type type = arg.type == syms().botType
                    ? syms().objectType
                    : RecursiveTypeVarEraser.eraseTypeVars(types(), arg.type);
            String typeName = type.toString();
            map.put(item, typeName);
            argsByName.put(arg, item);
        }
        return map;
    }

    /**
     * Changes method name to a field name like this:
     * getAddress -> address
     * callHome -> home
     * findJDKVersion -> jdkVersion
     * id -> id
     */
    private String getFieldNameFromMethodName(String methodName) {
        for (int i = 0; i < methodName.length(); i++) {
            if (Character.isUpperCase(methodName.charAt(i))) {
                StringBuilder name = new StringBuilder(methodName.substring(i));
                for (int j = 0; j < name.length(); j++) {
                    char c = name.charAt(j);
                    if (Character.isUpperCase(c) &&
                            (j == 0 || j == name.length() - 1 || Character.isUpperCase(name.charAt(j + 1)))) {
                        name.setCharAt(j, Character.toLowerCase(c));
                    } else {
                        break;
                    }
                }
                return name.toString();
            }
        }
        return methodName;
    }

    public Type attribExpr(JCTree tree, Env<AttrContext> env, Type pt) {
        if (isAutoType(pt)) {
            // don't let 'auto' type influence the expression's type
            pt = Type.noType;
        }
        return super.attribExpr(tree, env, pt);
    }

    @Override
    public void visitIndexed(JCTree.JCArrayAccess tree) {
        if (!JavacPlugin.instance().isExtensionsEnabled()) {
            super.visitIndexed(tree);
            return;
        }

        ManAttr.super.handleIndexedOverloading(tree);
    }

    @Override
    public void visitAssign(JCTree.JCAssign tree) {
        Class<?> ResultInfo_Class = ReflectUtil.type(Attr.class.getTypeName() + "$ResultInfo");
        Type owntype = (Type) ReflectUtil.method(this, "attribTree", JCTree.class, Env.class, ResultInfo_Class)
                .invoke(tree.lhs, getEnv().dup(tree), ReflectUtil.field(this, "varInfo").get());

        if (tree.lhs.type != null && tree.lhs.type.isPrimitive()) {
            // always cast rhs for the case where the original statement was a compound assign involving a primitive type
            // (manifold transforms a += b to a = a + b, so that we can simply use plus() to handle both addition and compound
            // assign addition, however:
            //   short a = 0;
            //   a += (byte)b;
            // blows up if we don't cast the rhs of the resulting
            // transformation:  a += (byte)b;  parse==>  a = a + (byte)b;  attr==>  a = (short) (a + (byte)b);
            tree.rhs = makeCast(tree.rhs, tree.lhs.type);
        }

        Type capturedType = types().capture(owntype);
        attribExpr(tree.rhs, getEnv(), owntype);
        setResult(tree, capturedType);
        ReflectUtil.field(this, "result").set(ReflectUtil.method(this, "check", JCTree.class, Type.class, int.class, ResultInfo_Class)
                .invoke(tree, capturedType, VAL, ReflectUtil.field(this, "resultInfo").get()));

        ensureIndexedAssignmentIsWritable(tree.lhs);
    }

    @Override
    public void visitAssignop(JCTree.JCAssignOp tree) {
        super.visitAssignop(tree);

        ensureIndexedAssignmentIsWritable(tree.lhs);
    }

    @Override
    public void visitBinary(JCTree.JCBinary tree) {
        if (!JavacPlugin.instance().isExtensionsEnabled()) {
            super.visitBinary(tree);
            return;
        }

        if (tree.getTag() == JCTree.Tag.APPLY) // binding expr
        {
            // Handle binding expressions

            visitBindingExpression(tree);
            ReflectUtil.field(tree, "opcode").set(JCTree.Tag.MUL); // pose as a MUL expr to pass binary expr checks
            return;
        }

        ReflectUtil.LiveMethodRef checkNonVoid = ReflectUtil.method(chk(), "checkNonVoid", DiagnosticPosition.class, Type.class);
        ReflectUtil.LiveMethodRef attribExpr = ReflectUtil.method(this, "attribExpr", JCTree.class, Env.class);
        Type left = (Type) checkNonVoid.invoke(tree.lhs.pos(), attribExpr.invoke(tree.lhs, getEnv()));
        Type right = (Type) checkNonVoid.invoke(tree.rhs.pos(), attribExpr.invoke(tree.rhs, getEnv()));

        if (handleOperatorOverloading(tree, left, right)) {
            // Handle operator overloading
            return;
        }

        // Everything after left/right operand attribution (see super.visitBinary())
        _visitBinary_Rest(tree, left, right);
    }

    private void _visitBinary_Rest(JCTree.JCBinary tree, Type left, Type right) {
        // Find operator.
        Symbol operator = tree.operator =
                (Symbol) ReflectUtil.method(rs(), "resolveBinaryOperator",
                                DiagnosticPosition.class, JCTree.Tag.class, Env.class, Type.class, Type.class)
                        .invoke(tree.pos(), tree.getTag(), getEnv(), left, right);

        Type owntype = types().createErrorType(tree.type);
        if (operator.kind == MTH &&
                !left.isErroneous() &&
                !right.isErroneous()) {
            owntype = operator.type.getReturnType();
            // This will figure out when unboxing can happen and
            // choose the right comparison operator.
            int opc = (int) ReflectUtil.method(chk(), "checkOperator",
                            DiagnosticPosition.class, OperatorSymbol.class, JCTree.Tag.class, Type.class, Type.class)
                    .invoke(tree.lhs.pos(), operator, tree.getTag(), left, right);

            // If both arguments are constants, fold them.
            if (left.constValue() != null && right.constValue() != null) {
                Type ctype = (Type) ReflectUtil.method(cfolder(), "fold2", int.class, Type.class, Type.class).invoke(opc, left, right);
                if (ctype != null) {
                    owntype = (Type) ReflectUtil.method(cfolder(), "coerce", Type.class, Type.class).invoke(ctype, owntype);
                }
            }

            // Check that argument types of a reference ==, != are
            // castable to each other, (JLS 15.21).  Note: unboxing
            // comparisons will not have an acmp* opc at this point.
            if ((opc == ByteCodes.if_acmpeq || opc == ByteCodes.if_acmpne)) {
                if (!types().isEqualityComparable(left, right,
                        new Warner(tree.pos()))) {
                    getLogger().error(tree.pos(), "incomparable.types", left, right);
                }
            }

            ReflectUtil.method(chk(), "checkDivZero", DiagnosticPosition.class, Symbol.class, Type.class)
                    .invoke(tree.rhs.pos(), operator, right);
        }
        setResult(tree, owntype);
    }

    @Override
    public void visitUnary(JCTree.JCUnary tree) {
        if (!JavacPlugin.instance().isExtensionsEnabled()) {
            super.visitUnary(tree);
            return;
        }

        if (handleUnaryOverloading(tree)) {
            return;
        }

        super.visitUnary(tree);
    }

    /**
     * Overrides to handle fragments in String literals
     */
    public void visitLiteral(JCTree.JCLiteral tree) {
        if (tree.typetag == CLASS && tree.value.toString().startsWith("[>")) {
            Type type = getFragmentValueType(tree);
            tree.type = type;
            ReflectUtil.field(this, "result").set(type);
        } else {
            super.visitLiteral(tree);
        }
    }

    private Type getFragmentValueType(JCTree.JCLiteral tree) {
        try {
            CharSequence source = ParserFactoryFiles.getSource(getEnv().toplevel.sourcefile);
            CharSequence chars = source.subSequence(tree.pos().getStartPosition(),
                    tree.pos().getEndPosition(getEnv().toplevel.endPositions));
            FragmentProcessor.Fragment fragment = FragmentProcessor.instance().parseFragment(
                    tree.pos().getStartPosition(), chars.toString(),
                    chars.length() > 3 && chars.charAt(1) == '"'
                            ? TEXT_BLOCK_LITERAL
                            : DOUBLE_QUOTE_LITERAL);
            if (fragment != null) {
                String fragClass = getEnv().toplevel.packge.toString() + '.' + fragment.getName();
                Symbol.ClassSymbol fragSym = IDynamicJdk.instance().getTypeElement(JavacPlugin.instance().getContext(), getEnv().toplevel, fragClass);
                for (Attribute.Compound annotation : fragSym.getAnnotationMirrors()) {
                    if (annotation.type.toString().equals(FragmentValue.class.getName())) {
                        Type type = getFragmentValueType(annotation);
                        if (type != null) {
                            return type;
                        }
                    }
                }
                getLogger().rawWarning(tree.pos().getStartPosition(),
                        "No @" + FragmentValue.class.getSimpleName() + " is provided for metatype '" + fragment.getExt() + "'. The resulting value remains a String literal.");
            }
        } catch (Exception e) {
            getLogger().rawWarning(tree.pos().getStartPosition(),
                    "Error parsing Manifold fragment.\n" +
                            e.getClass().getSimpleName() + ": " + e.getMessage() + "\n" +
                            (e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : ""));
        }
        return _syms.stringType.constType(tree.value);
    }

    private Type getFragmentValueType(Attribute.Compound attribute) {
        String type = null;
        for (com.sun.tools.javac.util.Pair<Symbol.MethodSymbol, Attribute> pair : attribute.values) {
            Name argName = pair.fst.getSimpleName();
            if (argName.toString().equals("type")) {
                type = (String) pair.snd.getValue();
            }
        }

        if (type != null) {
            Symbol.ClassSymbol fragValueSym = IDynamicJdk.instance().getTypeElement(JavacPlugin.instance().getContext(), getEnv().toplevel, type);
            if (fragValueSym != null) {
                return fragValueSym.type;
            }
        }

        return null;
    }

    @Override
    public void attribClass(DiagnosticPosition pos, Symbol.ClassSymbol c) {
        if (c.sourcefile instanceof ISelfCompiledFile) {
            ISelfCompiledFile sourcefile = (ISelfCompiledFile) c.sourcefile;
            String fqn = c.getQualifiedName().toString();
            if (sourcefile.isSelfCompile(fqn)) {
                // signal the self-compiled class to fully parse and report errors
                // (note its source in javac is just a stub)
                sourcefile.parse(fqn);
            }
        }

        super.attribClass(pos, c);
    }
}