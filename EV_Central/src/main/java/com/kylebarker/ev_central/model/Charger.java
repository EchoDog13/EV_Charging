package com.kylebarker.ev_central.model;

import jakarta.persistence.*;

@Entity
public class Charger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private double pricePerKW;

    private String location;

    @Enumerated(EnumType.STRING)
    private chargerState state;

    public Charger(){};

    public Charger(double pricePerKW, String location, chargerState state) {
        this.pricePerKW = pricePerKW;
        this.location = location;
        this.state = state;
    }

    public Long getId() {
        return id;
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
}
