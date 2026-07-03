package com.iam.infrastructure.tenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Wraps a DataSource so every returned Connection switches to the given schema via "USE <schema>".
 * This is the lowest-friction way to implement Schema-per-tenant on a single MySQL instance.
 */
public class SchemaRoutingDataSource extends DelegatingDataSource {

    private final String schema;

    public SchemaRoutingDataSource(DataSource target, String schema) {
        super(target);
        this.schema = schema;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection c = super.getConnection();
        try (Statement s = c.createStatement()) { s.execute("USE " + schema); }
        return c;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection c = super.getConnection(username, password);
        try (Statement s = c.createStatement()) { s.execute("USE " + schema); }
        return c;
    }

    // ---- satisfy the DelegatingDataSource abstract methods ----
    @Override
    public Logger getParentLogger() {
        try {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        } catch (Exception e) {
            return Logger.getGlobal();
        }
    }

    @Override
    public PrintWriter getLogWriter() { return null; }

    @Override
    public void setLogWriter(PrintWriter out) {}

    @Override
    public void setLoginTimeout(int seconds) {}

    @Override
    public int getLoginTimeout() { return 0; }

    @Override
    public <T> T unwrap(Class<T> iface) { return null; }

    @Override
    public boolean isWrapperFor(Class<?> iface) { return false; }
}
