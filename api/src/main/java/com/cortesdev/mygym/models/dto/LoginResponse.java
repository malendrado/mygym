package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.Role;

public record LoginResponse(
        String token, Long userId, String email, String name, Role role, Long gymId, boolean isNewMember) {}
