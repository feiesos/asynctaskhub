package org.feiesos.asynctaskhub.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.feiesos.asynctaskhub.entity.TaskStatus;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

@MappedTypes(TaskStatus.class)
public class PgEnumTypeHandler extends BaseTypeHandler<TaskStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, TaskStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter.name(), Types.OTHER);
    }

    @Override
    public TaskStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String val = rs.getString(columnName);
        return val == null ? null : TaskStatus.valueOf(val);
    }

    @Override
    public TaskStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String val = rs.getString(columnIndex);
        return val == null ? null : TaskStatus.valueOf(val);
    }

    @Override
    public TaskStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String val = cs.getString(columnIndex);
        return val == null ? null : TaskStatus.valueOf(val);
    }
}
