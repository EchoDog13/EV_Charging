package com.kylebarker.ev_driver;

import com.kylebarker.ev_driver.model.SimulationStartDto;
import com.kylebarker.ev_driver.model.SimulationStatusDto;
import java.util.concurrent.ConcurrentHashMap;

public class CentralServer 
{
    
    private final ConcurrentHashMap<String, Long> reservedStations = new ConcurrentHashMap<>();

    public SimulationStatusDto startSimulation(SimulationStartDto startRequest) {
        String stationId = startRequest.getStationId();
        long driverId = startRequest.getDriverId();
        
        Long existingDriver = reservedStations.putIfAbsent(stationId, driverId);

        if (existingDriver != null) {
            return new SimulationStatusDto
            (
                false, 
                "FAILURE: Station " + stationId + " is already reserved by Driver " + existingDriver + "."
            );
        }
        
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        System.out.println("   [SERVER LOG]: Station " + stationId + " successfully RESERVED by Driver " + driverId);

        return new SimulationStatusDto(
            true, 
            "SUCCESS: Station " + stationId + " is reserved. Starting charging simulation."
        );
    }
    
    public SimulationStatusDto getSimulationStatus(String stationId, long driverId) {
        if (reservedStations.containsKey(stationId) && reservedStations.get(stationId).equals(driverId)) {
            return new SimulationStatusDto(true, "Driver " + driverId + " is the current active charger at " + stationId + ".");
        } else if (reservedStations.containsKey(stationId)) {
             return new SimulationStatusDto(false, "Station " + stationId + " is reserved by a different driver.");
        } else {
            return new SimulationStatusDto(false, "Station is currently available.");
        }
    }
}