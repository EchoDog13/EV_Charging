package com.kylebarker.ev_driver.model;

public class SessionDto 
{
    private String sessionId; private long driverId; private long cpUid; private String status;
    private Long startedAt; private Long endedAt; private Double kwh; private Double euro;
    public SessionDto(){}
    public SessionDto(String sessionId,long driverId,long cpUid,String status,Long startedAt,Long endedAt,Double kwh,Double euro){
        this.sessionId=sessionId; this.driverId=driverId; this.cpUid=cpUid; this.status=status;
        this.startedAt=startedAt; this.endedAt=endedAt; this.kwh=kwh; this.euro=euro;
    }
    public String getSessionId(){return sessionId;} public void setSessionId(String sessionId){this.sessionId=sessionId;}
    public long getDriverId(){return driverId;} public void setDriverId(long driverId){this.driverId=driverId;}
    public long getCpUid(){return cpUid;} public void setCpUid(long cpUid){this.cpUid=cpUid;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public Long getStartedAt(){return startedAt;} public void setStartedAt(Long startedAt){this.startedAt=startedAt;}
    public Long getEndedAt(){return endedAt;} public void setEndedAt(Long endedAt){this.endedAt=endedAt;}
    public Double getKwh(){return kwh;} public void setKwh(Double kwh){this.kwh=kwh;}
    public Double getEuro(){return euro;} public void setEuro(Double euro){this.euro=euro;}
}