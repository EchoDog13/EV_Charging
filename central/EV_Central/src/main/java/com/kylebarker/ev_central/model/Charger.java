package com.kylebarker.ev_central.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Charger {
    @Id
    @Column(nullable = false)
    private Long uid;

    @Column(nullable = false)
    private double pricePerKW;

    private String location;

    @Enumerated(EnumType.STRING)
    private chargerState state;

    private Long lastHealthCheck;

    public Charger() {
    };

    public Charger(double pricePerKW, String location, chargerState state) {
        this.pricePerKW = pricePerKW;
        this.location = location;
        this.state = state;
    }

    public Long getUid() {
        return uid;
    }

    public double getPricePerKW() {
        return pricePerKW;
    }

    public void setPricePerKW(double pricePerKW) {
        this.pricePerKW = pricePerKW;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public chargerState getState() {
        return state;
    }

    public void setState(chargerState state) {
        this.state = state;
    }

    public Long getLastHealthCheck() {
        return lastHealthCheck;
    }

    public void setLastHealthCheck(Long lastHealthCheck) {
        this.lastHealthCheck = lastHealthCheck;
    }

    @Override
    public String toString() {
        return "Charger{" +
                "uid=" + uid +
                ", pricePerKW=" + pricePerKW +
                ", location='" + location + '\'' +
                ", state=" + state +
                '}';
    }
}
