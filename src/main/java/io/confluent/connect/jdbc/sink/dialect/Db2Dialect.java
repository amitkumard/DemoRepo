/*
 * Copyright 2016 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.confluent.connect.jdbc.sink.dialect;

import static io.confluent.connect.jdbc.sink.dialect.StringBuilderUtil.joinToBuilder;

import java.util.Collection;
import java.util.Map;

import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;

public class Db2Dialect extends DbDialect {
  public Db2Dialect() {
    super("\"", "\"");
  }

  @Override
  protected String getSqlType(String schemaName, Map<String, String> parameters, Schema.Type type) {
    if (schemaName != null) {
      switch (schemaName) {
        case Decimal.LOGICAL_NAME:
          return "DECIMAL(31," + parameters.get(Decimal.SCALE_FIELD) + ")";
        case Date.LOGICAL_NAME:
          return "DATE";
        case Time.LOGICAL_NAME:
          return "TIME";
        case Timestamp.LOGICAL_NAME:
          return "TIMESTAMP";
      }
    }
    switch (type) {
      case INT8:
        return "SMALLINT";
      case INT16:
        return "SMALLINT";
      case INT32:
        return "INTEGER";
      case INT64:
        return "BIGINT";
      case FLOAT32:
        return "REAL";
      case FLOAT64:
        return "FLOAT";
      case BOOLEAN:
        return "CHAR(1)";
      case STRING:
        // Different for row vs column organized
        // return "VARCHAR(32592 OCTETS)";
        return "VARCHAR(8148 CODEUNITS32)";
      case BYTES:
        // Different for row vs column organized
        return "VARCHAR(32592) FOR BIT DATA";
    }
    return super.getSqlType(schemaName, parameters, type);
  }

  @Override
  public String getUpsertQuery(final String table, Collection<String> keyCols, Collection<String> cols) {
    // http://www.ibm.com/support/knowledgecenter/SSEPGG_11.1.0/com.ibm.db2.luw.sql.ref.doc/doc/r0010873.html

    final StringBuilder builder = new StringBuilder();
    builder.append("merge into ");
    final String tableName = escaped(table);
    builder.append(tableName);
    builder.append(" using (select ");
    joinToBuilder(builder, ", ", keyCols, cols, prefixedEscaper("? "));
    builder.append(" FROM SYSIBM.SYSDUMMY1) incoming on(");
    joinToBuilder(builder, " and ", keyCols, new StringBuilderUtil.Transform<String>() {
      @Override
      public void apply(StringBuilder builder, String col) {
        builder.append(tableName).append(".").append(escaped(col)).append("=incoming.").append(escaped(col));
      }
    });
    builder.append(")");
    if (cols != null && cols.size() > 0) {
      builder.append(" when matched then update set ");
      joinToBuilder(builder, ",", cols, new StringBuilderUtil.Transform<String>() {
        @Override
        public void apply(StringBuilder builder, String col) {
          builder.append(tableName).append(".").append(escaped(col)).append("=incoming.").append(escaped(col));
        }
      });
    }

    builder.append(" when not matched then insert(");
    joinToBuilder(builder, ",", cols, keyCols, prefixedEscaper(tableName + "."));
    builder.append(") values(");
    joinToBuilder(builder, ",", cols, keyCols, prefixedEscaper("incoming."));
    builder.append(")");
    return builder.toString();
  }
}
