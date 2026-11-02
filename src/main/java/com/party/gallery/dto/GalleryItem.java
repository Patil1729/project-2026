package com.party.gallery.dto;
import java.time.*;
public record GalleryItem(Long id, Long organizationUnitId, String areaName, String title, String description,
                          LocalDate eventDate, boolean published, Long version, LocalDateTime updatedAt) { }
