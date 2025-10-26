package com.kylebarker.ev_driver.model;

public class ChargingPointDto 
{
    private Long uid; private String name; private String location;
    private Double pricePerKWh; private String state; private Long lastHealthCheck;
    public ChargingPointDto() {}
    public ChargingPointDto(Long uid,String name,String location,Double pricePerKWh,String state,Long lastHealthCheck){
        this.uid=uid; this.name=name; this.location=location; this.pricePerKWh=pricePerKWh; this.state=state; this.lastHealthCheck=lastHealthCheck;
    }
    public Long getUid(){return uid;} public void setUid(Long uid){this.uid=uid;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public String getLocation(){return location;} public void setLocation(String location){this.location=location;}
    public Double getPricePerKWh(){return pricePerKWh;} public void setPricePerKWh(Double pricePerKWh){this.pricePerKWh=pricePerKWh;}
    public String getState(){return state;} public void setState(String state){this.state=state;}
    public Long getLastHealthCheck(){return lastHealthCheck;} public void setLastHealthCheck(Long lastHealthCheck){this.lastHealthCheck=lastHealthCheck;}
}