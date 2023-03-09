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

package manifold.api.fs.def;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import manifold.api.fs.IDirectory;
import manifold.api.fs.IDirectoryUtil;
import manifold.api.fs.IFile;
import manifold.api.fs.IFileSystem;
import manifold.api.fs.IResource;

public class JavaDirectoryImpl extends JavaResourceImpl implements IDirectory {
    private FileRetrievalStrategy _fileRetrievalStrategy;

    public JavaDirectoryImpl(IFileSystem fileSystem, File file, IFileSystem.CachingMode cachingMode) {
        super(fileSystem, file);
        setCachingMode(cachingMode);
    }

    public void setCachingMode(IFileSystem.CachingMode cachingMode) {
        switch (cachingMode) {
            case NO_CACHING:
                _fileRetrievalStrategy = new UncachedFileRetrievalStrategy();
                break;
            case CHECK_TIMESTAMPS:
                _fileRetrievalStrategy = new TimestampBasedCachingFileRetrievalStrategy();
                break;
            case FUZZY_TIMESTAMPS:
                _fileRetrievalStrategy = new FuzzyTimestampCachingFileRetrievalStrategy();
                break;
            case FULL_CACHING:
                _fileRetrievalStrategy = new FullyCachedFileRetrievalStrategy();
                break;
            default:
                throw new IllegalStateException("Unrecognized caching mode " + cachingMode);
        }
    }

    @Override
    public void clearCaches() {
        if (_fileRetrievalStrategy instanceof CachingFileRetrievalStrategy) {
            getFileSystem().getLock().lock();
            try {
                ((CachingFileRetrievalStrategy) _fileRetrievalStrategy).clearCache();
            } finally {
                getFileSystem().getLock().unlock();
            }
        }
    }

    @Override
    public IDirectory dir(String relativePath) {
//    try {
        File subDir = new File(this._file, relativePath)/*.getCanonicalFile()*/;
        return getFileSystem().getIDirectory(subDir);
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
    }

    @Override
    public IFile file(String path) {
//    try {
        File subFile = new File(this._file, path)/*.getCanonicalFile()*/;
        return getFileSystem().getIFile(subFile);
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
    }

    @Override
    public boolean mkdir() throws IOException {
        return _file.mkdir();
    }

    @Override
    public List<? extends IDirectory> listDirs() {
        return _fileRetrievalStrategy.listDirs();
    }

    @Override
    public List<? extends IFile> listFiles() {
        return _fileRetrievalStrategy.listFiles();
    }

    @Override
    public String relativePath(IResource resource) {
        return IDirectoryUtil.relativePath(this, resource);
    }

    @Override
    public boolean exists() {
        return _file.isDirectory();
    }

    @Override
    public boolean hasChildFile(String path) {
        if (_fileRetrievalStrategy instanceof FullyCachedFileRetrievalStrategy && path.indexOf('/') == -1 && path.indexOf('\\') == -1) {
            for (IFile file : listFiles()) {
                if (file.getName().equals(path)) {
                    return true;
                }
            }

            return false;
        } else {
            IFile childFile = file(path);
            return childFile != null && childFile.exists();
        }
    }

    @Override
    public boolean isAdditional() {
        return false;
    }

    private interface FileRetrievalStrategy {
        List<? extends IDirectory> listDirs();

        List<? extends IFile> listFiles();
    }

    private class UncachedFileRetrievalStrategy implements FileRetrievalStrategy {
        @Override
        public List<? extends IDirectory> listDirs() {
            List<IDirectory> results = new ArrayList<IDirectory>();
            File[] files = _file.listFiles();
            if (files != null) {
                for (File f : _file.listFiles()) {
                    if (FileSystemImpl.isDirectory(f)) {
                        results.add(getFileSystem().getIDirectory(f));
                    }
                }
            }
            return results;
        }

        @Override
        public List<? extends IFile> listFiles() {
            List<IFile> results = new ArrayList<IFile>();
            File[] files = _file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!FileSystemImpl.isDirectory(f)) {
                        results.add(getFileSystem().getIFile(f));
                    }
                }
            }
            return results;
        }
    }

    private abstract class CachingFileRetrievalStrategy implements FileRetrievalStrategy {
        protected List<IDirectory> _directories;
        protected List<IFile> _files;

        public void clearCache() {
            // This should always be called with the CACHED_FILE_SYSTEM_LOCK monitor already acquired
            _directories = null;
            _files = null;
        }

        @Override
        public List<IDirectory> listDirs() {
            getFileSystem().getLock().lock();
            try {
                refreshIfNecessary();
                return _directories;
            } finally {
                getFileSystem().getLock().unlock();
            }
        }

        @Override
        public List<IFile> listFiles() {
            getFileSystem().getLock().lock();
            try {
                refreshIfNecessary();
                return _files;
            } finally {
                getFileSystem().getLock().unlock();
            }
        }

        protected void refreshInfo() {
            _files = new ArrayList<IFile>();
            _directories = new ArrayList<IDirectory>();
            File javaFile = toJavaFile();
            maybeSetTimestamp(javaFile);

            File[] files = javaFile.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (FileSystemImpl.isDirectory(f)) {
                        _directories.add(getFileSystem().getIDirectory(f));
                    } else {
                        _files.add(getFileSystem().getIFile(f));
                    }
                }
            }

            if (_directories.isEmpty()) {
                _directories = Collections.emptyList();
            } else {
                ((ArrayList) _directories).trimToSize();
            }

            if (_files.isEmpty()) {
                _files = Collections.emptyList();
            } else {
                ((ArrayList) _files).trimToSize();
            }
        }

        protected abstract void refreshIfNecessary();

        protected abstract void maybeSetTimestamp(File javaFile);
    }

    private class TimestampBasedCachingFileRetrievalStrategy extends CachingFileRetrievalStrategy {
        private long _lastTimestamp;

        public void clearCache() {
            // This should always be called with the CACHED_FILE_SYSTEM_LOCK monitor already acquired
            super.clearCache();
            _lastTimestamp = -1;
        }

        protected void refreshIfNecessary() {
            if (_lastTimestamp == -1) {
                refreshInfo();
            } else {
                File file = toJavaFile();
                long currentTimestamp = file.lastModified();
                if (currentTimestamp == 0) {
                    // If the timestamp is 0, assume it's been deleted
                    _files = Collections.emptyList();
                    _directories = Collections.emptyList();
                } else if (_lastTimestamp != currentTimestamp) {
                    refreshInfo();
                }
            }
        }

        @Override
        protected void maybeSetTimestamp(File javaFile) {
            _lastTimestamp = javaFile.lastModified();
        }
    }

    private class FuzzyTimestampCachingFileRetrievalStrategy extends CachingFileRetrievalStrategy {
        private long _lastFileTimestamp;  // in ms, absolute time
        private long _lastRefreshTimestamp; // in ms, absolute time

        public void clearCache() {
            // This should always be called with the CACHED_FILE_SYSTEM_LOCK monitor already acquired
            super.clearCache();
            _lastFileTimestamp = -1;
            _lastRefreshTimestamp = -1;
        }

        protected void refreshIfNecessary() {
            if (_lastFileTimestamp == -1) {
                doRefreshImpl();
            } else {
                File file = toJavaFile();
                long currentTimestamp = file.lastModified();
                if (currentTimestamp == 0) {
                    // If the timestamp is 0, assume it's been deleted
                    _files = Collections.emptyList();
                    _directories = Collections.emptyList();
                } else if (_lastFileTimestamp != currentTimestamp) {
                    doRefreshImpl();
                } else {
                    long refreshDelta = _lastRefreshTimestamp - currentTimestamp;
                    if (refreshDelta > -16 && refreshDelta < 16) {
                        doRefreshImpl();
                    }
                }
            }
        }

        private void doRefreshImpl() {
            _lastRefreshTimestamp = System.currentTimeMillis();
            refreshInfo();
        }

        @Override
        protected void maybeSetTimestamp(File javaFile) {
            _lastFileTimestamp = javaFile.lastModified();
        }
    }

    private class FullyCachedFileRetrievalStrategy extends CachingFileRetrievalStrategy {
        @Override
        protected void refreshIfNecessary() {
            if (_files == null) {
                refreshInfo();
            }
        }

        @Override
        protected void maybeSetTimestamp(File javaFile) {
            // Do nothing
        }
    }
}
