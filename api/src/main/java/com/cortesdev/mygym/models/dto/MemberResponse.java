package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.Role;
import java.time.Instant;

public record MemberResponse(
        Long id, String name, String email, Role role, Long gymId, boolean active, Instant createdAt) {}
