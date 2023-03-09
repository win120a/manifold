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

import java.util.Map;

import manifold.rt.api.Bindings;
import manifold.api.json.codegen.ErrantType;
import manifold.api.json.codegen.IJsonParentType;
import manifold.api.json.codegen.IJsonType;
import manifold.api.json.JsonIssue;
import manifold.api.json.codegen.JsonStructureType;
import manifold.json.rt.parser.Token;
import manifold.json.rt.api.DataBindings;
import manifold.internal.javac.IIssue;
import manifold.api.util.DebugLogUtil;
import manifold.rt.api.util.Pair;


import static manifold.api.json.codegen.schema.JsonSchemaTransformer.JSCH_TYPE;

/**
 *
 */
class ObjectTransformer {
    private final JsonSchemaTransformer _schemaTx;
    private final JsonStructureType _type;
    private final Bindings _jsonObj;

    static void transform(JsonSchemaTransformer schemaTx, JsonStructureType type, Bindings jsonObj) {
        new ObjectTransformer(schemaTx, type, jsonObj).transform();
    }

    private ObjectTransformer(JsonSchemaTransformer schemaTx, JsonStructureType type, Bindings jsonObj) {
        _schemaTx = schemaTx;
        _jsonObj = jsonObj;
        _type = type;
    }

    JsonStructureType getType() {
        return _type;
    }

    private void transform() {
        IJsonParentType parent = _type.getParent();
        if (parent != null) {
            parent.addChild(_type.getLabel(), _type);
        }
        _schemaTx.cacheByFqn(_type); // must cache now to handle recursive refs

        addProperties();
    }

    private void addProperties() {
        Object props = _jsonObj.get(JsonSchemaTransformer.JSCH_PROPERTIES);
        if (props == null) {
            return;
        }

        Token token = null;
        try {
            Bindings properties;
            if (props instanceof Pair) {
                properties = (Bindings) ((Pair) props).getSecond();
            } else {
                properties = (Bindings) props;
            }

            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Pair) {
                    token = ((Token[]) ((Pair) value).getFirst())[0];
                    value = ((Pair) value).getSecond();
                } else {
                    token = null;
                }
                Bindings bindings = handleOpenApiIdiom(value);
                IJsonType type;
                if (bindings == null) {
                    ErrantType errant = new ErrantType(null, name);
                    _type.addIssue(new JsonIssue(IIssue.Kind.Error, token, "Missing type"));
                    type = errant;
                } else {
                    type = _schemaTx.transformType(_type, _type.getFile(), name, bindings, null);
                }
                _type.addMember(name, type, token);
            }
            addRequired();
        } catch (Exception e) {
            String message = e.getMessage();
            _type.addIssue(new JsonIssue(IIssue.Kind.Error, token,
                    message == null ? DebugLogUtil.getStackTrace(e) : message));
        }
    }

    /**
     * OpenAPI lets you do this:
     * <pre><code>
     * "name": "string"
     * </code></pre>
     * instead of this:
     * <pre><code>
     * "name": {
     *   "type": "string"
     * }
     * </code></pre>
     */
    private Bindings handleOpenApiIdiom(Object value) {
        Bindings bindings;
        if (value instanceof String) {
            bindings = new DataBindings();
            bindings.put(JSCH_TYPE, value);
        } else {
            bindings = (Bindings) value;
        }
        return bindings;
    }

    private void addRequired() {
        Object requiredValue = _jsonObj.get(JsonSchemaTransformer.JSCH_REQUIRED);
        _type.addRequiredWithTokens(requiredValue);
    }
}
