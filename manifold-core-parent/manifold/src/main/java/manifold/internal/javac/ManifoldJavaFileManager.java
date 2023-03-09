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

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.file.RelativePath;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Name;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import manifold.api.fs.IFile;
import manifold.api.fs.IFileFragment;
import manifold.api.host.IManifoldHost;
import manifold.api.host.IModule;
import manifold.api.host.ITypeSystemListener;
import manifold.api.host.RefreshRequest;
import manifold.api.type.ITypeManifold;
import manifold.api.type.TypeName;
import manifold.internal.host.SimpleModule;
import manifold.rt.api.util.Stack;
import manifold.util.JreUtil;
import manifold.rt.api.util.ManClassUtil;
import manifold.util.ReflectUtil;
import manifold.api.util.cache.FqnCache;
import manifold.api.util.cache.FqnCacheNode;


import static manifold.api.type.ContributorKind.Primary;
import static manifold.api.type.ContributorKind.Supplemental;

/**
 *
 */
class ManifoldJavaFileManager extends JavacFileManagerBridge<JavaFileManager> implements ITypeSystemListener {
    private static final JavaFileObject MISS_FO = new MissFileObject();
    static final Context.Key<Stack> MODULE_CTX = new Context.Key<Stack>() {
    };

    private final IManifoldHost _host;
    private final boolean _fromJavaC;
    private FqnCache<InMemoryClassJavaFileObject> _classFiles;
    private FqnCache<JavaFileObject> _generatedFiles;
    private Context _ctx;
    private int _runtimeMode;

    ManifoldJavaFileManager(IManifoldHost host, JavaFileManager fileManager, Context ctx, boolean fromJavaC) {
        super(fileManager, ctx == null ? ctx = new Context() : ctx);
        _host = host;
        _ctx = ctx;
        _fromJavaC = fromJavaC;
        _classFiles = new FqnCache<>();
        _generatedFiles = new FqnCache<>();
        if (JreUtil.isJava9orLater()) {
            ctx.put(MODULE_CTX, new Stack());
        }
        if (ctx.get(JavaFileManager.class) == null) {
            ctx.put(JavaFileManager.class, fileManager);
        }
        _host.addTypeSystemListenerAsWeakRef(null, this);
    }

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        _ctx = context;
    }

    public IManifoldHost getHost() {
        return _host;
    }

    /**
     * @since 9
     */
    public String inferModuleName(Location location) {
        if (location instanceof ManPatchLocation) {
            String name = ((ManPatchLocation) location).inferModuleName(_ctx);
            if (name != null) {
                return name;
            }
        }
        return super.inferModuleName(location);
    }

    @Override
    public boolean hasLocation(Location location) {
        return !JreUtil.isJava8() && location == ReflectUtil.field(StandardLocation.class, "PATCH_MODULE_PATH").getStatic()
                || super.hasLocation(location);
    }

    /**
     * @since 9
     */
    @Override
    public Location getLocationForModule(Location location, String moduleName) throws IOException {
        Location locationForModule = super.getLocationForModule(location, moduleName);
        if (location == ReflectUtil.field(StandardLocation.class, "PATCH_MODULE_PATH").getStatic()) {
            return new ManPatchModuleLocation(moduleName, locationForModule);
        }
        return locationForModule;
    }

    /**
     * @since 9
     */
    @Override
    public Location getLocationForModule(Location location, JavaFileObject fo) throws IOException {
        if (fo instanceof GeneratedJavaStubFileObject &&
                location == ReflectUtil.field(StandardLocation.class, "PATCH_MODULE_PATH").getStatic()) {
            //System.err.println( "PATCH: " + fo.getName() );
            return new ManPatchLocation((GeneratedJavaStubFileObject) fo);
        }
        return super.getLocationForModule(location, fo);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        if (!okToWriteClassFile(kind, sibling)) {
            InMemoryClassJavaFileObject file = new InMemoryClassJavaFileObject(className, kind);
            if (!(sibling instanceof GeneratedJavaStubFileObject) || ((GeneratedJavaStubFileObject) sibling).isPrimary()) {
                // only retain primary class files e.g., don't keep stubbed class files from extension classes

                _classFiles.add(className, file);
                className = className.replace('$', '.');
                _classFiles.add(className, file);
            }
            return file;
        }
        return super.getJavaFileForOutput(location, className, kind, sibling);
    }

    private boolean okToWriteClassFile(JavaFileObject.Kind kind, FileObject fo) {
        // it's ok to write a type manifold class to disk if we're running javac and the class is not an extended java class

        return !isRuntimeMode() && _fromJavaC &&
                !isIntellijPluginTemporaryFile(kind, fo) &&
                (kind != JavaFileObject.Kind.CLASS ||
                        !(fo instanceof GeneratedJavaStubFileObject) ||
                        (JavacPlugin.instance().isStaticCompile() &&
                                (((GeneratedJavaStubFileObject) fo).isPrimary() || fo.getName().contains("generatedproxy_"))));
    }

    // ManChangedResourceBuilder from IJ plugin
    private boolean isIntellijPluginTemporaryFile(JavaFileObject.Kind kind, FileObject fo) {
        String name = fo == null ? null : fo.getName();
        return name != null && name.contains("_Manifold_Temp_Main_");
    }

    public InMemoryClassJavaFileObject findCompiledFile(String fqn) {
        return _classFiles.get(fqn);
    }

    public JavaFileObject getSourceFileForInput(Location location, String fqn, JavaFileObject.Kind kind, DiagnosticListener<JavaFileObject> errorHandler) {
        try {
            JavaFileObject file = super.getJavaFileForInput(location, fqn, kind);
            if (file != null) {
                return file;
            }
        } catch (IOException ignore) {
        }

        return findGeneratedFile(fqn.replace('$', '.'), location, getHost().getSingleModule(), errorHandler);
    }

    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        Iterable<JavaFileObject> list = super.list(
                location instanceof ManPatchModuleLocation ? ((ManPatchModuleLocation) location).getLocationForModule() : location,
                packageName,
                new HashSet<>(kinds), // make a copy because this super call likes to remove the SOURCE kind in a multi-module project
                recurse);
        if (kinds.contains(JavaFileObject.Kind.SOURCE) && (location == StandardLocation.SOURCE_PATH || location == StandardLocation.CLASS_PATH || location instanceof ManPatchModuleLocation)) {
            SimpleModule compilingModule = (SimpleModule) getHost().getSingleModule(); // note, a null module indicates JavacPlugin is not initialized at ENTER phase yet
            Set<TypeName> children = compilingModule == null ? null : compilingModule.getChildrenOfNamespace(packageName);
            if (children == null || children.isEmpty()) {
                return list;
            }

            ArrayList<JavaFileObject> newList = new ArrayList<>();
            list.forEach(newList::add);
            Set<String> names = makeNames(list);

            Iterable<JavaFileObject> patchableFiles = null;
            if (location instanceof ManPatchModuleLocation) {
                Set<JavaFileObject.Kind> classesAndSource = new HashSet<>(Arrays.asList(JavaFileObject.Kind.CLASS, JavaFileObject.Kind.SOURCE));
                Location modulePatchLocation = makeModuleLocation((ManPatchModuleLocation) location);
                if (modulePatchLocation == null) {
                    return list;
                }
                // Get a list of class files from the patch module location; these are patch candidates
                patchableFiles = super.list(modulePatchLocation, packageName, classesAndSource, recurse);
            }

            for (TypeName tn : children) {
                if (names.contains(ManClassUtil.getShortClassName(tn.name))) {
                    continue;
                }

                if (tn.kind == TypeName.Kind.NAMESPACE) {
                    if (recurse) {
                        //noinspection ConstantConditions
                        Iterable<JavaFileObject> sublist = list(location, tn.name, kinds, recurse);
                        sublist.forEach(newList::add);
                    }
                } else {
                    IssueReporter<JavaFileObject> issueReporter = new IssueReporter<>(() -> _ctx);
                    String fqn = tn.name.replace('$', '.');
                    JavaFileObject file = findGeneratedFile(fqn, location, tn.getModule(), issueReporter);
                    if (file != null && isSourceOk(file, location) && isCorrectModule(tn.getModule(), location, patchableFiles, file, fqn)) {
                        newList.add(file);
                    }
                }
            }
            list = newList;
        }
        return list;
    }

    private boolean isSourceOk(JavaFileObject file, Location location) {
        JavacPlugin javacPlugin = JavacPlugin.instance();
        boolean isModular = javacPlugin != null && JreUtil.isJava9Modular_compiler(javacPlugin.getContext());
        return location != StandardLocation.CLASS_PATH ||
                !isModular ||
                // allow extension classes source to copy to CLASS_PATH
                file instanceof GeneratedJavaStubFileObject && !((GeneratedJavaStubFileObject) file).isPrimary();
    }

    private boolean isCorrectModule(IModule module, Location location, Iterable<JavaFileObject> patchableFiles, JavaFileObject file, String fqn) {
        if (!(location instanceof ManPatchModuleLocation)) {
            // NOT a ManPatchModuleLocation means NOT an extended class...
            // and also NOT a class in a dependency that is not compiled, but
            // needs to be dynamically compiled from the dependent module

            if (!JreUtil.isJava9Modular_compiler(_ctx)) {
                return true;
            }

            // true if type is not exclusively an extended type
            //noinspection unchecked
            Set<ITypeManifold> typeManifoldsFor = getHost().getSingleModule().findTypeManifoldsFor(fqn, tm -> tm.getContributorKind() == Primary);
            if (typeManifoldsFor.isEmpty() && fqn.contains(".generatedproxy_")) {
                // handle auto-generated proxies for structural interface impl by extension class
                typeManifoldsFor = getHost().getSingleModule().findTypeManifoldsFor(fqn, tm -> tm.getContributorKind() == Supplemental);
            }
            return !typeManifoldsFor.isEmpty();
        }

        Set<ITypeManifold> tms = module.findTypeManifoldsFor(fqn);
        if (tms.stream().anyMatch(tm -> tm.getContributorKind() == Primary)) {
            // type is from a dependency module, but was not compiled there, yet is
            // referenced outside the module and needs to be dynamically compiled
            return true;
        }

        if (patchableFiles == null) {
            return true;
        }

        String cname = inferBinaryName(location, file);

        for (JavaFileObject f : patchableFiles) {
            String name = inferBinaryName(location, f);

            if (cname.equals(name)) {
                // existing type is extended
                return true;
            }
        }

        return false;
    }

    private Location makeModuleLocation(ManPatchModuleLocation location) {
        // Module module = Modules.instance( _ctx ).getObservableModule( Names.instance( _ctx ).fromString( location.getName() ) );
        // return module.classLocation;

        Symbol moduleElement = (Symbol) ReflectUtil.method(Symtab.instance(_ctx), "getModule", Name.class)
                .invoke(Names.instance(_ctx).fromString(location.getName()));
        if (moduleElement == null) {
            return null;
        }
        return (Location) ReflectUtil.field(moduleElement, "classLocation").get();
    }

    public JavaFileObject findGeneratedFile(String fqn, Location location, IModule module, DiagnosticListener<JavaFileObject> errorHandler) {
        FqnCacheNode<JavaFileObject> node = _generatedFiles.getNode(fqn);
        if (node != null) {
            JavaFileObject fo = node.getUserData();
            // note userdata can be null in the case where an innerclass is loaded before the enclosing
            if (fo != null) {
                return fo == MISS_FO ? null : fo;
            }
        }

        if (isFilteredFromIncrementalCompilation(fqn)) {
            return null;
        }

        JavaFileObject fo;
        pushLocation(location);
        try {
            fo = module.produceFile(fqn, location, errorHandler);
        } finally {
            popLocation(location);
        }

        // note we cache even if file is null, fqn cache is also a miss cache
        _generatedFiles.add(fqn, fo == null ? MISS_FO : fo);

        return fo;
    }

    private void pushLocation(JavaFileManager.Location location) {
        if (JavacPlugin.instance() == null || !JreUtil.isJava9orLater()) {
            return;
        }

        if (location == null) {
            throw new IllegalStateException("null Location");
        }

        /*Symbol.ModuleSymbol*/
        Object module = inferModule(location);
        _ctx.get(MODULE_CTX).push(module);
    }

    private void popLocation(JavaFileManager.Location location) {
        if (JavacPlugin.instance() == null || !JreUtil.isJava9orLater()) {
            return;
        }

        Object top = _ctx.get(MODULE_CTX).pop();
        if (top != inferModule(location)) {
            throw new IllegalStateException("stack not balanced");
        }
    }

    private /*Symbol.ModuleSymbol*/ Object inferModule(JavaFileManager.Location location) {
        /*Modules*/
        Object modules = ReflectUtil.method("com.sun.tools.javac.comp.Modules", "instance", Context.class).invokeStatic(_ctx);
        Object defaultModule = ReflectUtil.method(modules, "getDefaultModule").invoke();
        if (defaultModule == ReflectUtil.field(Symtab.instance(_ctx), "noModule").get()) {
            return defaultModule;
        }
        Set<?>/*<Symbol.ModuleSymbol>*/ rootModules = (Set<?>) ReflectUtil.method(modules, "getRootModules").invoke();

        Object moduleSym = null;
        if (location instanceof ManPatchLocation) {
            String moduleName = ((ManPatchLocation) location).inferModuleName(_ctx);
            if (moduleName != null) {
                Name name = Names.instance(_ctx).fromString(moduleName);
                moduleSym = ReflectUtil.method(modules, "getObservableModule", Name.class).invoke(name);
                if (moduleSym == null) {
                    throw new IllegalStateException("null module symbol for module: '" + moduleName + "'");
                }
            }
        }

        if (rootModules.size() == 1) {
            return rootModules.iterator().next();
        }

        throw new IllegalStateException("no module inferred");
    }

    private boolean isFilteredFromIncrementalCompilation(String fqn) {
        JavacPlugin javacPlugin = JavacPlugin.instance();
        if (javacPlugin == null || !javacPlugin.isIncremental()) {
            // not performing incremental compilation, compile everything
            return false;
        }

        // changed files indicates incremental compilation, thus if fqn is not present in changes, use .class file
        List<File> changedFiles = getChangedResourceFiles();
        IManifoldHost host = getHost();
        Set<IFile> changes = changedFiles.stream().map((File f) -> host.getFileSystem().getIFile(f))
                .collect(Collectors.toSet());
        for (ITypeManifold tm : host.getSingleModule().getTypeManifolds()) {
            if ((tm.getContributorKind() == Supplemental || !tm.isFileBacked()) && tm.isType(fqn)) {
                // do not filter extension classes, they must be augmented in memory (the extended .class file does not have extensions)
                return false;
            }

            for (IFile file : tm.findFilesForType(fqn)) {
                if (file instanceof IFileFragment) {
                    // do not filter fragments, they are a fragment of a .java file
                    return false;
                }
            }

            for (IFile file : changes) {
                Set<String> types = Arrays.stream(tm.getTypesForFile(file)).collect(Collectors.toSet());
                if (types.contains(fqn)) {
                    //## todo: cache the types for changed files so we don't have to recompute them for each call to this method
                    // the resource file changed, recompile the type[s]
                    return false;
                }
            }
        }

        // no files changed corresponding with the type, do not compile it, instead use the existing .class file
        return true;
    }

    public static List<File> getChangedResourceFiles() {
        List<File> changedFiles = Collections.emptyList();
        Class<?> type = ReflectUtil.type("manifold.ij.jps.IjChangedResourceFiles");
        if (type != null) {
            changedFiles = (List<File>) ReflectUtil.method(type, "getChangedFiles").invokeStatic();
        }
        return changedFiles;
    }

    private Set<String> makeNames(Iterable<JavaFileObject> list) {
        HashSet<String> set = new HashSet<>();
        for (JavaFileObject file : list) {
            String name = file.getName();
            if (name.endsWith(".java")) {
                set.add(name.substring(name.lastIndexOf(File.separatorChar) + 1, name.lastIndexOf('.')));
            }
        }
        return set;
    }

    public void remove(String fqn) {
        _classFiles.remove(fqn);
    }

    @Override
    public void refreshedTypes(RefreshRequest request) {
        switch (request.kind) {
            case CREATION:
            case MODIFICATION:
            case DELETION:
                // Remove all affected types for any refresh kind.
                // Note we remove types for CREATION request because we could have cached misses to the type name.
                _classFiles.remove(request.types);
                _generatedFiles.remove(request.types);
                break;
        }
    }

    @Override
    public void refreshed() {
        _classFiles = new FqnCache<>();
    }

    public Collection<InMemoryClassJavaFileObject> getCompiledFiles() {
        HashSet<InMemoryClassJavaFileObject> files = new HashSet<>();
        _classFiles.visitDepthFirst(
                o ->
                {
                    if (o != null) {
                        files.add(o);
                    }
                    return true;
                });
        return files;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject fileObj) {
        if (fileObj instanceof GeneratedJavaStubFileObject) {
            return removeExtension(fileObj.getName()).replace(File.separatorChar, '.').replace('/', '.');
        }

        if (fileObj instanceof SourceJavaFileObject) {
            return ((SourceJavaFileObject) fileObj).inferBinaryName(location);
        }

        if (location instanceof ManPatchModuleLocation) {
            if (fileObj.getClass().getSimpleName().equals("DirectoryFileObject")) {
                RelativePath relativePath = (RelativePath) ReflectUtil.field(fileObj, "relativePath").get();
                return removeExtension(relativePath.getPath()).replace(File.separatorChar, '.').replace('/', '.');
            } else if (fileObj.getClass().getSimpleName().equals("SigJavaFileObject")) {
                // Since Java 10 javac uses .sig files...
                FileObject fileObject = (FileObject) ReflectUtil.field(fileObj, "fileObject").get();
                return fileObject instanceof JavaFileObject ? inferBinaryName(location, (JavaFileObject) fileObject) : null;
            } else if (fileObj.getClass().getSimpleName().equals("JarFileObject")) {
                String relativePath = ReflectUtil.method(fileObj, "getPath").invoke().toString();
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                return removeExtension(relativePath).replace(File.separatorChar, '.').replace('/', '.');
            }
        }
        return super.inferBinaryName(location, fileObj);
    }

    @Override
    public boolean isSameFile(FileObject file1, FileObject file2) {
        if (file1 instanceof GeneratedJavaStubFileObject || file2 instanceof GeneratedJavaStubFileObject) {
            return file1.equals(file2);
        }
        return super.isSameFile(file1, file2);
    }

    private static String removeExtension(String fileName) {
        int iDot = fileName.lastIndexOf(".");
        return iDot == -1 ? fileName : fileName.substring(0, iDot);
    }

    public int pushRuntimeMode() {
        return _runtimeMode++;
    }

    public void popRuntimeMode(int check) {
        if (--_runtimeMode != check) {
            throw new IllegalStateException("runtime mode unbalanced");
        }
    }

    public boolean isRuntimeMode() {
        return _runtimeMode > 0;
    }
}
