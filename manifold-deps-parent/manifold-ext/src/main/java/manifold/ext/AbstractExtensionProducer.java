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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import manifold.api.fs.IFile;
import manifold.api.host.IModule;
import manifold.api.host.RefreshKind;
import manifold.api.host.RefreshRequest;
import manifold.api.type.IModel;
import manifold.api.type.JavaTypeManifold;
import manifold.api.util.cache.FqnCache;
import manifold.util.concurrent.LocklessLazyVar;

/**
 * An abstraction for a type manifold that produces Extension Classes to be processed by the {@link ExtensionManifold}.
 * See ExtensionProducerSampleTypeManifold as a reference implementation.
 */
public abstract class AbstractExtensionProducer<M extends IModel> extends JavaTypeManifold<M> implements IExtensionClassProducer {
    private final LocklessLazyVar<Map<String, LocklessLazyVar<M>>> _extensionToModel = LocklessLazyVar.make(this::buildCache);
    private final LocklessLazyVar<Map<String, Set<IFile>>> _extensionToFiles = LocklessLazyVar.make(HashMap::new);


    @Override
    public void init(IModule moduleComponent) {
        super.init(moduleComponent, this::createModel);
    }

    /**
     * @return The file extension of the resource files this type manifold processes
     */
    protected abstract String getFileExt();

    /**
     * @return A new model for the qualified {@code extensionClassName} using {@code files}
     */
    protected abstract M createModel(String extensionClassName, Set<IFile> files);

    /**
     * @return The qualified extension class name corresponding with {@code extendedClassFqn}
     */
    protected abstract String makeExtensionClassName(String extendedClassFqn);

    /**
     * @return The qualified extended class name derived from {@code extendedClassFqn}
     */
    protected abstract String deriveExtendedClassFrom(String extensionClassFqn);

    /**
     * The type name[s] produced from a file from an extension producer are the names of the extension classes
     */
    @Override
    public String getTypeNameForFile(String defaultFqn, IFile file) {
        return null;
    }

    /**
     * Overridden because the file's fqn isn't really a type for this type manifold,
     * only the extension classes derived from the files are types this manifold produces.
     */

    @Override
    public String findTopLevelFqn(String fqn) {
        //noinspection ConstantConditions
        if (_extensionToModel.get().containsKey(fqn)) {
            return fqn;
        }
        return null;
    }

    private Map<String, LocklessLazyVar<M>> buildCache() {
        Map<String, LocklessLazyVar<M>> extensionToModel = new HashMap<>();
        FqnCache<IFile> extensionCache = getModule().getPathCache().getExtensionCache(getFileExt());
        Set<String> files = extensionCache.getFqns();
        for (String fileFqn : files) {
            IFile file = extensionCache.get(fileFqn);
            addFile(extensionToModel, file);
        }
        return extensionToModel;
    }

    private void addFile(Map<String, LocklessLazyVar<M>> extensionToModel, IFile file) {
        Set<String> extendedTypes = getExtendedTypes(file);
        for (String extended : extendedTypes) {
            String extension = makeExtensionClassName(extended);
            //noinspection ConstantConditions
            Set<IFile> files = _extensionToFiles.get().computeIfAbsent(extension, e -> new HashSet<>());
            files.add(file);
            LocklessLazyVar<M> lazyModel = extensionToModel.putIfAbsent(extension, LocklessLazyVar.make(() -> createModel(extension, files)));
            if (lazyModel != null) {
                Objects.requireNonNull(lazyModel.get()).addFile(file);
            }
        }
    }

    private void removeFile(IFile file) {
        for (String extension : getTypesForFile(file)) {
            //noinspection ConstantConditions
            _extensionToModel.get().remove(extension);
            //noinspection ConstantConditions
            _extensionToFiles.get().get(extension).remove(file);
        }
    }

    @Override
    protected CacheClearer createCacheClearer() {
        return new MyCacheClearer();
    }

    @Override
    public RefreshKind refreshedFile(IFile file, String[] types, RefreshKind kind) {
        _extensionToModel.clear();
        return kind;
    }

    @Override
    public boolean handlesFileExtension(String fileExtension) {
        return fileExtension.equalsIgnoreCase(getFileExt());
    }

    @Override
    protected Map<String, LocklessLazyVar<M>> getPeripheralTypes() {
        return _extensionToModel.get();
    }

    @Override
    public boolean isInnerType(String topLevelFqn, String relativeInner) {
        return false;
    }

    @Override
    public boolean isExtendedType(String fqn) {
        String extension = makeExtensionClassName(fqn);
        //noinspection ConstantConditions
        return _extensionToModel.get().containsKey(extension);
    }

    @Override
    public Set<String> getExtensionClasses(String extendedType) {
        return isExtendedType(extendedType)
                ? getExtensions(extendedType)
                : Collections.emptySet();
    }

    private Set<String> getExtensions(String extendedType) {
        String extension = makeExtensionClassName(extendedType);
        //noinspection ConstantConditions
        if (_extensionToModel.get().containsKey(extension)) {
            return Collections.singleton(extension);
        }
        return Collections.emptySet();
    }

    @Override
    public Set<String> getExtendedTypes() {
        //noinspection ConstantConditions
        return _extensionToModel.get()
                .keySet().stream()
                .map(this::deriveExtendedClassFrom)
                .collect(Collectors.toSet());
    }

    @Override
    public String[] getTypesForFile(IFile file) {
        if (!handlesFile(file)) {
            return new String[0];
        }

        Set<String> types = new HashSet<>();
        for (Map.Entry<String, LocklessLazyVar<M>> entry :
                Objects.requireNonNull(_extensionToModel.get()).entrySet()) {
            String extension = entry.getKey();
            M model = Objects.requireNonNull(entry.getValue().get());
            if (model.getFiles().contains(file)) {
                types.add(extension);
            }
        }
        return types.toArray(new String[types.size()]);
    }

    @Override
    public Set<String> getExtendedTypesForFile(IFile file) {
        if (!handlesFile(file)) {
            return Collections.emptySet();
        }

        Set<String> types = new HashSet<>();
        for (Map.Entry<String, LocklessLazyVar<M>> entry :
                Objects.requireNonNull(_extensionToModel.get()).entrySet()) {
            String extension = entry.getKey();
            M model = Objects.requireNonNull(entry.getValue().get());
            if (model.getFiles().contains(file)) {
                types.add(deriveExtendedClassFrom(extension));
            }
        }
        return types;
    }

    protected abstract Set<String> getExtendedTypes(IFile file);

    private class MyCacheClearer extends CacheClearer {
        @Override
        public void preRefresh(RefreshRequest request) {
            switch (request.kind) {
                case CREATION:
                    addFile(_extensionToModel.get(), request.file);
                    break;
                case MODIFICATION:
                    removeFile(request.file);
                    addFile(_extensionToModel.get(), request.file);
                    break;
            }
        }

        @Override
        public void postRefresh(RefreshRequest request) {
            switch (request.kind) {
                case DELETION:
                    removeFile(request.file);
                    break;
            }
        }
    }
}