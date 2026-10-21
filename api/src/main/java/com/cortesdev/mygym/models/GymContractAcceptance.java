package com.cortesdev.mygym.models;

import jakarta.persistence.Column;
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

/**
 * Respaldo de que el administrador de un gimnasio firmo el contrato (firma electronica simple, Ley 19.799). Solo se inserta:
 * la base impide modificarla o borrarla (trigger de V58) y no tiene clave foranea al gimnasio para sobrevivir a su desvinculacion.
 */
@Entity
@Table(name = "gym_contract_acceptance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymContractAcceptance {

    public static final String PERSON_OWNER = "PERSON_OWNER";
    public static final String LEGAL_REP = "LEGAL_REP";
    public static final String ATTORNEY = "ATTORNEY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private String contractVersion;

    @Column(columnDefinition = "text")
    private String renderedText;

    private String renderedSha256;

    private Long signerUserId;

    private String signerEmail;

    private String signerName;

    private String signerRut;

    private String signerCapacity;

    private boolean adultDeclared;

    private boolean powersDeclared;

    private String gymName;

    private String gymRut;

    private String billingKind;

    private String planCode;

    private int netPriceClp;

    private Instant codeSentAt;

    private Instant signedAt;

    private String ip;

    private String userAgent;
}
