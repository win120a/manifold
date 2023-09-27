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

import manifold.rt.api.util.Pair;
import manifold.sql.query.api.QueryParameter;

import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class JdbcQueryParameter implements QueryParameter
{
  private final JdbcQueryTable _queryTable;
  private final int _position;
  private final String _name;
  private final int _jdbcType;
  private final String _sqlType;
  private final int _size;
  private final int _scale;
  private final boolean _isNullable;
  private final boolean _isSigned;
  private final String _javaClassNameForGetObject;

  public JdbcQueryParameter( int paramIndex, String name, JdbcQueryTable queryTable, ParameterMetaData paramMetaData, PreparedStatement preparedStatement ) throws SQLException
  {
    _position = paramIndex;
    _name = name == null ? "p" + paramIndex : name;
    _queryTable = queryTable;

    Pair<Integer, Boolean> types = getJdbcType( paramMetaData, paramIndex );
    _jdbcType = types.getFirst();
    boolean isFlakyDriver = types.getSecond();

    String sqlType;
    int size;
    int scale;
    boolean isNullable;
    boolean isSigned;
    String javaClassNameForGetObject;

    if( isFlakyDriver )
    {
      sqlType = "varchar";
      size = 0;
      scale = 0;
      isNullable = true;
      isSigned = true;
      javaClassNameForGetObject = Object.class.getTypeName();
    }
    else
    {
      sqlType = paramMetaData.getParameterTypeName( paramIndex );
      size = paramMetaData.getPrecision( paramIndex );
      scale = paramMetaData.getScale( paramIndex );
      isNullable = paramMetaData.isNullable( paramIndex ) != ParameterMetaData.parameterNoNulls;
      isSigned = paramMetaData.isSigned( paramIndex );
      javaClassNameForGetObject = paramMetaData.getParameterClassName( paramIndex );
    }
    
    _sqlType = sqlType;
    _size = size;
    _scale = scale;
    _isNullable = isNullable;
    _isSigned = isSigned;
    _javaClassNameForGetObject = javaClassNameForGetObject;
  }

  private Pair<Integer, Boolean> getJdbcType( ParameterMetaData paramMetaData, int paramIndex ) throws SQLException
  {
    int jdbcType;
    boolean isFlakyDriver = false;
    try
    {
      jdbcType = paramMetaData.getParameterType( paramIndex );

      // sqlite and mysql are known flakes here. See comment in exception below.
      String productName = getTable().getSchema().getDatabaseProductName();
      if( jdbcType == Types.VARCHAR &&
        ("sqlite".equalsIgnoreCase( productName ) ||
         "mysql".equalsIgnoreCase( productName )) )
      {
        throw new SQLException(); // caught just below
      }
    }
    catch( SQLException se )
    {
      // (circus music)
      // Some drivers don't provide query parameter types when the parameter's value is not set. For instance, depending
      // on the version, SQLite will either return VARCHAR for all parameters or it will throw an exception when a parameter
      // is not set, hence this catch block. Mysql throws the borfin exception too unless the "generateSimpleParameterMetadata"
      // url parameter is set in which case it provides the same crap sandwich as sqlite (as of mysql driver 8.1).
      //
      jdbcType = Types.OTHER;
      isFlakyDriver = true;
    }

    return new Pair<>( jdbcType, isFlakyDriver );
  }

  @Override
  public int getJdbcType()
  {
    return _jdbcType;
  }

  @Override
  public String getSqlType()
  {
    return _sqlType;
  }

  @Override
  public String getColumnClassName()
  {
    return _javaClassNameForGetObject;
  }

  @Override
  public JdbcQueryTable getTable()
  {
    return _queryTable;
  }

  @Override
  public int getPosition()
  {
    return _position;
  }

  @Override
  public String getName()
  {
    return _name;
  }

  @Override
  public int getSize()
  {
    return _size;
  }

  @Override
  public int getScale()
  {
    return _scale;
  }

  @Override
  public boolean isNullable()
  {
    return _isNullable;
  }

  @Override
  public boolean isSigned()
  {
    return _isSigned;
  }
}
