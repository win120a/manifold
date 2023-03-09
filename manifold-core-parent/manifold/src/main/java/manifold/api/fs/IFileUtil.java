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
import java.io.InputStreamReader;

import manifold.rt.api.util.StreamUtil;


import static java.nio.charset.StandardCharsets.UTF_8;

public class IFileUtil {

    public static String getExtension(IFile file) {
        int lastDot = file.getName().lastIndexOf(".");
        if (lastDot >= 0) {
            return file.getName().substring(lastDot + 1);
        } else {
            return "";
        }
    }

    public static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot >= 0) {
            return fileName.substring(lastDot + 1);
        } else {
            return "";
        }
    }

    public static String getBaseName(IFile file) {
        return getBaseName(file.getName());
    }

    public static String getBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot >= 0) {
            return fileName.substring(0, lastDot);
        } else {
            return "";
        }
    }

    /**
     * Avoid including dependency jar files that are not meant to be scanned for source files
     */
    public static boolean hasSourceFiles(IDirectory root) {
        if (!root.exists()) {
            return false;
        }

        return !Extensions.containsManifest(root) ||
                !Extensions.getExtensions(root, Extensions.CONTAINS_SOURCES).isEmpty() ||

                // this check is too aggressive eg., including many jars we don't need to process
                //!Extensions.getExtensions( root, "Main-Class" ).isEmpty() ||

                // Weblogic packages all WEB-INF/classes content into this JAR
                // http://middlewaremagic.com/weblogic/?p=408
                // http://www.coderanch.com/t/69641/BEA-Weblogic/wl-cls-gen-jar-coming
                // So we need to always treat it as containing sources
                root.getName().equals("_wl_cls_gen.jar");
    }

    public static int findOffset(IFile file, int line, int column) {
        try {
            int offset = 0;
            String content = StreamUtil.getContent(new InputStreamReader(file.openInputStream(), UTF_8));
            for (int i = 1; i < line; i++) {
                if (content.length() > offset) {
                    offset = content.indexOf('\n', offset) + 1;
                }
            }
            return offset + column - 1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
