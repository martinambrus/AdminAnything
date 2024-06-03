package com.martinambrus.adminAnything;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Configuration class containing all, mutable and immutable
 * configuration options for AdminAnything.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("HardCodedStringLiteral")
final class SQLManager {

    /**
     * Instance of {@link AdminAnything}.
     */
    private final AdminAnything plugin;

    /***
     * SQL Connection handle.
     */
    private transient Connection conn;

    /***
     * Table prefix for AA tables.
     */
    private String prefix = "aa_";

    /**
     * Prevents raising errors from an error handler.
     */
    private Boolean omitErrorLogs = false;

    /**
     * Database configuration file handle.
     */
    private FileConfiguration dbConfig;

    /**
     * Name of the file containing DB configuration.
     */
    private String dbConfigFileName = "config-db.yml";

    /***
     * Whether or not a successful connection was made.
     */
    private boolean isConnected = false;

    /***
     * Constructor, sets the main plugin class locally and initiates a connection
     * based on config settings.
     *
     * @param aa Instance of {@link AdminAnything}.
     */
    SQLManager(AdminAnything aa) {
        this.plugin = aa;

        // load DB config
        File dbFile = new File( AA_API.getAaDataDir(), this.dbConfigFileName );

        if (!dbFile.exists()) {
            plugin.saveResource( this.dbConfigFileName, true );
        }

        this.dbConfig = YamlConfiguration.loadConfiguration( dbFile );

        // store defaults to make sure all keys are present in the config in case we've upgraded the plugin
        InputStream defConfigStream = this.plugin.getResource( this.dbConfigFileName );
        if (defConfigStream != null) {
            this.dbConfig.setDefaults( YamlConfiguration.loadConfiguration( new InputStreamReader(defConfigStream, StandardCharsets.UTF_8) ) );
        }

        this.dbConfig.options().copyDefaults(true);

        try {
            this.dbConfig.save( AA_API.getAaDataDir() + "/" + this.dbConfigFileName );
        } catch (Throwable ex) {
            // even if we can't save the config, we still have it in memory, so we can continue below
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] Could not save configuration file. Please report the following to the plugin's author..."); //NON-NLS
            ex.printStackTrace();
        }

        // check DB type and work accordingly
        String sqlType = this.dbConfig.getString("db_type").toLowerCase();
        if ("mysql".equals(sqlType)) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
                this.conn = DriverManager.getConnection("jdbc:mysql://" + (
                        this.dbConfig.getString("host") != null ? this.dbConfig.getString("host") : "localhost") +
                        ":" + (this.dbConfig.getString("port") != null ? this.dbConfig.getString("port") : "3306") +
                        "/" + (this.dbConfig.getString("db_name") != null ? this.dbConfig.getString("db_name") : "minecraft"),
                        this.dbConfig.getString("user") != null ? this.dbConfig.getString("user") : "root",
                        this.dbConfig.getString("password") != null ? this.dbConfig.getString("password") : "");

                this.prefix = this.dbConfig.getString("table_prefix") != null ? this.dbConfig.getString("table_prefix") : "";

                // enable auto-committing to prevent retrieving old cached results
                this.conn.setAutoCommit(true);
                this.conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                this.isConnected = true;
            } catch (Throwable e) {
                Bukkit.getLogger()
                      .warning("[AdminAnything] It was not possible to connect to the database with details provided in the config-db.yml file. Using config-file.yml configuration instead.");

                return;
            }
        }
    } // end of method

    /**
     * Checks whether a DB connection was made.
     *
     * @return Returns true if a DB connection was made, false otherwise.
     */
    boolean connected() {
        return this.isConnected;
    } // end of method

    /**
     * Returns a table prefix for this SQL Manager's instance.
     *
     * @return Returns a table prefix for this SQL Manager's instance.
     */
    String getPrefix() {
        return this.prefix;
    } // end of method

    /**
     * Returns database configuration options.
     *
     * @return Returns database configuration options.
     */
    FileConfiguration getDbConfig() { return this.dbConfig; } // end of method

    /***
     * Executes a prepared query that doesn't return a resultset.
     *
     * @param query The actual query to run.
     * @param params Any number of parameters that will be used
     *               in a prepared SQL statement according to their type.
     *
     * @return Returns true if the query was executed successfully, false otherwise.
     */
    Boolean query(String query, Object... params) {
        if (!this.connected()) {
            Bukkit.getLogger()
                  .warning(plugin.getConf().getPluginName() + " - no DB connection, cannot perform queries!");

            return false;
        }

        if (params.length == 0) {
            try {
                Statement stat = conn.createStatement();
                stat.executeUpdate(query);
                stat.close();
            } catch (Throwable e) {
                Bukkit.getLogger().info("[AdminAnything] There was an error while trying to run a database query.");

                Bukkit.getLogger()
                      .info("[AdminAnything] query: " + query);

                Bukkit.getLogger()
                      .info("[AdminAnything] message: "
                          + e.getMessage() + ", cause: " + e.getCause());

                return false;
            }
        } else {
            // if we have only 1 parameter that is an ArrayList, make an array of objects out of it
            if ((params.length == 1) && ((params[0] instanceof List) || (params[0] instanceof ArrayList))) {
                params = ((List<?>) params[0]).toArray();
            }

            try {
                PreparedStatement prep = conn.prepareStatement(query);
                int i    = 1;
                for (Object o : params) {
                    if (o instanceof Integer) {
                        prep.setInt(i, (Integer)o);
                    } else if (o instanceof String) {
                        prep.setString(i, (String)o);
                    } else if (o instanceof Double) {
                        prep.setDouble(i, (Double)o);
                    } else if (o instanceof Float) {
                        prep.setFloat(i, (Float)o);
                    } else if (o instanceof Long) {
                        prep.setLong(i, (Long)o);
                    } else if (o instanceof Boolean) {
                        prep.setBoolean(i, (Boolean)o);
                    } else if (o instanceof Date) {
                        prep.setTimestamp(i, new Timestamp(((Date) o).getTime()));
                    } else if (o instanceof Timestamp) {
                        prep.setTimestamp(i, (Timestamp) o);
                    } else if (o == null) {
                        prep.setNull(i, 0);
                    } else {
                        // unhandled variable type
                        Bukkit.getLogger().info(plugin.getConf().getPluginName() + ' ' + AA_API.__("error.sql-invalid-parameter"));

                        if (AA_API.getDebug()) {
                            Bukkit.getLogger()
                                  .info(plugin.getConf().getPluginName() + ' ' + AA_API.__("sql.query") + ": " + query
                                  + ", " + AA_API.__("general.variable") + ": " + o.toString());
                        }

                        prep.clearBatch();
                        prep.close();
                        return false;
                    }
                    i++;
                }
                prep.addBatch();
                conn.setAutoCommit(false);
                prep.executeBatch();
                conn.commit();
                prep.close();
                prep = null;
            } catch (Throwable e) {
                if (!omitErrorLogs) {
                    Bukkit.getLogger().info("[AdminAnything] There was an error while trying to run a database query.");

                    Bukkit.getLogger()
                          .info("[AdminAnything] query: " + query
                          + ", parameters: " + Utils.implode(params, ", "));

                    Bukkit.getLogger()
                          .info("[AdminAnything] message: "
                              + e.getMessage() + ", cause: " + e.getCause());
                }
                return false;
            }
        }

        return true;
    } // end of method

    /***
     * Executes a selection SQL statement and returns a resultset.
     *
     * @param query The actual query to run.
     * @param params Any number of parameters that will be used
     *               in a prepared SQL statement according to their type.
     *
     * @return Returns the actual ResultSet for the given SQL query.
     */
    ResultSet query_res(String query, Object... params) {
        if (!this.connected()) {
            Bukkit.getLogger()
                  .warning(plugin.getConf().getPluginName() + " - no DB connection, cannot perform queries!");

            return null;
        }

        if (params.length == 0) {
            try {
                Statement stat = conn.createStatement();
                ResultSet res = stat.executeQuery(query);
                return res;
            } catch (Throwable e) {
                Bukkit.getLogger().info("[AdminAnything] There was an error while trying to run a database query.");

                Bukkit.getLogger()
                      .info("[AdminAnything] query: " + query);

                Bukkit.getLogger()
                      .info("[AdminAnything] message: "
                          + e.getMessage() + ", cause: " + e.getCause());
            }
        } else {
            // if we have only 1 parameter that is an ArrayList, make an array of objects out of it
            if ((params.length == 1) && ((params[0] instanceof List) || (params[0] instanceof ArrayList))) {
                params = ((List<?>) params[0]).toArray();
            }

            try {
                PreparedStatement prep = conn.prepareStatement(query);
                int i = 1;
                for (Object o : params) {
                    if (o instanceof Integer) {
                        prep.setInt(i, (Integer)o);
                    } else if (o instanceof String) {
                        prep.setString(i, (String)o);
                    } else if (o instanceof Double) {
                        prep.setDouble(i, (Double)o);
                    } else if (o instanceof Float) {
                        prep.setFloat(i, (Float)o);
                    } else if (o instanceof Long) {
                        prep.setLong(i, (Long)o);
                    } else if (o == null) {
                        prep.setNull(i, 0);
                    } else {
                        // unhandled variable type
                        Bukkit.getLogger().info(plugin.getConf().getPluginName() + ' ' + AA_API.__("error.sql-invalid-parameter"));

                        if (AA_API.getDebug()) {
                            Bukkit.getLogger()
                                  .info(plugin.getConf().getPluginName() + ' ' + AA_API.__("sql.query") + ": " + query
                                      + ", " + AA_API.__("general.variable") + ": " + o.toString());
                        }

                        prep.close();
                        return null;
                    }
                    i++;
                }
                return prep.executeQuery();
            } catch (Throwable e) {
                if (!omitErrorLogs) {
                    Bukkit.getLogger().info("[AdminAnything] There was an error while trying to run a database query.");

                    Bukkit.getLogger()
                          .info("[AdminAnything] query: " + query
                              + ", parameters: " + Utils.implode(params, ", "));
                    Bukkit.getLogger()
                          .info("[AdminAnything] message: "
                              + e.getMessage() + ", cause: " + e.getCause());
                }
            }
        }

        return null;
    } // end of method

    /***
     * closes any DB connection that's still open
     * (used when disabling the plugin)
     */
    void close() {
        try {
            conn.close();
        } catch (Exception e) {}
    } // end of method

} // end class