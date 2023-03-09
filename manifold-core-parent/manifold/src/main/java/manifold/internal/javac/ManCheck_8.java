/*
 * Copyright (c) 2020 - Manifold Systems LLC
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

import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import com.sun.tools.javac.util.JCDiagnostic;
import manifold.util.ReflectUtil;

public class ManCheck_8 extends Check {
    private static final String CHECK_FIELD = "chk";
    private boolean _enterGuard;

    public static Check instance(Context ctx) {
        Check check = ctx.get(checkKey);
        if (!(check instanceof ManCheck_8)) {
            ctx.put(checkKey, (Check) null);
            check = new ManCheck_8(ctx);
        }

        return check;
    }

    private ManCheck_8(Context ctx) {
        super(ctx);

        ReflectUtil.field(JavaCompiler.instance(ctx), CHECK_FIELD).set(this);
        ReflectUtil.field(Annotate.instance(ctx), CHECK_FIELD).set(this);
    }

    @Override
    public void reportDeferredDiagnostics() {
        // compile manifold types that were not referenced directly or indirectly from explicitly compiled Java files
        StaticCompiler.instance().compileRemainingTypes_ByFile();
        StaticCompiler.instance().compileRemainingTypes_ByTypeNameRegexes();

        super.reportDeferredDiagnostics();
    }

    @Override
    public void checkRedundantCast(Env<AttrContext> env, JCTree.JCTypeCast tree) {
        if (tree instanceof ManTypeCast) {
            // ManTypeCast is always generated and should never cause a warning
            return;
        }

        super.checkRedundantCast(env, tree);
    }

    @Override
    public void warnStatic(JCDiagnostic.DiagnosticPosition pos, String msg, Object... args) {
        JCTree tree = pos.getTree();
        if (tree instanceof JCTree.JCFieldAccess) {
            JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) tree;
            if (fieldAccess.sym.enclClass().isInterface() &&
                    fieldAccess.sym.getAnnotationMirrors().stream()
                            .anyMatch(e -> e.toString().contains("manifold.ext.props.rt.api"))) {
                // filter properties on interfaces
                return;
            }
        }
        super.warnStatic(pos, msg, args);
    }
}
