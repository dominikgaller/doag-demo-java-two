package com.esentri.microservices.doag.demo.service.two.phonebookservice;

import com.esentri.microservices.doag.demo.service.two.IdeRunner;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

class EntryDatabasePopulator {
  
  private Vertx vertxInstance;
  
  private String dbName;
  
  private static final String ENTRIESPATH = "entries.json";
  
  EntryDatabasePopulator (Vertx vertx, String dbname) {
    
    this.setVertxInstance(vertx);
    this.setDBName(dbname);
  }
  
  void populateDatabase () {
    
    initDBClient();
  }
  
  private void initDBClient () {
    
    final JDBCClient client = JDBCClient.createShared(this.vertxInstance, createConfig(), this.dbName);
    client.getConnection(conn -> {
      if (conn.failed()) {
        throw new IllegalStateException(conn.cause().getMessage());
      }
      setUpDB(conn);
    });
  }
  
  private void setUpDB (AsyncResult<SQLConnection> conn) {
    
    createEntryDB(conn);
    insertEntries(conn);
  }
  
  private void createEntryDB (AsyncResult<SQLConnection> conn) {
    
    SQLConnection connection = conn.result();
    connection.execute(
            "create table entry(id integer identity primary key, name varchar(255), number varchar(255))", res -> {
              if (res.failed()) {
                throw new IllegalStateException(conn.cause().getMessage());
              }
              connection.close();
            });
  }
  
  private void insertEntries (AsyncResult<SQLConnection> conn) {
    
    JsonArray arr = readJsonArrayFile(ENTRIESPATH);
    arr.forEach(elem -> {
      JsonObject val = new JsonObject(elem.toString());
      insertEntry(conn, val.getString("name"), val.getString("number"));
    });
  }
  
  private void insertEntry (AsyncResult<SQLConnection> conn, String name, String number) {
    
    SQLConnection connection = conn.result();
    connection.execute("insert into entry (name, number) values ('" + name + "','" + number + "')", res -> {
      if (res.failed()) {
        throw new IllegalStateException(res.cause().getMessage());
      }
      connection.close();
    });
  }
  
  private JsonArray readJsonArrayFile (String path) {
    
    String content = readDataFile(path);
    return new JsonArray(content);
  }
  
  private JsonObject createConfig () {
    
    return new JsonObject().put("url", "jdbc:hsqldb:mem:" + this.dbName + "?shutdown=false")
            .put("driver_class", "org.hsqldb.jdbcDriver").put("max_pool_size", 30);
  }
  
  private void setVertxInstance (Vertx vertxInstance) {
    
    this.vertxInstance = vertxInstance;
  }
  
  private void setDBName (String dbName) {
    
    this.dbName = dbName;
  }
  
  private static String readDataFile (String filename) {
    
    ClassLoader classLoader = IdeRunner.class.getClassLoader();
    try (InputStream in = classLoader.getResourceAsStream(filename);
         BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String str;
      StringBuilder sb = new StringBuilder(8192);
      while ((str = r.readLine()) != null) {
        sb.append(str);
      }
      return sb.toString();
    } catch (IOException ioe) {
      throw new IllegalStateException("Error while reading input config");
    }
  }
  
}
