package com.library.loan;

import java.time.Instant;

/**
 * Wire representation of a loan as persisted and returned by Service B. Field names
 * match Service B's JSON exactly (camelCase on both sides) so this class can be used
 * directly as the Jackson deserialization target for its responses.
 */
public record LoanDto(
        Long id,
        Long userId,
        Long bookId,
        Instant loanDate,
        Instant dueDate,
        Instant returnDate,
        LoanStatus status
) {
}
