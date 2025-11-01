package com.kylebarker.ev_driver;

import com.kylebarker.ev_driver.model.SimulationStartDto;
import com.kylebarker.ev_driver.model.SimulationStatusDto;

public class Driver implements Runnable
 {
    
    private final long driverId;
    private final CentralServer centralServer;
    private String currentStatus;
    private static final String TARGET_STATION = "Station 001"; 

    public Driver(long driverId, CentralServer centralServer) {
        this.driverId = driverId;
        this.centralServer = centralServer;
        this.currentStatus = "Idle";
    }

    @Override
    public void run() {
        System.out.println("Driver " + driverId + " started run sequence.");
        
        SimulationStartDto startRequest = new SimulationStartDto();
        startRequest.setDriverId(this.driverId);
        startRequest.setStationId(TARGET_STATION); 
        startRequest.setFilePath("/log_" + this.driverId + ".csv");

        SimulationStatusDto response = centralServer.startSimulation(startRequest);
        
        if (response.isRunning()) {
            this.currentStatus = "Running: " + response.getMessage();
            System.out.println("SUCCESS: Driver " + driverId + " secured " + TARGET_STATION);
            
            checkCurrentStatus();
            
        } else {
            this.currentStatus = "Failed: " + response.getMessage();
            System.err.println("FAILURE: Driver " + driverId + " failed to reserve. Reason: " + response.getMessage());
        }
    }

    private void checkCurrentStatus() {
        try {
            Thread.sleep(500); 
        } catch (InterruptedException e) {
             Thread.currentThread().interrupt();
        }
        
        SimulationStatusDto statusCheck = centralServer.getSimulationStatus(TARGET_STATION, this.driverId);
        System.out.println("  --> " + driverId + " Status Check: " + statusCheck.getMessage());
    }

    public String getFinalStatus() {
        return "Driver " + driverId + " final state: " + currentStatus;
    }
}