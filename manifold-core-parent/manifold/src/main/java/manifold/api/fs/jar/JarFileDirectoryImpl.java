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

package manifold.api.fs.jar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import manifold.api.fs.IDirectory;
import manifold.api.fs.IDirectoryUtil;
import manifold.api.fs.IFile;
import manifold.api.fs.IFileSystem;
import manifold.api.fs.IResource;
import manifold.api.fs.ResourcePath;

public class JarFileDirectoryImpl implements IJarFileDirectory {
    private IFileSystem _fileSystem;
    private File _file;
    private JarFile _jarFile;
    private Map<String, IResource> _resources;
    private List<IDirectory> _childDirs;
    private List<IFile> _childFiles;
    private String _name;
    private IDirectory _parent;
    private URI _uri;
    private ResourcePath _path;

    public JarFileDirectoryImpl(IFileSystem fileSystem, File file) {
        _fileSystem = fileSystem;
        _resources = new HashMap<>();
        _childFiles = new ArrayList<>();
        _childDirs = new ArrayList<>();
        _file = file;

        if (file.exists()) {
            try {
                _jarFile = new JarFile(file);
                Enumeration<JarEntry> entries = _jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    processJarEntry(e);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public IFileSystem getFileSystem() {
        return _fileSystem;
    }

    private void processJarEntry(JarEntry e) {
        List<String> pathComponents = IDirectoryUtil.splitPath(e.getName());
        if (pathComponents.isEmpty()) {
            return;
        }
        if (pathComponents.size() == 1) {
            String name = pathComponents.get(0);
            if (e.isDirectory()) {
                JarEntryDirectoryImpl resource = getOrCreateDirectory(name);
                resource.setEntry(e);
            } else {
                try {
                    JarEntryFileImpl resource = getOrCreateFile(name);
                    resource.setEntry(e);
                } catch (UnsupportedOperationException uoe) {
                    // Better to support this case with a message than to blow up with an exception.
                    // For instance, an Uber Jar file sometimes results in an odd configuration having both a META-INF directory
                    // _and_ file.
                    System.err.println(uoe.getMessage());
                }
            }
        } else {
            JarEntryDirectoryImpl parentDirectory = getOrCreateDirectory(pathComponents.get(0));
            for (int i = 1; i < pathComponents.size() - 1; i++) {
                parentDirectory = parentDirectory.getOrCreateDirectory(pathComponents.get(i));
            }

            if (e.isDirectory()) {
                JarEntryDirectoryImpl leafDir = parentDirectory.getOrCreateDirectory(pathComponents.get(pathComponents.size() - 1));
                leafDir.setEntry(e);
            } else {
                JarEntryFileImpl leafFile = parentDirectory.getOrCreateFile(pathComponents.get(pathComponents.size() - 1));
                leafFile.setEntry(e);
            }
        }
    }

    public InputStream getInputStream(JarEntry entry) throws IOException {
        return _jarFile.getInputStream(entry);
    }

    // IJarFileDirectory methods

    @Override
    public JarEntryDirectoryImpl getOrCreateDirectory(String relativeName) {
        IResource resource = _resources.get(relativeName);
        if (resource instanceof IFile) {
            throw new UnsupportedOperationException("The requested resource " + relativeName + " is now being accessed as a directory, but was previously accessed as a file.");
        }
        JarEntryDirectoryImpl result = (JarEntryDirectoryImpl) resource;
        if (result == null) {
            result = new JarEntryDirectoryImpl(getFileSystem(), relativeName, this, this);
            _resources.put(relativeName, result);
            _childDirs.add(result);
        }
        return result;
    }

    @Override
    public JarEntryFileImpl getOrCreateFile(String relativeName) {
        IResource resource = _resources.get(relativeName);
        if (resource instanceof IDirectory) {
            throw new UnsupportedOperationException(
                    "Unsupported: The requested resource '" + relativeName + "' from '" + getName() + "'" +
                            " is now being accessed as a file, but was previously accessed as a directory.");
        }
        JarEntryFileImpl result = (JarEntryFileImpl) resource;
        if (result == null) {
            result = new JarEntryFileImpl(getFileSystem(), relativeName, this, this);
            _resources.put(relativeName, result);
            _childFiles.add(result);
        }
        return result;
    }

    // IDirectory methods

    @Override
    public IDirectory dir(String relativePath) {
        return IDirectoryUtil.dir(this, relativePath);
    }

    @Override
    public IFile file(String path) {
        return IDirectoryUtil.file(this, path);
    }

    @Override
    public boolean mkdir() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<? extends IDirectory> listDirs() {
        List<IDirectory> results = new ArrayList<>();
        for (IDirectory child : _childDirs) {
            if (child.exists()) {
                results.add(child);
            }
        }
        return results;
    }

    @Override
    public List<? extends IFile> listFiles() {
        List<IFile> results = new ArrayList<>();
        for (IFile child : _childFiles) {
            if (child.exists()) {
                results.add(child);
            }
        }
        return results;
    }

    @Override
    public String relativePath(IResource resource) {
        return IDirectoryUtil.relativePath(this, resource);
    }

    @Override
    public IDirectory getParent() {
        if (_parent == null) {
            File parentFile = _file.getParentFile();
            if (parentFile != null) {
                _parent = getFileSystem().getIDirectory(parentFile);
            } else {
                return null;
            }
        }
        return _parent;
    }

    @Override
    public String getName() {
        return _name == null ? _name = _file.getName() : _name;
    }

    @Override
    public boolean exists() {
        return _file.exists();
    }

    @Override
    public boolean delete() {
        return _file.delete();
    }

    @Override
    public URI toURI() {
        return _uri == null ? _uri = _file.toURI() : _uri;
    }

    @Override
    public ResourcePath getPath() {
        return _path == null ? _path = ResourcePath.parse(_file.getAbsolutePath()) : _path;
    }

    @Override
    public boolean isChildOf(IDirectory dir) {
        return dir.equals(getParent());
    }

    @Override
    public boolean isDescendantOf(IDirectory dir) {
        return dir.getPath().isDescendant(getPath());
    }

    @Override
    public File toJavaFile() {
        return _file;
    }

    public JarFile getJarFile() {
        return _jarFile;
    }

    @Override
    public boolean isJavaFile() {
        return true;
    }

    @Override
    public boolean isInJar() {
        return true;
    }

    @Override
    public boolean create() {
        return false;
    }

    @Override
    public String toString() {
        return toJavaFile().getPath();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof JarFileDirectoryImpl) {
            return getPath().equals(((JarFileDirectoryImpl) obj).getPath());
        } else {
            return false;
        }
    }

    @Override
    public void clearCaches() {
    }

    @Override
    public boolean hasChildFile(String path) {
        IFile childFile = file(path);
        return childFile != null && childFile.exists();
    }

    @Override
    public boolean isAdditional() {
        return false;
    }
}
