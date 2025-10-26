package com.kylebarker.ev_driver.model;

public class ChargeRequestStatusDto {
    private String requestId;
    private String status;
    private Long driverId;
    private Long cpUid;
    private String sessionId;

    public ChargeRequestStatusDto() {}
    public ChargeRequestStatusDto(String requestId, String status, Long driverId, Long cpUid, String sessionId) {
        this.requestId = requestId; this.status = status; this.driverId = driverId; this.cpUid = cpUid; this.sessionId = sessionId;
    }
    public String getRequestId(){return requestId;}
    public void setRequestId(String requestId){this.requestId=requestId;}
    public String getStatus(){return status;}
    public void setStatus(String status){this.status=status;}
    public Long getDriverId(){return driverId;}
    public void setDriverId(Long driverId){this.driverId=driverId;}
    public Long getCpUid(){return cpUid;}
    public void setCpUid(Long cpUid){this.cpUid=cpUid;}
    public String getSessionId(){return sessionId;}
    public void setSessionId(String sessionId){this.sessionId=sessionId;}
}