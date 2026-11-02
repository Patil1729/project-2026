package com.party.gallery.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record GalleryMetadata(@NotBlank @Size(max=150) String title, @NotBlank @Size(max=2000) String description,
                              @NotNull LocalDate eventDate, boolean published, @NotNull @PositiveOrZero Long version) { }
