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

package manifold.api.json.codegen;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

import manifold.api.json.codegen.schema.Type;
import manifold.api.json.codegen.schema.TypeAttributes;
import manifold.ext.rt.RuntimeMethods;

/**
 *
 */
public class JsonBasicType implements IJsonType {
    private final Type _type;
    private Class<?> _javaClass;
    private final TypeAttributes _typeAttributes;

    public JsonBasicType(Type type, TypeAttributes typeAttributes) {
        _type = type;
        _typeAttributes = typeAttributes;
        boolean nullable = typeAttributes.getNullable() != null && typeAttributes.getNullable();
        switch (type) {
            case String:
                _javaClass = String.class;
                break;
            case Number:
                _javaClass = nullable ? Double.class : double.class;
                break;
            case Integer:
                _javaClass = nullable ? Integer.class : int.class;
                break;
            case Boolean:
                _javaClass = nullable ? Boolean.class : boolean.class;
                break;
            case Null:
                _javaClass = Void.class;
                break;
            default:
                throw new IllegalArgumentException(type.getName() + " is not a simple type.");
        }
        Object defaultValue = typeAttributes.getDefaultValue();
        if (defaultValue != null) {
            _typeAttributes.setDefaultValue(RuntimeMethods.coerce(defaultValue, _javaClass));
        }
    }

    private JsonBasicType(Type type) {
        this(type, new TypeAttributes(true));
    }

    public boolean isPrimitive() {
        return _javaClass.isPrimitive();
    }

    public Class<?> box() {
        if (isPrimitive()) {
            if (_javaClass == int.class) {
                return Integer.class;
            }
            if (_javaClass == long.class) {
                return Long.class;
            }
            if (_javaClass == char.class) {
                return Character.class;
            }
            if (_javaClass == double.class) {
                return Double.class;
            }
            if (_javaClass == float.class) {
                return Float.class;
            }
            if (_javaClass == boolean.class) {
                return Boolean.class;
            }
        }
        throw new IllegalStateException("Unexpected type to box: " + _javaClass.getTypeName());
    }

    public static JsonBasicType get(Object jsonObj) {
        if (jsonObj == null) {
            return null;
        }

        Class<?> cls = jsonObj.getClass();
        if (cls == byte.class || cls == short.class || cls == int.class || cls == long.class ||
                cls == Byte.class || cls == Short.class || cls == Integer.class || cls == Long.class ||
                cls == BigInteger.class) {
            return new JsonBasicType(Type.Integer);
        }
        if (cls == float.class || cls == double.class ||
                cls == Float.class || cls == Double.class ||
                cls == BigDecimal.class) {
            return new JsonBasicType(Type.Number);
        }
        if (cls == boolean.class || cls == Boolean.class) {
            return new JsonBasicType(Type.Boolean);
        }
        return new JsonBasicType(Type.String);
    }

    @Override
    public String getName() {
        return _javaClass.getSimpleName();
    }

    @Override
    public String getIdentifier() {
        return _javaClass.getSimpleName();
    }

    public Type getJsonType() {
        return _type;
    }

    @Override
    public IJsonParentType getParent() {
        return null;
    }

    @Override
    public TypeAttributes getTypeAttributes() {
        return _typeAttributes;
    }

    @Override
    public IJsonType copyWithAttributes(TypeAttributes attributes) {
        if (getTypeAttributes().equals(attributes)) {
            return this;
        }
        return new JsonBasicType(_type, getTypeAttributes().overrideWith(attributes));
    }

    public IJsonType merge(IJsonType that) {
        if (!(that instanceof JsonBasicType)) {
            return that.merge(this);
        }

        JsonBasicType other = (JsonBasicType) that;
        if (_javaClass == String.class || other._javaClass == String.class) {
            // String is compatible with all simple types
            return new JsonBasicType(Type.String, getTypeAttributes().blendWith(other.getTypeAttributes()));
        }

        if (_javaClass == Void.class) {
            return other;
        }
        if (other._javaClass == Void.class) {
            return this;
        }

        if (_type == other._type) {
            return new JsonBasicType(_type, getTypeAttributes().blendWith(other.getTypeAttributes()));
        }

        if (_type == Type.Integer && other._type == Type.Number ||
                _type == Type.Number && other._type == Type.Integer) {
            return new JsonBasicType(Type.Number, getTypeAttributes().blendWith(other.getTypeAttributes()));
        }

        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonBasicType that = (JsonBasicType) o;
        return _type == that._type &&
                Objects.equals(_javaClass, that._javaClass) &&
                Objects.equals(_typeAttributes, that._typeAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_type, _javaClass, _typeAttributes);
    }
}
