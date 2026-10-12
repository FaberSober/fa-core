package com.faber.core.config.mybatis;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MybatisPlusConfigTest {

    @Test
    void shouldUseDatabaseSpecificLogicDeleteValues() throws Exception {
        assertLogicDeleteValues("Oracle", "0", "1");
        assertLogicDeleteValues("MySQL", "0", "1");
        assertLogicDeleteValues("PostgreSQL", "false", "true");
    }

    private void assertLogicDeleteValues(String productName, String notDeleted, String deleted) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(productName);

        GlobalConfig.DbConfig dbConfig = new MybatisPlusConfig().globalConfig(dataSource).getDbConfig();

        assertEquals(notDeleted, dbConfig.getLogicNotDeleteValue());
        assertEquals(deleted, dbConfig.getLogicDeleteValue());
    }
}
