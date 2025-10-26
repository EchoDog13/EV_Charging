package com.kylebarker.ev_driver.model;

public class SimulationStatusDto 
{
    private boolean running; private String message;
    public SimulationStatusDto() {}
    public SimulationStatusDto(boolean running,String message){this.running=running; this.message=message;}
    public boolean isRunning(){return running;} public void setRunning(boolean running){this.running=running;}
    public String getMessage(){return message;} public void setMessage(String message){this.message=message;}
}