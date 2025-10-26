package com.kylebarker.ev_driver.model;

public class CreateChargeRequestDto 
{
    private long driverId; private long cpUid;
    public CreateChargeRequestDto() {}
    public CreateChargeRequestDto(long driverId,long cpUid){this.driverId=driverId; this.cpUid=cpUid;}
    public long getDriverId(){return driverId;} public void setDriverId(long driverId){this.driverId=driverId;}
    public long getCpUid(){return cpUid;} public void setCpUid(long cpUid){this.cpUid=cpUid;}
}