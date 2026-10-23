package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.MygymSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MygymSettingsRepository extends JpaRepository<MygymSettings, Short> {}
