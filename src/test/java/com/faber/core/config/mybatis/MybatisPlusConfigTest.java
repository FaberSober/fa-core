package com.faber.core.config.mybatis;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.faber.core.constant.FaSetting;
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
        assertLogicDeleteValues("DM DBMS", "0", "1");
        assertLogicDeleteValues("MySQL", "0", "1");
        assertLogicDeleteValues("PostgreSQL", "false", "true");
    }

    @Test
    void shouldSelectDmMapperStatementsAndQuoteReservedColumns() throws Exception {
        DataSource dataSource = mockDatabase("DM DBMS");
        MybatisPlusConfig config = new MybatisPlusConfig();
        config.faSetting = new FaSetting();
        GlobalConfig global = config.globalConfig(dataSource);
        assertEquals("\"%s\"", global.getDbConfig().getColumnFormat());
        assertEquals("dm8", config.sqlSessionFactory(dataSource, global).getConfiguration().getDatabaseId());
    }

    private void assertLogicDeleteValues(String productName, String notDeleted, String deleted) throws Exception {
        DataSource dataSource = mockDatabase(productName);
        GlobalConfig.DbConfig dbConfig = new MybatisPlusConfig().globalConfig(dataSource).getDbConfig();
        assertEquals(notDeleted, dbConfig.getLogicNotDeleteValue());
        assertEquals(deleted, dbConfig.getLogicDeleteValue());
    }

    private DataSource mockDatabase(String productName) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(productName);

        return dataSource;
    }
}
