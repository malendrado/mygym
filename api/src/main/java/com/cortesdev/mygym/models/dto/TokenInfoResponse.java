package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.AuthTokenPurpose;

/** displayName viene de la AppUser ya existente (INVITE/PASSWORD_RESET) o de pendingName (SELF_REGISTER). */
public record TokenInfoResponse(AuthTokenPurpose purpose, String displayName, String email) {}
