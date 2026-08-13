package com.aitrainercrm.platform.booking.dto;

import com.aitrainercrm.platform.booking.entity.BookingLink;
import com.aitrainercrm.platform.booking.entity.BookingSlot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record BookingLinkDto(
        UUID id,
        UUID ownerId,
        String title,
        String description,
        int durationMinutes,
        String slug,
        boolean active,
        List<BookingSlotDto> slots,
        Instant createdAt,
        Instant updatedAt) {

    public static BookingLinkDto from(BookingLink link, List<BookingSlot> slots) {
        return BookingLinkDto.builder()
                .id(link.getId())
                .ownerId(link.getOwnerId())
                .title(link.getTitle())
                .description(link.getDescription())
                .durationMinutes(link.getDurationMinutes())
                .slug(link.getSlug())
                .active(link.isActive())
                .slots(slots.stream().map(BookingSlotDto::from).toList())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }
}
