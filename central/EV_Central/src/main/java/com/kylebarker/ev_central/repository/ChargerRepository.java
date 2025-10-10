package com.kylebarker.ev_central.repository;

import com.kylebarker.ev_central.model.Charger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargerRepository extends JpaRepository<Charger, Long> {}