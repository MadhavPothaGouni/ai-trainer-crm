package com.aitrainercrm.platform.booking.repository;

import com.aitrainercrm.platform.booking.entity.BookingSlot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, UUID> {

    List<BookingSlot> findByBookingLinkIdOrderByStartAtAsc(UUID bookingLinkId);

    Optional<BookingSlot> findByIdAndBookingLinkId(UUID id, UUID bookingLinkId);

    /** Friendlier pre-check ahead of the real uq_booking_slots_link_start constraint (V33) - same "check in memory, DB backstops it" pattern uq_course_enrollments_course_user_active's duplicate check already uses. */
    boolean existsByBookingLinkIdAndStartAtAndStatusNot(UUID bookingLinkId, Instant startAt, BookingSlot.Status status);
}
