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

package manifold.ext;

import com.sun.tools.javac.tree.TreeTranslator;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import manifold.api.fs.IDirectory;
import manifold.api.fs.IFile;
import manifold.api.gen.TypeNameParser;
import manifold.api.host.IModule;
import manifold.api.host.RefreshRequest;
import manifold.api.type.ContributorKind;
import manifold.api.type.ITypeManifold;
import manifold.api.type.ITypeProcessor;
import manifold.api.type.JavaTypeManifold;
import manifold.api.type.ResourceFileTypeManifold;
import manifold.ext.rt.api.Extension;
import manifold.internal.javac.*;
import manifold.rt.api.util.ManClassUtil;
import manifold.rt.api.util.StreamUtil;
import manifold.util.concurrent.LocklessLazyVar;


import static java.nio.charset.StandardCharsets.UTF_8;
import static manifold.ext.ExtCodeGen.GENERATEDPROXY_;
import static manifold.ext.ExtCodeGen.OF_;
import static manifold.ext.ExtCodeGen.TO_;

/**
 *
 */
public class ExtensionManifold extends JavaTypeManifold<Model> implements ITypeProcessor {
    @SuppressWarnings("WeakerAccess")
    public static final String EXTENSIONS_PACKAGE = "extensions";
    private static final Set<String> FILE_EXTENSIONS = new HashSet<>(Arrays.asList("java", "class"));

    public void init(IModule module) {
        init(module, (fqn, files) -> new Model(fqn, files, this));
    }

    @Override
    public boolean handlesFileExtension(String fileExtension) {
        return FILE_EXTENSIONS.contains(fileExtension.toLowerCase());
    }

    @Override
    public ContributorKind getContributorKind() {
        return ContributorKind.Supplemental;
    }

    @Override
    protected CacheClearer createCacheClearer() {
        return new ExtensionCacheHandler();
    }

    @Override
    public String getTypeNameForFile(String fqn, IFile file) {
        if (fqn.length() > EXTENSIONS_PACKAGE.length() + 2) {
            int iExt = fqn.indexOf(EXTENSIONS_PACKAGE + '.');

            if (iExt >= 0) {
                String extendedType = fqn.substring(iExt + EXTENSIONS_PACKAGE.length() + 1);

                int iDot = extendedType.lastIndexOf('.');
                if (iDot > 0) {
                    return extendedType.substring(0, iDot);
                }
            }
        }
        return null;
    }

    @Override
    public boolean handlesFile(IFile file) {
        Set<String> fqns = getModule().getPathCache().getFqnForFile(file);
        if (fqns == null) {
            return false;
        }

        for (String fqn : fqns) {
            if (fqn.length() > EXTENSIONS_PACKAGE.length() + 2) {
                int iExt = fqn.indexOf(EXTENSIONS_PACKAGE + '.');
                if (iExt >= 0) {
                    String extendedType = fqn.substring(iExt + EXTENSIONS_PACKAGE.length() + 1);

                    int iDot = extendedType.lastIndexOf('.');
                    if (iDot > 0) {
                        try {
                            //## note: this is pretty sloppy science here, but we don't want to parse
                            // java or use asm here i.e., handlesFile() this has to be *fast*.

                            if (file.getExtension().equalsIgnoreCase("java")) {
                                String content = StreamUtil.getContent(new InputStreamReader(file.openInputStream(), UTF_8));
                                return content.contains("@Extension") && content.contains(Extension.class.getPackage().getName());
                            } else // .class file
                            {
                                String content = StreamUtil.getContent(new InputStreamReader(file.openInputStream(), UTF_8));
                                return content.contains(Extension.class.getName().replace('.', '/'));
                            }
                        } catch (IOException e) {
                            // eat
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected Set<String> getAdditionalTypes(String fqnForFile, IFile file) {
        // Generate proxies for interfaces the extension class declares it implements

        Set<String> ifaces = getImplementedInterfaces(file);
        Set<String> proxyFactoryTypes = new HashSet<>();
        for (String iface : ifaces) {
            String proxyFqn = makeProxyFactoryTypeName(file, fqnForFile, iface);
            StaticCompiler.instance().addIProxyFactory(iface, proxyFqn);
            proxyFactoryTypes.add(proxyFqn);
        }
        return proxyFactoryTypes;
    }

    private String makeProxyFactoryTypeName(IFile file, String extendedType, String iface) {
        // we put the proxy in the same package as the extension class from which
        // we deduce the proxy with the following naming scheme:
        //
        //   <extension-packag>.generatedproxy_<extension-name>_Of_<extended-name>_To_<interface-name>
        //

        String proxyPackage = getExtensionPackage(file);
        String extensionName = file.getBaseName();
        String extendedName = extendedType.substring(extendedType.lastIndexOf('.') + 1);
        String interfaceName = iface.replace('.', '_');
        String proxyName = GENERATEDPROXY_ + extensionName + OF_ + extendedName + TO_ + interfaceName;
        return proxyPackage + '.' + proxyName;
    }

    private String getExtensionPackage(IFile file) {
        Set<String> fqnForFile = getModule().getPathCache().getFqnForFile(file);
        for (String fqn : fqnForFile) {
            return ManClassUtil.getPackage(fqn);
        }
        return null;
    }

    private Set<String> getImplementedInterfaces(IFile file) {
        if (!file.getExtension().equalsIgnoreCase("java")) {
            return Collections.emptySet();
        }

        try {
            //## note: this is pretty sloppy science here, but we don't want to parse
            // java or use asm here i.e., this has to be *fast*.

            if (file.getExtension().equalsIgnoreCase("java")) {
                String extensionName = file.getBaseName();
                String content = StreamUtil.getContent(new InputStreamReader(file.openInputStream(), UTF_8));
                String extMarker = "class " + extensionName + " ";
                int extIndex = content.indexOf(extMarker);
                if (extIndex > 0) {
                    int braceIndex = content.indexOf('{', extIndex + extMarker.length());
                    String header = content.substring(extIndex + extMarker.length(), braceIndex);
                    int implementsIndex = header.indexOf("implements ");
                    if (implementsIndex >= 0) {
                        String implementsClause = header.substring(implementsIndex + "implements ".length());
                        implementsClause = implementsClause.trim();
                        // file should be in extension class package, don't need to know the fqn of the iface
                        List<TypeNameParser.Type> types = new TypeNameParser(implementsClause).parseCommaSeparated();
                        return types.stream().map(e -> e.getPlainName()).collect(Collectors.toSet());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Collections.emptySet();
    }

    @Override
    protected Map<String, LocklessLazyVar<Model>> getPeripheralTypes() {
        // Include types extended by dynamically provided extension classes from IExtensionClassProducers

        Map<String, LocklessLazyVar<Model>> map = new HashMap<>();
        for (ITypeManifold tm : getModule().getTypeManifolds()) {
            if (tm instanceof IExtensionClassProducer) {
                for (String extended : ((IExtensionClassProducer) tm).getExtendedTypes()) {
                    map.put(extended, LocklessLazyVar.make(() -> new Model(extended, Collections.emptySet(), this)));
                }
            }
        }
        return map;
    }

    @Override
    public boolean isInnerType(String topLevel, String relativeInner) {
        return isType(topLevel) &&
                (isInnerToPrimaryManifold(topLevel, relativeInner) ||
                        (!isPrimaryManifold(topLevel) && isInnerToJavaClass(topLevel, relativeInner)));
    }

    private boolean isInnerToPrimaryManifold(String topLevel, String relativeInner) {
        Set<ITypeManifold> tms = getModule().findTypeManifoldsFor(topLevel,
                tm -> tm.getContributorKind() == ContributorKind.Primary &&
                        tm instanceof ResourceFileTypeManifold &&
                        ((ResourceFileTypeManifold) tm).isInnerType(topLevel, relativeInner));
        return !tms.isEmpty();
    }

    private boolean isPrimaryManifold(String topLevel) {
        Set<ITypeManifold> tms = getModule().findTypeManifoldsFor(topLevel,
                tm -> tm.getContributorKind() == ContributorKind.Primary);
        return !tms.isEmpty();
    }

    //## todo: This applies only to precompiled Java class files.
    //## todo: Need to move this method to IManifoldHost for different use-cases (class files, javac symbols, and IJ psi)
    private boolean isInnerToJavaClass(String topLevel, String relativeInner) {
        try {
            Class<?> cls = Class.forName(topLevel, false, getModule().getHost().getActualClassLoader());
            for (Class<?> inner : cls.getDeclaredClasses()) {
                if (isInnerClass(inner, relativeInner)) {
                    return true;
                }
            }
        } catch (ClassNotFoundException ignore) {
        }
        String name = topLevel.replace('.', '/') + "$" + relativeInner.replace('.', '$') + ".class";
        return isClassFile(name);
    }

    private boolean isClassFile(String relFileName) {
        for (IDirectory directory : getModule().getJavaClassPath()) {
            boolean isClassFile = findFile(directory, relFileName);
            if (isClassFile) {
                return true;
            }
        }
        return false;
    }

    private boolean findFile(IDirectory directory, String relFileName) {
        IFile descendant = directory.file(relFileName);
        return descendant != null && descendant.exists();
    }

    private boolean isInnerClass(Class<?> cls, String relativeInner) {
        String name;
        String remainder;
        int iDot = relativeInner.indexOf('.');
        if (iDot > 0) {
            name = relativeInner.substring(0, iDot);
            remainder = relativeInner.substring(iDot + 1);
        } else {
            name = relativeInner;
            remainder = null;
        }
        if (cls.getSimpleName().equals(name)) {
            if (remainder != null) {
                for (Class<?> m : cls.getDeclaredClasses()) {
                    if (isInnerClass(m, remainder)) {
                        return true;
                    }
                }
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    protected String contribute(JavaFileManager.Location location, String topLevelFqn, boolean genStubs, String existing, Model model, DiagnosticListener<JavaFileObject> errorHandler) {
        return new ExtCodeGen(location, model, topLevelFqn, genStubs, existing).make(location, errorHandler);
    }

    @Override
    public void process(TypeElement typeElement, TypeProcessor typeProcessor, IssueReporter<JavaFileObject> issueReporter) {
        if (typeElement.getKind() == ElementKind.CLASS ||
                typeElement.getKind() == ElementKind.ENUM ||
                typeElement.getKind().name().equals("RECORD") ||
                typeElement.getKind() == ElementKind.INTERFACE) {
            TreeTranslator visitor = new ExtensionTransformer(this, typeProcessor);
            typeProcessor.getTree().accept(visitor);
        }
    }

    private class ExtensionCacheHandler extends CacheClearer {
        @Override
        public void refreshedTypes(RefreshRequest request) {
            IModule refreshModule = request.module;
            if (refreshModule != null && refreshModule != getModule()) {
                return;
            }

            super.refreshedTypes(request);

            if (request.file == null) {
                return;
            }

            for (ITypeManifold tm : ExtensionManifold.this.getModule().findTypeManifoldsFor(request.file, tm -> tm instanceof IExtensionClassProducer)) {
                for (String extended : ((IExtensionClassProducer) tm).getExtendedTypesForFile(request.file)) {
                    refreshedType(extended, request);
                }
            }
        }

        private void refreshedType(String extended, RefreshRequest request) {
            switch (request.kind) {
                case CREATION:
                    createdType(Collections.emptySet(), extended);
                    break;
                case MODIFICATION:
                    modifiedType(Collections.emptySet(), extended);
                    break;
                case DELETION:
                    deletedType(Collections.emptySet(), extended);
                    break;
            }
        }
    }
}