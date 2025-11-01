package com.kylebarker.ev_driver.model;

public class SimulationStartDto 
{
    private String filePath; 
    private long driverId;
    private String stationId;
    
    public SimulationStartDto() {}
    
    // Getters and Setters for filePath
    public String getFilePath(){return filePath;} 
    public void setFilePath(String filePath){this.filePath=filePath;}
    
    // Getters and Setters for driverId
    public long getDriverId(){return driverId;} 
    public void setDriverId(long driverId){this.driverId=driverId;}
    
    // Getters and Setters for stationId 
    public String getStationId(){return stationId;} 
    public void setStationId(String stationId){this.stationId=stationId;}
}
