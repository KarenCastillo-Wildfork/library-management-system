package com.library.book;

/**
 * Response for the internal book-availability check. {@code exists=false} (rather
 * than an HTTP 404) lets Service B distinguish "book does not exist" — a normal
 * business outcome it must handle — from a transport/server error on our side.
 */
public record BookAvailabilityResponse(Long bookId, boolean exists, int availableCopies) {
}
