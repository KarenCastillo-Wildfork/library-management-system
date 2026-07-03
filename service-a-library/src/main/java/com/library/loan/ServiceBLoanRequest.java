package com.library.loan;

/** Body Service A sends to Service B's {@code POST /loans}. */
public record ServiceBLoanRequest(Long userId, Long bookId) {
}
