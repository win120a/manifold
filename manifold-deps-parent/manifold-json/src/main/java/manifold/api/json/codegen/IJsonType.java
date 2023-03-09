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

import java.util.Collections;
import java.util.List;

import manifold.api.json.codegen.schema.TypeAttributes;

/**
 *
 */
public interface IJsonType {
    String getName();

    String getIdentifier();

    IJsonParentType getParent();

    TypeAttributes getTypeAttributes();

    IJsonType copyWithAttributes(TypeAttributes attributes);

    IJsonType merge(IJsonType type);

    default List<IJsonType> getDefinitions() {
        return Collections.emptyList();
    }

    default void setDefinitions(List<IJsonType> definitions) {
    }

    /**
     * JSon Schema types normally compare by identity, however for
     * some use-cases we still need to compare them structurally e.g.,
     * for merging types.
     */
    default boolean equalsStructurally(IJsonType type2) {
        return equals(type2);
    }
}
