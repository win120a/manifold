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

package manifold.api.highjump;

import com.sun.tools.javac.tree.JCTree;

import java.net.MalformedURLException;
import java.util.Set;

import manifold.api.fs.IFile;
import manifold.api.host.IManifoldHost;
import manifold.api.type.AbstractSingleFileModel;

/**
 *
 */
class Model extends AbstractSingleFileModel {
    private String _url;
    private JCTree.JCClassDecl _classDecl;

    Model(IManifoldHost host, String fqn, Set<IFile> files) {
        super(host, fqn, files);
        assignUrl();
    }

    private void assignUrl() {
        try {
            _url = getFile().toURI().toURL().toString();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getUrl() {
        return _url;
    }

    public JCTree.JCClassDecl getClassDecl() {
        return _classDecl;
    }

    public void setClassDecl(JCTree.JCClassDecl classDecl) {
        _classDecl = classDecl;
    }

    @Override
    public void updateFile(IFile file) {
        super.updateFile(file);
        _classDecl = null;
        assignUrl();
    }
}
