package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Size;

public record RecordVisitRequest(@Size(max = 20) String page, @Size(max = 100) String gymSlug) {}
