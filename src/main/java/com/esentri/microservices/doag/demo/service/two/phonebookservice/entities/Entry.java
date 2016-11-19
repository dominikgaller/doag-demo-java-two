package com.esentri.microservices.doag.demo.service.two.phonebookservice.entities;

public class Entry {
  
  private long id;
  
  private String name;
  
  private String number;
  
  public Entry (String name, String number) {
    
    this.setName(name);
    this.setNumber(number);
  }
  
  public long getId () {
    
    return id;
  }
  
  public void setId (long id) {
    
    this.id = id;
  }
  
  public String getName () {
    
    return name;
  }
  
  private void setName (String name) {
    
    this.name = name;
  }
  
  public String getNumber () {
    
    return number;
  }
  
  private void setNumber (String number) {
    
    this.number = number;
  }
  
}
