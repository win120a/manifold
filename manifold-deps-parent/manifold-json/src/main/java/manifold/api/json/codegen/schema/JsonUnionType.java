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

package manifold.api.json.codegen.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import manifold.api.fs.IFile;
import manifold.api.json.AbstractJsonTypeManifold;
import manifold.api.json.codegen.DynamicType;
import manifold.api.json.codegen.IJsonParentType;
import manifold.api.json.codegen.IJsonType;
import manifold.api.json.JsonTransformer;
import manifold.api.json.codegen.JsonStructureType;
import manifold.util.concurrent.LocklessLazyVar;

/**
 *
 */
public class JsonUnionType extends JsonStructureType {
    private static final class State {
        private Map<String, IJsonType> _constituentTypes;
    }

    private LocklessLazyVar<JsonEnumType> _collapsedEnumType = LocklessLazyVar.make(() -> {
        if (!getMembers().isEmpty()) {
            return null;
        }
        if (getConstituents().stream().allMatch(e -> e instanceof JsonEnumType)) {
            return makeEnumType(getConstituents());
        }
        return null;
    });

    private final State _state;


    public JsonUnionType(JsonSchemaType parent, IFile source, String name, TypeAttributes attr) {
        super(parent, source, name, attr);
        _state = new State();
        _state._constituentTypes = Collections.emptyMap();
    }

    @Override
    protected void resolveRefsImpl() {
        super.resolveRefsImpl();
        for (Map.Entry<String, IJsonType> entry : new HashSet<>(_state._constituentTypes.entrySet())) {
            IJsonType type = entry.getValue();
            if (type instanceof JsonSchemaType) {
                ((JsonSchemaType) type).resolveRefs();
            } else if (type instanceof LazyRefJsonType) {
                type = ((LazyRefJsonType) type).resolve();
                _state._constituentTypes.put(entry.getKey(), type);
            }
        }
    }

    public Collection<? extends IJsonType> getConstituents() {
        return _state._constituentTypes.values();
    }

    public void addConstituent(String name, IJsonType type) {
        if (_state._constituentTypes.isEmpty()) {
            _state._constituentTypes = new LinkedHashMap<>();
        }
        _state._constituentTypes.put(name, type);
        if (type instanceof IJsonParentType && !isDefinition(type)) {
            super.addChild(name, (IJsonParentType) type);
        }
    }

    private boolean isDefinition(IJsonType type) {
        return type.getParent() != null &&
                type.getParent().getName().equals(JsonSchemaTransformer.JSCH_DEFINITIONS);
    }

    public JsonUnionType merge(IJsonType type) {
        IJsonType mergedType = null;
        for (IJsonType c : getConstituents()) {
            mergedType = JsonTransformer.mergeTypesNoUnion(c, type);
            if (mergedType != null && mergedType != DynamicType.instance()) {
                break;
            }
        }

        if (mergedType == null) {
            mergedType = type;
        }
        addConstituent(mergedType.getName(), mergedType);
        return this;
    }

    public JsonEnumType getCollapsedEnumType() {
        return _collapsedEnumType.get();
    }

    @Override
    public void render(AbstractJsonTypeManifold tm, StringBuilder sb, int indent, boolean mutable) {
        JsonEnumType collapsedEnumType = getCollapsedEnumType();
        if (collapsedEnumType != null) {
            collapsedEnumType.render(tm, sb, indent, mutable);
            return;
        }
        super.render(tm, sb, indent, mutable);
    }
}
