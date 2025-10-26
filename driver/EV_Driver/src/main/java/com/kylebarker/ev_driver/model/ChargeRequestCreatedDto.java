package com.kylebarker.ev_driver.model;

public class ChargeRequestCreatedDto {
    private String requestId;
    public ChargeRequestCreatedDto() {}
    public ChargeRequestCreatedDto(String requestId){this.requestId=requestId;}
    public String getRequestId(){return requestId;} public void setRequestId(String requestId){this.requestId=requestId;}
}
