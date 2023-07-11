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

package manifold.sql.query.jdbc;

import manifold.sql.query.api.ForeignKeyQueryRef;
import manifold.sql.query.api.QueryColumn;
import manifold.sql.schema.api.SchemaForeignKey;

import java.util.List;

public class JdbcForeignKeyQueryRef implements ForeignKeyQueryRef
{
  private final SchemaForeignKey _ref;
  private final List<QueryColumn> _queryCols;

  public JdbcForeignKeyQueryRef( SchemaForeignKey ref, List<QueryColumn> queryCols )
  {
    _ref = ref;
    _queryCols = queryCols;
  }

  public String getName()
  {
    return getFk().getName();
  }

  public SchemaForeignKey getFk()
  {
    return _ref;
  }

  public List<QueryColumn> getQueryCols()
  {
    return _queryCols;
  }
}
