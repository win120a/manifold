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

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;

import java.lang.reflect.Modifier;
import java.util.ArrayList;

import manifold.rt.api.NoBootstrap;
import manifold.rt.api.IBootstrap;


/**
 * Add a static block to top-level classes to automatically initialize Manifold:
 * <pre>
 *   static {
 *     Bootstrap.init();
 *   }
 * </pre>
 * Note this call is fast and does nothing if Manifold is already bootstrapped i.e., it is idempotent.
 * <p/>
 * You can use {@link NoBootstrap} to prevent a class from having the bootstrap block inserted.
 * <p/>
 * You can use the {@code no-bootstrap} Manifold plugin argument to completely disable bootstrap blocks from your
 * project.
 */
class BootstrapInserter extends TreeTranslator {
    private JavacPlugin _javacPlugin;

    public BootstrapInserter(JavacPlugin javacPlugin) {
        _javacPlugin = javacPlugin;
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        super.visitClassDef(tree);
        if (tree.sym != null && !tree.sym.isInner() && !tree.sym.isInterface()) {
            if (okToInsertBootstrap(tree)) {
                JCTree.JCStatement newNode = buildBootstrapStaticBlock();
                ArrayList<JCTree> newDefs = new ArrayList<>(tree.defs);
                newDefs.add(0, newNode);
                tree.defs = List.from(newDefs);
            }
        }
        result = tree;
    }

    private boolean okToInsertBootstrap(JCTree.JCClassDecl tree) {
        return !annotatedWith_NoBootstrap(tree.getModifiers().getAnnotations()) &&
                !JavacPlugin.instance().isNoBootstrapping() &&
                isExtensionsEnabled() &&
                !alreadyHasBootstrap(tree) &&
                !skipForOtherReasons(tree);
    }

    /**
     * Check for existence of manifold-ext in the project. If not in use, no need to bootstrap
     */
    public boolean isExtensionsEnabled() {
        try {
            Class.forName("manifold.ext.rt.api.Extension");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // If an annotation processor is active, a class can be processed multiple times,
    // so we check to see if we've already added the bootstrap block.
    private boolean alreadyHasBootstrap(JCTree.JCClassDecl tree) {
        return tree.defs.stream().anyMatch(
                def -> {
                    if (def instanceof JCTree.JCBlock) {
                        String staticBlock = def.toString();
                        return staticBlock.startsWith("static") &&
                                staticBlock.contains(IBootstrap.class.getSimpleName()) &&
                                staticBlock.contains(".dasBoot");
                    }
                    return false;
                });
    }

    private boolean skipForOtherReasons(JCTree.JCClassDecl tree) {
        if ((tree.getModifiers().flags & Flags.ANNOTATION) != 0) {
            // don't bootstrap from an annotation class,
            // many tools do not handle the presence of the <clinit> method well
            return true;
        }

        if (tree.implementing != null) {
            for (JCTree.JCExpression iface : tree.implementing) {
                if (iface.toString().contains("ManifoldHost")) {
                    // Don't insert bootstrap in a IManifoldHost impl
                    return true;
                }
            }
        }

        return false;
    }

    private boolean annotatedWith_NoBootstrap(List<JCTree.JCAnnotation> annotations) {
        for (JCTree.JCAnnotation anno : annotations) {
            if (anno.getAnnotationType().toString().endsWith(NoBootstrap.class.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private JCTree.JCStatement buildBootstrapStaticBlock() {
        TreeMaker make = _javacPlugin.getTreeMaker();
        JavacElements javacElems = _javacPlugin.getJavacElements();

        JCTree.JCMethodInvocation bootstrapInitCall = make.Apply(List.nil(), memberAccess(make, javacElems, IBootstrap.class.getName() + ".dasBoot"), List.nil());
        return make.Block(Modifier.STATIC, List.of(make.Exec(bootstrapInitCall)));
    }

    private JCTree.JCExpression memberAccess(TreeMaker make, JavacElements javacElems, String path) {
        return memberAccess(make, javacElems, path.split("\\."));
    }

    private JCTree.JCExpression memberAccess(TreeMaker make, JavacElements node, String... components) {
        JCTree.JCExpression expr = make.Ident(node.getName(components[0]));
        for (int i = 1; i < components.length; i++) {
            expr = make.Select(expr, node.getName(components[i]));
        }
        return expr;
    }
}
