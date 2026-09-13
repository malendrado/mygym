package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GymPhotoCreateRequest(@NotBlank String data, @Size(max = 160) String caption) {}
