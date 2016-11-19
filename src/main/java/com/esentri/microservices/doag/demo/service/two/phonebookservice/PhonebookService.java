package com.esentri.microservices.doag.demo.service.two.phonebookservice;

import com.esentri.microservices.doag.demo.service.two.phonebookservice.entities.Entry;
import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class PhonebookService extends AbstractVerticle {
  
  private final HazelcastUtil hazelcastUtil = new HazelcastUtil();
  
  private EventBus eventBus;
  
  private JDBCClient jdbcClient;
  
  private static final String SHARED_DBNAME = "entries";
  
  private static final String ENTRIES_REQUEST_TOPIC = "esentri.entries.request";
  
  private static final String ENTRIES_REPLY_TOPIC = "esentri.entries.display";
  
  private static final String DELETE_ENTRY_TOPIC = "esentri.entries.delete";
  
  private static final String ADD_ENTRY_TOPIC = "esentri.entries.add";
  
  private Logger logger;
  
  public void start (Future<Void> completionFuture) {
    
    logger = LoggerFactory.getLogger(PhonebookService.class);
    
    Future<HazelcastInstance> hazelcastInstanceFuture = Future.future();
    hazelcastInstanceFuture.setHandler(ref -> clusterCreatedHandler(ref, completionFuture));
    hazelcastUtil.createHazelcastCluster(hazelcastInstanceFuture);
  }
  
  private void initializePhonebookService (Future<Void> completionFuture) {
    
    EntryDatabasePopulator edp = new EntryDatabasePopulator(this.vertx, SHARED_DBNAME);
    edp.populateDatabase();
    
    this.eventBus = this.vertx.eventBus();
    this.jdbcClient = JDBCClient.createShared(vertx, new JsonObject(), SHARED_DBNAME);
    eventBus.consumer(ENTRIES_REQUEST_TOPIC, this::displayEntriesHandler);
    eventBus.consumer(DELETE_ENTRY_TOPIC, this::deleteEntryHandler);
    eventBus.consumer(ADD_ENTRY_TOPIC, this::addEntryHandler);
    completionFuture.complete();
  }
  
  private void addEntryHandler (Message<String> message) {
    
    logger.info("Request for creating new entry received.");
    logger.info("Payload for creating new entry is: " + message.body());
    JsonObject msg = new JsonObject(message.body());
    String sessionId = extractSessionId(msg);
    Entry e = makeEntryFromJson(msg.getJsonObject("entry"));
    addEntry(e, sessionId);
  }
  
  private void addEntry (Entry e, String sessionId) {
    
    this.jdbcClient.getConnection(conn -> {
      if (conn.failed()) {
        throw new IllegalStateException(conn.cause().getMessage());
      }
      SQLConnection connection = conn.result();
      addEntrySQL(connection, e, sessionId);
    });
  }
  
  private void addEntrySQL (SQLConnection conn, Entry e, String sessionId) {
    
    conn.execute("insert into entry (name, number) values ('" + e.getName() + "','" + e.getNumber() + "')", rs -> {
      if (rs.failed()) {
        throw new RuntimeException(rs.cause().getMessage());
      }
      logger.info("Entry added.");
      conn.close();
      internalRefreshDisplay(sessionId);
    });
  }
  
  private Entry makeEntryFromJson (JsonObject json) {
    
    String name = json.getString("name");
    String number = json.getString("number");
    Entry e = new Entry(name, number);
    if (json.containsKey("id")) {
      e.setId(json.getLong("id"));
    }
    return e;
  }
  
  private void deleteEntryHandler (Message<String> message) {
    
    logger.info("Request for deleting existing entry received.");
    logger.info(message.body());
    JsonObject msg = new JsonObject(message.body());
    String sessionId = extractSessionId(msg);
    JsonObject jsonEntry = msg.getJsonObject("entry");
    deleteEntry(makeEntryFromJson(jsonEntry), sessionId);
  }
  
  private void deleteEntry (Entry e, String sessionId) {
    
    this.jdbcClient.getConnection(conn -> {
      if (conn.failed()) {
        throw new IllegalStateException(conn.cause().getMessage());
      }
      SQLConnection connection = conn.result();
      deleteEntrySQL(connection, e, sessionId);
    });
  }
  
  private void deleteEntrySQL (SQLConnection conn, Entry e, String sessionId) {
    
    conn.execute("delete from entry where id = " + e.getId(), rs -> {
      if (rs.failed()) {
        throw new RuntimeException(rs.cause().getMessage());
      }
      conn.close();
      eventBus.send("esentri.testreply", new JsonObject().put("succeeded", true).toString());
      internalRefreshDisplay(sessionId);
    });
  }
  
  private String extractSessionId (JsonObject json) {
    
    if (json.containsKey("sessionId")) {
      return json.getString("sessionId");
    } else {
      throw new IllegalArgumentException("Malformed session token.");
    }
  }
  
  private void displayEntriesHandler (Message<String> message) {
    
    logger.info("Request for displaying entries received.");
    logger.info("Payload for displaying entries is: " + message.body());
    String sessionId = extractSessionId(new JsonObject(message.body()));
    entitiesSelection(sessionId);
  }
  
  private void entitiesSelection (String sessionId) {
    
    this.jdbcClient.getConnection(conn -> {
      if (conn.failed()) {
        throw new IllegalStateException(conn.cause().getMessage());
      }
      SQLConnection connection = conn.result();
      selectAllEntries(connection, sessionId);
    });
  }
  
  private void internalRefreshDisplay (String sessionId) {
    
    logger.info("Entryset changed. Will refresh users view.");
    entitiesSelection(sessionId);
  }
  
  private void selectAllEntries (SQLConnection conn, String sessionId) {
    
    JsonArray reply = new JsonArray();
    conn.query("select * from entry", rs -> {
      if (rs.failed()) {
        throw new RuntimeException(rs.cause().getMessage());
      }
      for (JsonArray line : rs.result().getResults()) {
        reply.add(makeEntryJsonObjectFromSQLResult(line));
      }
      this.eventBus.send(ENTRIES_REPLY_TOPIC + ":" + sessionId, reply.toString());
      conn.close();
    });
  }
  
  private JsonObject makeEntryJsonObjectFromSQLResult (JsonArray sqlResult) {
    
    int id = sqlResult.getInteger(0);
    String name = sqlResult.getString(1);
    String number = sqlResult.getString(2);
    return new JsonObject()
            .put("id", id)
            .put("name", name)
            .put("number", number);
  }
  
  private void clusterCreatedHandler (AsyncResult<HazelcastInstance> hazelcastInstanceAsyncResult, Future<Void>
          completionFuture) {
    
    if (!hazelcastInstanceAsyncResult.succeeded()) {
      throw new IllegalStateException("Error while creating hazelcast cluster");
    }
    
    ClusterManager clusterManager = new HazelcastClusterManager(hazelcastInstanceAsyncResult.result());
    
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setClusterManager(clusterManager);
    Vertx.clusteredVertx(vertxOptions, res -> {
      if (res.succeeded()) {
        this.vertx = res.result();
        this.eventBus = vertx.eventBus();
        initializePhonebookService(completionFuture);
      } else {
        completionFuture.fail("Failed: " + res.cause());
      }
    });
  }
  
}
