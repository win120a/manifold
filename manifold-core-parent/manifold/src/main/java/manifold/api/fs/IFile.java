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

package manifold.api.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Path;

public interface IFile extends IResource {
    public static IFile getIFile(IFileSystem fs, Path classFile) {
        try {
            return fs.getIFile(classFile.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    IFile[] EMPTY_ARRAY = new IFile[0];

    InputStream openInputStream() throws IOException;

    OutputStream openOutputStream() throws IOException;

    OutputStream openOutputStreamForAppend() throws IOException;

    String getExtension();

    String getBaseName();

    /**
     * Facilitates virtual files e.g., IFileFragment
     */
    default IFile getPhysicalFile() {
        return this;
    }
}
