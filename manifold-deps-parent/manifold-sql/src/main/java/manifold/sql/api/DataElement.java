/*
 * Copyright (c) 2023 - Manifold Systems LLC
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

package manifold.sql.api;

import manifold.sql.rt.api.BaseElement;
import manifold.sql.rt.api.Dependencies;
import manifold.sql.rt.api.ValueAccessor;

public interface DataElement extends BaseElement
{
  Table getTable();
  int getJdbcType();
  int getScale();

  default Class<?> getType()
  {
    ValueAccessor accessor = Dependencies.instance().getValueAccessorProvider().get( getJdbcType() );
    return accessor == null ? null : accessor.getJavaType( this );
  }
}
