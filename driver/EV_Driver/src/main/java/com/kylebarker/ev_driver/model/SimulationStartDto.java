package com.kylebarker.ev_driver.model;

public class SimulationStartDto 
{
    private String filePath; private long driverId;
    public SimulationStartDto() {}
    public String getFilePath(){return filePath;} public void setFilePath(String filePath){this.filePath=filePath;}
    public long getDriverId(){return driverId;} public void setDriverId(long driverId){this.driverId=driverId;}
}