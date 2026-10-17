package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GymPhotoCreateRequest(@NotBlank @Size(max = 3_000_000) String data, @Size(max = 160) String caption) {}
