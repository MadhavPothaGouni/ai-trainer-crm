package com.aitrainercrm.platform.room.dto;

import com.aitrainercrm.platform.room.entity.Room;
import java.time.Instant;
import java.util.UUID;

public record RoomDto(
        UUID id,
        String label,
        String location,
        Integer capacity,
        Room.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static RoomDto from(Room room) {
        return new RoomDto(
                room.getId(),
                room.getLabel(),
                room.getLocation(),
                room.getCapacity(),
                room.getStatus(),
                room.getNotes(),
                room.getCreatedAt(),
                room.getUpdatedAt());
    }
}
