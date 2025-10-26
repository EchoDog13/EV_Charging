package com.kylebarker.ev_driver.model;

public class TicketDto 
{
    private String sessionId; private long driverId; private long cpUid;
    private double kwh; private double euro; private Long startedAt; private Long endedAt;
    public TicketDto(){}
    public TicketDto(String sessionId,long driverId,long cpUid,double kwh,double euro,Long startedAt,Long endedAt){
        this.sessionId=sessionId; this.driverId=driverId; this.cpUid=cpUid; this.kwh=kwh; this.euro=euro; this.startedAt=startedAt; this.endedAt=endedAt;
    }
    public String getSessionId(){return sessionId;} public void setSessionId(String sessionId){this.sessionId=sessionId;}
    public long getDriverId(){return driverId;} public void setDriverId(long driverId){this.driverId=driverId;}
    public long getCpUid(){return cpUid;} public void setCpUid(long cpUid){this.cpUid=cpUid;}
    public double getKwh(){return kwh;} public void setKwh(double kwh){this.kwh=kwh;}
    public double getEuro(){return euro;} public void setEuro(double euro){this.euro=euro;}
    public Long getStartedAt(){return startedAt;} public void setStartedAt(Long startedAt){this.startedAt=startedAt;}
    public Long getEndedAt(){return endedAt;} public void setEndedAt(Long endedAt){this.endedAt=endedAt;}
}