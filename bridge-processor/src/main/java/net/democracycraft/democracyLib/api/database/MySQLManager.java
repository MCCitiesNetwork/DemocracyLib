package net.democracycraft.democracyLib.api.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL connection manager for obtaining JDBC connections and running tasks asynchronously.
 * <p>
 * Contract:
 * <ul>
 *   <li>Reads configuration keys from plugin config: mysql.host, mysql.port, mysql.database, mysql.user, mysql.password, mysql.useSSL</li>
 *   <li>Creates the database if it does not exist</li>
 *   <li>Provides a lazily created single JDBC connection (guarded by a lock for multi-threaded access)</li>
 *   <li>Exposes a Gson instance for JSON serialization used by table helpers</li>
 *   <li>Offers withConnection utility to safely execute code with the current connection</li>
 *   <li>Offers withTransaction utility for atomic multi-statement operations</li>
 *   <li>Provides async utilities for non-blocking database operations</li>
 * </ul>
 */
public class MySQLManager {

    /** A Gson instance with null serialization enabled for JSON column serialization. */
    public final Gson gson = new GsonBuilder().serializeNulls().create();

    private final String databaseHost;
    private final int databasePort;
    private final String databaseName;
    private final String databaseUsername;
    private final String databasePassword;
    private final boolean useSslConnection;

    private volatile Connection activeConnection;
    private final Object connectionLock = new Object();
    private final JavaPlugin ownerPlugin;

    /**
     * Creates a MySQLManager using the plugin's own config.
     * <p>
     * Expected config keys:
     * <ul>
     *   <li>mysql.host - Database server hostname</li>
     *   <li>mysql.port - Database server port (default: 3306)</li>
     *   <li>mysql.database - Database name to use/create</li>
     *   <li>mysql.user - Database username</li>
     *   <li>mysql.password - Database password</li>
     *   <li>mysql.useSSL - Whether to use SSL connection (default: false)</li>
     * </ul>
     *
     * @param plugin the owning plugin
     * @return a configured MySQLManager instance
     */
    public static MySQLManager fromConfig(JavaPlugin plugin) {
        return new MySQLManager(plugin, plugin.getConfig());
    }

    /**
     * Creates a MySQLManager with explicit connection parameters.
     *
     * @param plugin the owning plugin for logging and async scheduling
     * @param host database server hostname
     * @param port database server port
     * @param database database name
     * @param username database username
     * @param password database password
     * @param useSSL whether to use SSL
     * @return a configured MySQLManager instance
     */
    public static MySQLManager create(JavaPlugin plugin, String host, int port, String database,
                                       String username, String password, boolean useSSL) {
        return new MySQLManager(plugin, host, port, database, username, password, useSSL);
    }

    private MySQLManager(JavaPlugin plugin, FileConfiguration configuration) {
        this.ownerPlugin = Objects.requireNonNull(plugin, "plugin cannot be null");

        this.databaseHost = Objects.requireNonNull(configuration.getString("mysql.host"), "mysql.host is required");
        this.databasePort = configuration.getInt("mysql.port", 3306);
        this.databaseName = Objects.requireNonNull(configuration.getString("mysql.database"), "mysql.database is required");
        this.databaseUsername = Objects.requireNonNull(configuration.getString("mysql.user"), "mysql.user is required");
        this.databasePassword = Objects.requireNonNull(configuration.getString("mysql.password"), "mysql.password is required");
        this.useSslConnection = configuration.getBoolean("mysql.useSSL", false);
    }

    private MySQLManager(JavaPlugin plugin, String host, int port, String database,
                         String username, String password, boolean useSSL) {
        this.ownerPlugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.databaseHost = Objects.requireNonNull(host, "host cannot be null");
        this.databasePort = port;
        this.databaseName = Objects.requireNonNull(database, "database cannot be null");
        this.databaseUsername = Objects.requireNonNull(username, "username cannot be null");
        this.databasePassword = Objects.requireNonNull(password, "password cannot be null");
        this.useSslConnection = useSSL;
    }

    /**
     * Ensures that the target database exists; creates it if missing.
     * <p>
     * This connects to the MySQL server without specifying a database and creates
     * the configured database if it does not exist.
     * <p>
     * This method is thread-safe.
     */
    public void ensureDatabaseExists() {
        String serverConnectionUrl = "jdbc:mysql://" + databaseHost + ":" + databasePort +
                "/?useSSL=" + useSslConnection + "&autoReconnect=true&characterEncoding=UTF-8&serverTimezone=UTC";
        try (Connection serverConnection = DriverManager.getConnection(serverConnectionUrl, databaseUsername, databasePassword)) {
            try (var createStatement = serverConnection.createStatement()) {
                createStatement.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName +
                        "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                getLogger().info("Database '" + databaseName + "' ensured to exist.");
            }
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "Failed to create database '" + databaseName +
                    "'. Check credentials and privileges.", exception);
        }
    }

    /**
     * Opens a connection to the database if none exists or the current one is closed.
     * <p>
     * This method is thread-safe. Multiple threads calling this method simultaneously
     * will not create duplicate connections.
     */
    public void connect() {
        synchronized (connectionLock) {
            // Double-check inside synchronized block
            try {
                if (activeConnection != null && !activeConnection.isClosed()) {
                    return;
                }
            } catch (SQLException ignored) {
                // Connection check failed, will attempt to reconnect
            }

            final String connectionUrl = "jdbc:mysql://" + databaseHost + ":" + databasePort + "/" + databaseName +
                    "?useSSL=" + useSslConnection + "&autoReconnect=true&characterEncoding=UTF-8&serverTimezone=UTC";
            try {
                activeConnection = DriverManager.getConnection(connectionUrl, databaseUsername, databasePassword);
                getLogger().info("Connected to MySQL database: " + databaseName);
            } catch (SQLException exception) {
                activeConnection = null;
                getLogger().log(Level.SEVERE, "Failed to connect to MySQL (" + connectionUrl +
                        ") as '" + databaseUsername + "'.", exception);
            }
        }
    }

    /**
     * Sets up the database by ensuring it exists and establishing a connection.
     * <p>
     * This is the recommended method to call during plugin initialization.
     * This method is thread-safe.
     */
    public void setupDatabase() {
        ensureDatabaseExists();
        connect();
    }

    /**
     * Sets up the database asynchronously.
     *
     * @return a CompletableFuture that completes when setup is done
     */
    public CompletableFuture<Void> setupDatabaseAsync() {
        return runAsync(this::setupDatabase);
    }

    /**
     * Closes the active connection, if any.
     * <p>
     * This method is thread-safe.
     */
    public void disconnect() {
        synchronized (connectionLock) {
            try {
                if (activeConnection != null) {
                    activeConnection.close();
                    activeConnection = null;
                    getLogger().info("Disconnected from MySQL database: " + databaseName);
                }
            } catch (SQLException exception) {
                getLogger().log(Level.SEVERE, "Failed to disconnect from MySQL", exception);
            }
        }
    }

    /**
     * Guaranteed to return a live connection; reconnects as needed and throws if unavailable.
     * <p>
     * This method is thread-safe.
     *
     * @return an active database connection
     * @throws IllegalStateException if connection cannot be established
     */
    public Connection getConnection() {
        synchronized (connectionLock) {
            return getConnectionInternal();
        }
    }

    /**
     * Internal method to get connection - must be called within synchronized(connectionLock) block.
     *
     * @return an active database connection
     * @throws IllegalStateException if connection cannot be established
     */
    private Connection getConnectionInternal() {
        try {
            if (activeConnection == null || activeConnection.isClosed()) {
                connectInternal();
            }
        } catch (SQLException exception) {
            connectInternal();
        }
        if (activeConnection == null) {
            throw new IllegalStateException("MySQL connection is not available. Verify mysql.host/port/database/user/password, " +
                    "server reachability, and privileges.");
        }
        return activeConnection;
    }

    /**
     * Internal connect method - must be called within synchronized(connectionLock) block.
     */
    private void connectInternal() {
        final String connectionUrl = "jdbc:mysql://" + databaseHost + ":" + databasePort + "/" + databaseName +
                "?useSSL=" + useSslConnection + "&autoReconnect=true&characterEncoding=UTF-8&serverTimezone=UTC";
        try {
            activeConnection = DriverManager.getConnection(connectionUrl, databaseUsername, databasePassword);
            getLogger().info("Connected to MySQL database: " + databaseName);
        } catch (SQLException exception) {
            activeConnection = null;
            getLogger().log(Level.SEVERE, "Failed to connect to MySQL (" + connectionUrl +
                    ") as '" + databaseUsername + "'.", exception);
        }
    }

    /**
     * Thread-safe execution with a JDBC connection, returning a value.
     * <p>
     * <strong>WARNING:</strong> This method is synchronous and should only be called
     * from an async context (e.g., within supplyAsync or runAsync callbacks).
     *
     * @param databaseOperation the operation to execute with the connection
     * @param <R> the return type
     * @return the result of the operation
     */
    public <R> R executeWithConnection(ConnectionFunction<R> databaseOperation) {
        synchronized (connectionLock) {
            Connection connection = getConnectionInternal();
            try {
                return databaseOperation.apply(connection);
            } catch (Exception exception) {
                throw new RuntimeException("Database operation failed", exception);
            }
        }
    }

    /**
     * Executes the provided function inside a transaction with connection-level synchronization.
     * Auto-commits are disabled for the duration; on any exception, a rollback is issued.
     * <p>
     * <strong>WARNING:</strong> This method is synchronous and should only be called
     * from an async context (e.g., within supplyAsync or runAsync callbacks).
     *
     * @param transactionOperation work to execute within a single transaction
     * @param <R> the return type
     * @return function result
     */
    public <R> R executeWithTransaction(ConnectionFunction<R> transactionOperation) {
        synchronized (connectionLock) {
            Connection connection = getConnectionInternal();
            boolean previousAutoCommit;
            try {
                previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    R result = transactionOperation.apply(connection);
                    connection.commit();
                    return result;
                } catch (Exception exception) {
                    try { connection.rollback(); } catch (SQLException ignored) {}
                    throw new RuntimeException("Transaction failed and was rolled back", exception);
                } finally {
                    try { connection.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) {}
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to manage transaction state", exception);
            }
        }
    }

    // ================= Async Utilities =================

    /**
     * Runs a task asynchronously using the Bukkit scheduler.
     *
     * @param task the task to run
     */
    public void runAsyncTask(Runnable task) {
        getServer().getScheduler().runTaskAsynchronously(ownerPlugin, task);
    }

    /**
     * Runs a task asynchronously and returns a CompletableFuture.
     *
     * @param task the task to run
     * @return a CompletableFuture that completes when the task finishes
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runAsyncTask(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    /**
     * Runs a supplier asynchronously and returns a CompletableFuture with the result.
     *
     * @param supplier the supplier to run
     * @param <T> the return type
     * @return a CompletableFuture that completes with the result
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runAsyncTask(() -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    /**
     * Returns an Executor that runs tasks asynchronously on the Bukkit scheduler.
     *
     * @return an async executor
     */
    public Executor getAsyncExecutor() {
        return this::runAsyncTask;
    }

    /**
     * Simple ping to validate the connection is healthy asynchronously.
     *
     * @return CompletableFuture with true if the connection responds to a small query
     */
    public CompletableFuture<Boolean> pingAsync() {
        return supplyAsync(() -> {
            try {
                return executeWithConnection(connection -> {
                    try (var statement = connection.createStatement();
                         var resultSet = statement.executeQuery("SELECT 1")) {
                        return resultSet.next();
                    }
                });
            } catch (Exception exception) {
                return false;
            }
        });
    }

    /**
     * Functional interface for database operations that may throw exceptions.
     *
     * @param <R> the return type
     */
    @FunctionalInterface
    public interface ConnectionFunction<R> {
        R apply(Connection connection) throws Exception;
    }

    private Logger getLogger() {
        return ownerPlugin.getLogger();
    }

    private Server getServer() {
        return ownerPlugin.getServer();
    }

    /**
     * Gets the database name this manager is configured for.
     *
     * @return the database name
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Gets the owning plugin.
     *
     * @return the plugin
     */
    public JavaPlugin getOwnerPlugin() {
        return ownerPlugin;
    }

    @Override
    public String toString() {
        return "MySQLManager{" + databaseHost + ':' + databasePort + '/' + databaseName + "}";
    }
}
