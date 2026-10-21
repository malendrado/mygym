package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Codigo de un solo uso enviado al correo del firmante del contrato. Se guarda solo su hash. */
@Entity
@Table(name = "gym_contract_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymContractCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private Long userId;

    private String codeHash;

    private String renderedSha256;

    private String signerName;

    private String signerRut;

    private String signerCapacity;

    private Instant expiresAt;

    private int attempts;

    private Instant usedAt;

    private Instant createdAt;
}
