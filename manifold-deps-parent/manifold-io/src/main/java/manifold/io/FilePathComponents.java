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

package manifold.io;

import manifold.io.extensions.java.io.File.ManFileExt;

import java.io.File;
import java.util.List;

/**
 * Represents the path to a file as a collection of directories.
 */
public class FilePathComponents {
    /**
     * The {@link File} object representing root of the path (for example, {@code /} or {@code C:} or empty for relative paths).
     */
    public final File root;
    /**
     * The list of {@link File} objects representing every directory in the path to the file, up to an including the file itself.
     */
    public final List<File> segments;


    public FilePathComponents(File root, List<File> segments) {
        this.root = root;
        this.segments = segments;
    }

    /**
     * Returns a string representing the root for this file, or an empty string is this file name is relative.
     */
    public String rootName() {
        return root.getPath();
    }

    /**
     * Returns {@code true} when the {@link #root} is not empty.
     */
    public boolean isRooted() {
        return !root.getPath().isEmpty();
    }

    /**
     * Returns the number of elements in the path to the file.
     */
    public int size() {
        return segments.size();
    }

    /**
     * Returns a sub-path of the path, starting with the directory at the specified {@code beginIndex} and up
     * to the specified {@code endIndex}.
     */
    public File subPath(int beginIndex, int endIndex) {
        if (beginIndex < 0 || beginIndex > endIndex || endIndex > size()) {
            throw new IllegalArgumentException();
        }

        return new File(segments.subList(beginIndex, endIndex).joinToString(File.separator));
    }

    public FilePathComponents normalize() {
        return new FilePathComponents(root, ManFileExt.normalize(segments));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FilePathComponents that = (FilePathComponents) o;

        if (!root.equals(that.root)) {
            return false;
        }
        return segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        int result = root.hashCode();
        result = 31 * result + segments.hashCode();
        return result;
    }
}
