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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.jar.JarEntry;

import manifold.api.fs.IDirectory;
import manifold.api.fs.IFileSystem;
import manifold.api.fs.IResource;
import manifold.api.fs.ResourcePath;

public abstract class JarEntryResourceImpl implements IResource {
    private IFileSystem _fs;
    JarEntry _entry;
    protected IJarFileDirectory _parent;
    JarFileDirectoryImpl _jarFile;
    protected String _name;
    private boolean _exists;
    private ResourcePath _path;
    private URI _uri;

    JarEntryResourceImpl(IFileSystem fs, String name, IJarFileDirectory parent, JarFileDirectoryImpl jarFile) {
        _fs = fs;
        _name = name;
        _parent = parent;
        _jarFile = jarFile;
    }

    public IFileSystem getFileSystem() {
        return _fs;
    }

    public void setEntry(JarEntry entry) {
        _entry = entry;
        setExists();
    }

    private void setExists() {
        _exists = true;
        if (getParent() instanceof JarEntryResourceImpl) {
            ((JarEntryResourceImpl) getParent()).setExists();
        }
    }

    @Override
    public IDirectory getParent() {
        return _parent;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean exists() {
        return _exists;
    }

    @Override
    public boolean delete() {
        throw new UnsupportedOperationException();
    }

    @Override
    public URI toURI() {
        if (_uri == null) {
            try {
                _uri = new URI("jar:" + _jarFile.toURI().toString() + "!/" + getEntryName().replace(" ", "%20"));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return _uri;
    }

    private String getEntryName() {
        if (_entry != null) {
            return _entry.getName();
        } else {
            String result = _name;
            IDirectory parent = _parent;
            while (!(parent instanceof JarFileDirectoryImpl)) {
                result = parent.getName() + "/" + result;
                parent = parent.getParent();
            }
            return result;
        }
    }

    @Override
    public ResourcePath getPath() {
        return _path == null ? _path = _parent.getPath().join(_name) : _path;
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
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isJavaFile() {
        return false;
    }

    @Override
    public String toString() {
        return getPath().toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof JarEntryResourceImpl) {
            return getPath().equals(((JarEntryResourceImpl) obj).getPath());
        } else {
            return false;
        }
    }

    @Override
    public boolean create() {
        return false;
    }

    @Override
    public boolean isInJar() {
        return true;
    }
}
