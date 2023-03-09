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

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.Collections;
import java.util.Locale;
import java.util.function.Predicate;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;


public class JavaDynamicJdk_8 implements IDynamicJdk {
    @Override
    public <T> void report(Log issueLogger, Diagnostic<? extends T> diagnostic) {
        // Adapted from JavacMessager.printMessage.  Following same basic routine regarding use of Log

        JavaFileObject oldSource = issueLogger.useSource((JavaFileObject) diagnostic.getSource());
        boolean oldMultipleErrors = issueLogger.multipleErrors;
        issueLogger.multipleErrors = true;
        try {
            switch (diagnostic.getKind()) {
                case ERROR:
                    issueLogger.error(new IssueReporter.Position(diagnostic), "proc.messager", diagnostic.getMessage(Locale.getDefault()));
                    break;
                case WARNING:
                    issueLogger.warning(new IssueReporter.Position(diagnostic), "proc.messager", diagnostic.getMessage(Locale.getDefault()));
                    break;
                case MANDATORY_WARNING:
                    issueLogger.mandatoryWarning(new IssueReporter.Position(diagnostic), "proc.messager", diagnostic.getMessage(Locale.getDefault()));
                    break;
                case NOTE:
                case OTHER:
                    issueLogger.note(new IssueReporter.Position(diagnostic), "proc.messager", diagnostic.getMessage(Locale.getDefault()));
                    break;
            }
        } finally {
            issueLogger.useSource(oldSource);
            issueLogger.multipleErrors = oldMultipleErrors;
        }
    }

    @Override
    public Iterable<Symbol> getMembers(Symbol.ClassSymbol classSym, boolean completeFirst) {
        Scope members = completeFirst ? classSym.members() : classSym.members_field;
        return members == null ? Collections.emptyList() : members.getElements();
    }

    @Override
    public Iterable<Symbol> getMembers(Symbol.ClassSymbol classSym, Predicate<Symbol> predicate, boolean completeFirst) {
        Scope members = completeFirst ? classSym.members() : classSym.members_field;
        return members == null ? Collections.emptyList() : members.getElements(t -> predicate.test(t));
    }

    @Override
    public Iterable<Symbol> getMembersByName(Symbol.ClassSymbol classSym, Name call, boolean completeFirst) {
        Scope members = completeFirst ? classSym.members() : classSym.members_field;
        return members == null ? Collections.emptyList() : members.getElementsByName(call);
    }

    @Override
    public Symbol.ClassSymbol getTypeElement(Context ctx, Object ignore, String fqn) {
        return JavacElements.instance(ctx).getTypeElement(fqn);
    }

    @Override
    public Symbol.ClassSymbol getLoadedClass(Context ctx, String fqn) {
        Name name = Names.instance(ctx).fromString(fqn);
        return Symtab.instance(ctx).classes.get(name);
    }

    public void setOperatorSymbol(Context ctx, JCTree.JCBinary cond, JCTree.Tag tag, String op, Symbol operandType) {
        Symbol.OperatorSymbol operatorSym = (Symbol.OperatorSymbol) IDynamicJdk.instance().getMembers(
                        Symtab.instance(ctx).predefClass,
                        (Symbol s) -> s instanceof Symbol.OperatorSymbol &&
                                s.name.toString().equals(op) &&
                                ((Symbol.MethodSymbol) s).params().get(0).type.tsym == operandType)
                .iterator().next(); // should be just one
        setOperator(cond, operatorSym);
    }

    @Override
    public List<Type> getTargets(JCTree.JCLambda tree) {
        return tree.targets;
    }

    @Override
    public void setTargets(JCTree.JCLambda tree, List<Type> targets) {
        tree.targets = targets;
    }

    @Override
    public Symbol getOperator(JCTree.JCExpression tree) {
        return tree instanceof JCTree.JCBinary ? ((JCTree.JCBinary) tree).operator : ((JCTree.JCUnary) tree).operator;
    }

    @Override
    public void setOperator(JCTree.JCExpression tree, Symbol.OperatorSymbol operator) {
        if (tree instanceof JCTree.JCBinary) {
            ((JCTree.JCBinary) tree).operator = operator;
        } else {
            ((JCTree.JCUnary) tree).operator = operator;
        }
    }

    @Override
    public void logError(Log logger, JCDiagnostic.DiagnosticPosition pos, String key, Object... args) {
        logger.error(pos, key, args);
    }

    @Override
    public void logWarning(Log logger, JCDiagnostic.DiagnosticPosition pos, String key, Object... args) {
        logger.warning(pos, key, args);
    }
}
