package com.esentri.microservices.doag.demo.service.two;

import com.esentri.microservices.doag.demo.service.two.phonebookservice.PhonebookService;
import io.vertx.core.Vertx;

/**
 * Created by dominikgaller on 15.11.16.
 */
public class IdeRunner {
  
  
  public static void main(String[] args) {
  
    Vertx.vertx().deployVerticle(PhonebookService.class.getName(), completionFuture -> {
      if(!completionFuture.succeeded()) {
        throw new RuntimeException("Error while deploying service");
      }
    });
  }
}
