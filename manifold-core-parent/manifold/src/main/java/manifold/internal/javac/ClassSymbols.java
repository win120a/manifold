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

import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.util.Options;
import manifold.api.fs.IResource;
import manifold.api.gen.SrcClass;
import manifold.api.host.IModule;
import manifold.api.host.ITypeSystemListener;
import manifold.api.host.RefreshRequest;
import manifold.rt.api.util.ManClassUtil;
import manifold.rt.api.util.Pair;
import manifold.api.util.SourcePathUtil;
import manifold.rt.api.util.Stack;
import manifold.util.JreUtil;
import manifold.util.ReflectUtil;
import manifold.util.concurrent.LocklessLazyVar;

/**
 * Utility to get ClassSymbol for a given type name.
 * <p/>
 * Note this class must have a FileManager separate from the one used in JavaParser (or JavacPlugin)
 * to avoid circularity issues.
 */
public class ClassSymbols {
    private static final Map<IModule, ClassSymbols> INSTANCES = new ConcurrentHashMap<>();

    private final IModule _module;
    private LocklessLazyVar<BasicJavacTask> _altJavacTask_PlainFileMgr;
    private LocklessLazyVar<BasicJavacTask> _altJavacTask_ManFileMgr;
    private JavacTool _javacTool;
    private volatile StandardJavaFileManager _fm;
    private JavaFileManager _wfm;

    public static ClassSymbols instance(IModule module) {
        ClassSymbols classSymbols = INSTANCES.get(module);
        if (classSymbols == null) {
            INSTANCES.put(module, classSymbols = new ClassSymbols(module));
        }
        return classSymbols;
    }

    private ClassSymbols(IModule module) {
        _module = module;
        _module.getHost().addTypeSystemListenerAsWeakRef(module, new CacheClearer());
        _altJavacTask_PlainFileMgr = LocklessLazyVar.make(() -> {
            init();

            StringWriter errors = new StringWriter();
            BasicJavacTask task = (BasicJavacTask) _javacTool.getTask(errors, _fm, null, makeJavacArgs(), null, null);
            if (errors.getBuffer().length() > 0) {
                // report errors to console
                System.err.println(errors.getBuffer());
            }
            return task;
        });
        _altJavacTask_ManFileMgr = LocklessLazyVar.make(() -> {
            init();
            if (_wfm == null) {
                _wfm = new ManifoldJavaFileManager(_module.getHost(), _fm, null, false);
            }
            StringWriter errors = new StringWriter();
            BasicJavacTask task = (BasicJavacTask) _javacTool.getTask(errors, _wfm, null, makeJavacArgs(), null, null);
            if (_wfm instanceof ManifoldJavaFileManager) {
                if (JreUtil.isJava9orLater()) {
                    // Java 9+ requires a module when resolving a symbol, it's always 'noModule' with this particular java task
                    Stack stack = new Stack();
                    stack.push(ReflectUtil.field(Symtab.instance(task.getContext()), "noModule").get());
                    task.getContext().put(ManifoldJavaFileManager.MODULE_CTX, stack);
                }
                ((ManifoldJavaFileManager) _wfm).setContext(task.getContext());
            }

            if (errors.getBuffer().length() > 0) {
                // report errors to console
                System.err.println(errors.getBuffer());
            }
            return task;
        });
    }

    private List<String> makeJavacArgs() {
        return new ArrayList<String>() {{
            add("-proc:none");
            add("-Xprefer:source");

            // add "--release 8" if compiling say from Java 11 but targeting Java 8 with "--release 8" which runs against the
            // actual Java 8 JRE classes, not the Java 11 ones. Otherwise, there is trouble if we bring in JRE 11 sources here,
            // which have Java 11 features that won't compile with source level 8.
            JavacPlugin javacPlugin = JavacPlugin.instance();
            Options options = javacPlugin == null ? null : Options.instance(javacPlugin.getContext());
            String release = options == null ? null : options.get("--release");
            if (release == null && JreUtil.isJava9orLater() && options != null) // must be *building* with Java 9+
            {
                release = options.get("-target");
                release = release == null ? options.get("--target") : release;
            }
            if ("8".equals(release) || "1.8".equals(release)) {
                add("--release");
                add("8");
            } else {
                add("-source");
                add("1.8");
            }
        }};
    }

    private void init() {
        if (_fm != null) {
            return;
        }

        synchronized (this) {
            if (_fm != null) {
                return;
            }

            _javacTool = JavacTool.create();
            StandardJavaFileManager fm = _javacTool.getStandardFileManager(null, null, StandardCharsets.UTF_8);

            try {
                fm.setLocation(StandardLocation.SOURCE_PATH, _module.getCollectiveSourcePath().stream().map(IResource::toJavaFile).filter(f -> !SourcePathUtil.excludeFromTestPath(f.getAbsolutePath())).collect(Collectors.toList()));
                fm.setLocation(StandardLocation.CLASS_PATH, _module.getCollectiveJavaClassPath().stream().map(IResource::toJavaFile).filter(f -> !SourcePathUtil.excludeFromTestPath(f.getAbsolutePath())).collect(Collectors.toList()));
                if (JreUtil.isJava8()) {
                    fm.setLocation(StandardLocation.PLATFORM_CLASS_PATH, extendBootclasspath(fm.getLocation(StandardLocation.PLATFORM_CLASS_PATH)));
                }
                _fm = fm;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Iterable<? extends File> extendBootclasspath(Iterable<? extends File> existing) {
        if (JavacPlugin.instance() == null) {
            return existing;
        }
        String bootclasspath = JavacPlugin.instance().getBootclasspath();
        if (bootclasspath == null || bootclasspath.isEmpty()) {
            return existing;
        }

        List<File> bootcp = new ArrayList<>();
        existing.forEach(f -> bootcp.add(f));
        String[] split = bootclasspath.split(";");
        for (int i = 0; i < split.length; i++) {
            bootcp.add(i, new File(split[i]));
        }
        return bootcp;
    }

    public BasicJavacTask getJavacTask_PlainFileMgr() {
        return _altJavacTask_PlainFileMgr.get();
    }

    public BasicJavacTask getJavacTask_ManFileMgr() {
        return _altJavacTask_ManFileMgr.get();
    }

    public Pair<Symbol.ClassSymbol, JCTree.JCCompilationUnit> getClassSymbol(BasicJavacTask javacTask, String fqn) {
        return getClassSymbol(javacTask, (TypeProcessor) null, fqn);
    }

    public Pair<Symbol.ClassSymbol, JCTree.JCCompilationUnit> getClassSymbol(BasicJavacTask javacTask, JavaFileManager.Location location, String fqn) {
        return getClassSymbol(javacTask.getContext(), location, fqn);
    }

    public Pair<Symbol.ClassSymbol, JCTree.JCCompilationUnit> getClassSymbol(BasicJavacTask javacTask, Object moduleSymbol, String fqn) {
        return getClassSymbol(javacTask.getContext(), moduleSymbol, fqn);
    }

    public Pair<Symbol.ClassSymbol, JCTree.JCCompilationUnit> getClassSymbol(BasicJavacTask javacTask, TypeProcessor tp, String fqn) {
        Context ctx = tp == null ? javacTask.getContext() : tp.getContext();
        JCTree.JCCompilationUnit cu = tp == null ? null : (JCTree.JCCompilationUnit) tp.getCompilationUnit();
        return getClassSymbol(ctx, cu, fqn);
    }

    private Pair<Symbol.ClassSymbol, JCTree.JCCompilationUnit> getClassSymbol(Context ctx, Object moduleCtx, String fqn) {
        Symbol.ClassSymbol typeElement = IDynamicJdk.instance().getTypeElement(ctx, moduleCtx, fqn);
        if (typeElement == null) {
            // For the case where the class is generated from a type manifold esp. from a IExtensionClassProducer
            return getClassSymbolForProducedClass(fqn, new BasicJavacTask[1]);

//## want this instead, but the typeElement is not complete in this case, investigate this
//      if( JavacPlugin.instance() != null )
//      {
//        typeElement = IDynamicJdk.instance().getTypeElement( JavacPlugin.instance().getContext(), moduleCtx, fqn );
//        typeElement.complete();
//      }
        }

        JavacTrees trees = JavacTrees.instance(ctx);
        TreePath path = trees.getPath(typeElement);
        if (path != null) {
            return new Pair<>(typeElement, (JCTree.JCCompilationUnit) path.getCompilationUnit());
        } else {
            // TreePath is only applicable to a source file;
            // if fqn is not a source file, there is no compilation unit available
            return new Pair<>(typeElement, null);
        }
    }

    public SrcClass makeSrcClassStub(String fqn) {
        return makeSrcClassStub(fqn, null, null);
    }

    public SrcClass makeSrcClassStub(String fqn, JavaFileManager.Location location, DiagnosticListener<JavaFileObject> errorHandler) {
        BasicJavacTask javacTask = /*location != null && JavacPlugin.instance() != null ? JavacPlugin.instance().getJavacTask() :*/ getJavacTask_PlainFileMgr();
        Pair<Symbol.ClassSymbol, JCTree.JCCompilationUnit> pair = getClassSymbol(javacTask, (JavaFileManager.Location) null /*location*/, fqn);
        if (pair == null) {
            //## todo: revisit this, but for now we need to return null to handle inner class extensions
            return null;
//      throw new IllegalStateException( "Failed to find class, '" + fqn + "'" );
        }
        Symbol.ClassSymbol classSymbol = pair.getFirst();
        if (classSymbol == null) {
            // For the case where the class is generated from a type manifold esp. from a IExtensionClassProducer
            return makeSrcClassStubFromProducedClass(fqn, location, errorHandler);
        }

        return SrcClassUtil.instance().makeStub(fqn, classSymbol, pair.getSecond(), getJavacTask_PlainFileMgr(), _module, location, errorHandler);
    }

    private SrcClass makeSrcClassStubFromProducedClass(String fqn, JavaFileManager.Location location, DiagnosticListener<JavaFileObject> errorHandler) {
        BasicJavacTask[] task = new BasicJavacTask[1];
        Pair<Symbol.ClassSymbol, JCTree.JCCompilationUnit> pair = getClassSymbolForProducedClass(fqn, task);
        if (pair == null) {
            throw new NullPointerException("Could not find ClassSymbol for: " + fqn);
        }

        Symbol.ClassSymbol classSymbol = pair.getFirst();

        return SrcClassUtil.instance().makeStub(fqn, classSymbol, pair.getSecond(), task[0], _module, location, errorHandler);
    }

    private Pair<Symbol.ClassSymbol, JCTree.JCCompilationUnit> getClassSymbolForProducedClass(String fqn, BasicJavacTask[] task) {
        StringWriter errors = new StringWriter();

        // need javac with ManifoldJavaFileManager because the produced class must come from manifold
        task[0] = getJavacTask_ManFileMgr();

        Symbol.ClassSymbol e = IDynamicJdk.instance().getTypeElement(task[0].getContext(), null, fqn);

        if (e != null && e.getSimpleName().contentEquals(ManClassUtil.getShortClassName(fqn))) {
            JavacTrees trees = JavacTrees.instance(task[0].getContext());
            TreePath path = trees.getPath(e);
            if (path != null) {
                return new Pair<>(e, (JCTree.JCCompilationUnit) path.getCompilationUnit());
            } else {
                // TreePath is only applicable to a source file;
                // if fqn is not a source file, there is no compilation unit available
                return new Pair<>(e, null);
            }
        }

        StringBuffer errorText = errors.getBuffer();
        if (errorText.length() > 0) {
            throw new RuntimeException("Compile errors:\n" + errorText);
        }

        return null;
    }

    private class CacheClearer implements ITypeSystemListener {
        @Override
        public void refreshedTypes(RefreshRequest request) {
        }

        @Override
        public void refreshed() {
            INSTANCES.remove(_module);
        }
    }
}
