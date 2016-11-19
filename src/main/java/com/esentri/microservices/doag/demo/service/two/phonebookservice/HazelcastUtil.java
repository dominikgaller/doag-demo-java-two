package com.esentri.microservices.doag.demo.service.two.phonebookservice;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;

class HazelcastUtil {
  
  private static final String HAZELCAST_INSTANCE_NAME = "doag-verx-demo-cluster";
  
  HazelcastUtil () {
    
  }
  
  void createHazelcastCluster (Future<HazelcastInstance> hazelcastInstanceFuture) {
    
    Vertx.vertx().executeBlocking(handler -> {
      Config hazelcastConfig = setHazelcastConfig();
      HazelcastInstance hazelcastInstance = Hazelcast.getOrCreateHazelcastInstance(hazelcastConfig);
      hazelcastInstanceFuture.complete(hazelcastInstance);
    }, res -> {
      if (!res.succeeded()) {
        throw new RuntimeException("Error while creating hazelcast cluster");
      }
    });
  }
  
  private Config setHazelcastConfig () {
    
    Config hazelcastConfig = new Config();
    hazelcastConfig.setInstanceName(HAZELCAST_INSTANCE_NAME);
    tcpIpConfiguration(hazelcastConfig);
    networkConfiguration(hazelcastConfig);
    
    return hazelcastConfig;
  }
  
  private void networkConfiguration (Config hazelcastConfig) {
    
    NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
    networkConfig.addOutboundPortDefinition("52601-52699");
  }
  
  private void tcpIpConfiguration (Config hazelcastConfig) {
    
    TcpIpConfig tcpIpConfig = new TcpIpConfig();
    tcpIpConfig.setEnabled(true);
    hazelcastConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
    List<String> members = new ArrayList<>();
    members.add("localhost");
    members.add("127.0.0.1");
    tcpIpConfig.setMembers(members);
    hazelcastConfig.getNetworkConfig().getJoin().setTcpIpConfig(tcpIpConfig);
  }
}