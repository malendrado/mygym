package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Datos de mygym como empresa. Una sola fila (id = 1); ver {@code MygymSettingsService} para los valores de respaldo. */
@Entity
@Table(name = "mygym_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MygymSettings {

    public static final short ID = 1;

    @Id
    private Short id;

    private String whatsapp;
    private String contactEmail;
    private String companyName;
    private String companyRut;
    private String legalRepName;
    private String legalRepRut;
    private String companyAddress;
    private String bankHolder;
    private String bankRut;
    private String bankName;
    private String bankAccountType;
    private String bankAccountNumber;
    private String bankEmail;
    private Instant updatedAt;
    private Long updatedBy;
}
