/*
 * Copyright (c) 2019 - Manifold Systems LLC
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

package manifold.api.gen;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import manifold.api.fs.IFile;
import manifold.api.fs.IFileFragment;
import manifold.api.host.IModule;
import manifold.rt.api.ActualName;
import manifold.rt.api.SourcePosition;
import manifold.rt.api.util.ManIdentifierUtil;
import manifold.rt.api.util.ManStringUtil;
import manifold.rt.api.util.StreamUtil;
import manifold.util.ManExceptionUtil;


import static java.nio.charset.StandardCharsets.UTF_8;

public class SrcLinkedClass extends AbstractSrcClass<SrcLinkedClass> {
    protected static final String FIELD_FILE_URL = "__FILE_URL";

    private IFile _linkedFile;
    private Map<IFile, int[]> _resFileToContent;
    private String _fileContent;


    public SrcLinkedClass(String fqn, Kind kind, IFile linkedFile) {
        this(fqn, kind, linkedFile, null, null, null);
    }

    public SrcLinkedClass(String fqn, AbstractSrcClass enclosingClass, Kind kind) {
        this(fqn, enclosingClass, kind, null, null, null, null);
    }

    /**
     * Use this constructor to automatically handle extension methods on inner classes
     */
    public SrcLinkedClass(String fqn, Kind kind, IFile linkedFile,
                          JavaFileManager.Location location, IModule module, DiagnosticListener<JavaFileObject> errorHandler) {
        this(fqn, null, kind, linkedFile, location, module, errorHandler);
    }

    public SrcLinkedClass(String fqn, AbstractSrcClass enclosingClass, Kind kind, IFile linkedFile,
                          JavaFileManager.Location location, IModule module, DiagnosticListener<JavaFileObject> errorHandler) {
        super(fqn, enclosingClass, kind, location, module, errorHandler);
        if (enclosingClass == null) {
            _linkedFile = linkedFile;
            addFileField();
        }
    }

    protected void addFileField() {
        IFile linkedFile = getLinkedFile();
        if (linkedFile == null) {
            throw new IllegalStateException("Expecting non-null linkedFile");
        }

        addField(
                new SrcField(FIELD_FILE_URL, String.class)
                        .modifiers(isInterface() ? 0 : Modifier.STATIC | Modifier.FINAL)
                        .initializer(new SrcRawExpression("\"" + linkedFile.toURI().toString() + "\"")));
    }

    public void addSourcePositionAnnotation(SrcAnnotated srcAnno, String name, int line, int column) {
        SrcAnnotationExpression annotation = new SrcAnnotationExpression(SourcePosition.class.getSimpleName())
                .addArgument(new SrcArgument(new SrcMemberAccessExpression(FIELD_FILE_URL)).name("url"))
                .addArgument("feature", String.class, name)
                .addArgument("offset", int.class, findOffset(getLinkedFile(), line, column))
                .addArgument("length", int.class, name.length());
        srcAnno.addAnnotation(annotation);
    }

    private Map<IFile, int[]> getResFileToContent() {
        if (_resFileToContent != null) {
            return _resFileToContent;
        }

        AbstractSrcClass enclosingClass = getEnclosingClass();
        if (enclosingClass instanceof SrcLinkedClass) {
            Map<IFile, int[]> resFileToContent = ((SrcLinkedClass) enclosingClass).getResFileToContent();
            if (resFileToContent != null) {
                return resFileToContent;
            }
        }
        return _resFileToContent = new HashMap<>();
    }

    private IFile getLinkedFile() {
        if (_linkedFile == null) {
            AbstractSrcClass enclosingClass = getEnclosingClass();
            if (enclosingClass instanceof SrcLinkedClass) {
                return ((SrcLinkedClass) enclosingClass).getLinkedFile();
            }
            throw new IllegalStateException("Expecting non-null _linkedFile");
        } else {
            return _linkedFile;
        }
    }

    public static void addActualNameAnnotation(SrcAnnotated srcAnno, String name, boolean capitalize) {
        String identifier = makeIdentifier(name, capitalize);
        if (!identifier.equals(name) ||
                !Character.isAlphabetic(name.charAt(0))) // so that underscore is included as key in maps (for MapStruct)
        {
            srcAnno.addAnnotation(new SrcAnnotationExpression(ActualName.class.getSimpleName())
                    .addArgument(new SrcArgument(new SrcRawExpression('"' + name + '"'))));
        }
    }

    public static String makeIdentifier(String name, boolean capitalize) {
        String identifier = capitalize
                ? ManStringUtil.capitalize(ManIdentifierUtil.makeIdentifier(name))
                : ManIdentifierUtil.makeIdentifier(name);
        return handleSpecialCases(identifier);
    }

    private static String handleSpecialCases(String identifier) {
        if (identifier.equals(Class.class.getSimpleName())) {
            // prevent overriding Object#getClass()
            return "Clazz";
        }
        return identifier;
    }

    public void processContent(int line, int column, BiConsumer<String, Integer> contentHandler) {
        IFile file = getLinkedFile();
        int offset = findOffset(file, line, column);
        try {
            contentHandler.accept(getFileContent(), offset);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private int findOffset(IFile file, int line, int column) {
        int[] lineOffsets = getResFileToContent().computeIfAbsent(file,
                f -> {
                    try {
                        String content = getFileContent();
                        ArrayList<Integer> lineOffsetList = new ArrayList<>();
                        lineOffsetList.add(0);
                        for (int index = content.indexOf('\n') + 1; index > 0; index = content.indexOf('\n', index) + 1) {
                            lineOffsetList.add(index);
                        }
                        int[] array = new int[lineOffsetList.size()];
                        for (int i = 0; i < array.length; i++) {
                            array[i] = lineOffsetList.get(i);
                        }
                        return array;
                    } catch (IOException ioe) {
                        throw ManExceptionUtil.unchecked(ioe);
                    }
                });
        int offset;
        try {
            offset = lineOffsets[line - 1] + column - 1;
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.err.print("WARNING: ");
            ex.printStackTrace();
            offset = 0;
        }

        if (file instanceof IFileFragment) {
            offset += ((IFileFragment) file).getOffset();
        }
        return offset;
    }

    private String getFileContent() throws IOException {
        if (_fileContent != null) {
            return _fileContent;
        }

        AbstractSrcClass enclosingClass = getEnclosingClass();
        if (enclosingClass instanceof SrcLinkedClass) {
            return ((SrcLinkedClass) enclosingClass).getFileContent();
        } else if (enclosingClass == null) {
            return _fileContent == null
                    ? _fileContent = StreamUtil.getContent(new InputStreamReader(getLinkedFile().openInputStream(), UTF_8))
                    : _fileContent;
        }

        throw new IllegalStateException();
    }
}
