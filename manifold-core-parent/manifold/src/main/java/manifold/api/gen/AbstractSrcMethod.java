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

package manifold.api.gen;

import com.sun.tools.javac.code.Flags;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class AbstractSrcMethod<T extends AbstractSrcMethod<T>> extends SrcStatement<T> {
    private SrcType _returns;
    private SrcStatementBlock _body;
    private List<SrcType> _typeVars;
    private List<SrcType> _throwTypes;
    private boolean _isConstructor;
    private boolean _isPrimaryConstructor;

    public AbstractSrcMethod(AbstractSrcClass srcClass) {
        super(srcClass);
        _typeVars = Collections.emptyList();
        _throwTypes = Collections.emptyList();
    }

    public boolean isConstructor() {
        return _isConstructor;
    }

    public void setConstructor(boolean isConstructor) {
        _isConstructor = isConstructor;
    }

    // for record classes only
    public boolean isPrimaryConstructor() {
        return _isPrimaryConstructor;
    }

    public void setPrimaryConstructor(boolean primary) {
        _isPrimaryConstructor = primary;
    }

    public T returns(SrcType returns) {
        _returns = returns;
        return (T) this;
    }

    public T returns(Class returns) {
        _returns = new SrcType(returns);
        return (T) this;
    }

    public T returns(String returns) {
        _returns = new SrcType(returns);
        return (T) this;
    }

    public void addTypeVar(SrcType typeVar) {
        if (_typeVars.isEmpty()) {
            _typeVars = new ArrayList<>();
        }
        _typeVars.add(typeVar);
    }

    public void addThrowType(SrcType type) {
        if (_throwTypes.isEmpty()) {
            _throwTypes = new ArrayList<>();
        }
        _throwTypes.add(type);
    }

    public T body(SrcStatementBlock body) {
        _body = body;
        return (T) this;
    }

    public T body(String rawText) {
        _body = new SrcStatementBlock().addStatement(rawText);
        return (T) this;
    }

    public SrcType getReturnType() {
        return _returns;
    }

    public List<SrcType> getTypeVariables() {
        return _typeVars;
    }

    public List<SrcType> getThrowTypes() {
        return _throwTypes;
    }

    private String renderThrowTypes(StringBuilder sb) {
        if (_throwTypes.size() > 0) {
            sb.append(" throws ");
            for (int i = 0; i < _throwTypes.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(_throwTypes.get(i));
            }
        }
        return "";
    }

    public String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(getSimpleName()).append(renderParameters(sb, true));
        return sb.toString();
    }

    @Override
    public StringBuilder render(StringBuilder sb, int indent) {
        renderAnnotations(sb, indent, false);
        indent(sb, indent);
        renderModifiers(sb,
                getModifiers() & ~Modifier.TRANSIENT,
                (getModifiers() & Flags.DEFAULT) != 0,
                isNonDefaultNonStaticInterfaceMethod() ? Modifier.PUBLIC : 0);
        renderTypeVars(_typeVars, sb);
        if (_returns != null) {
            _returns.render(sb, indent).append(' ').append(getSimpleName()).append(renderParameters(sb)).append(renderThrowTypes(sb));
        } else if (isConstructor()) {
            sb.append(getOwner().getSimpleName()).append(renderParameters(sb)).append(renderThrowTypes(sb));
        }
        if (isAbstractMethod()) {
            sb.append(";\n");
        } else {
            if (_body != null) {
                _body.render(sb, indent);
            } else {
                throw new IllegalStateException("Body of method is null");
            }
        }
        return sb;
    }

    private boolean isAbstractMethod() {
        return Modifier.isAbstract((int) getModifiers()) ||
                isNonDefaultNonStaticInterfaceMethod();
    }

    private boolean isNonDefaultNonStaticInterfaceMethod() {
        return getOwner() instanceof AbstractSrcClass &&
                ((AbstractSrcClass) getOwner()).isInterface() &&
                (getModifiers() & Flags.DEFAULT) == 0 &&
                (getModifiers() & Flags.STATIC) == 0;
    }
}
