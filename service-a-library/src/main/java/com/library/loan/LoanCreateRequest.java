package com.library.loan;

import jakarta.validation.constraints.NotNull;

/** What the authenticated client sends; {@code userId} is taken from the JWT, never from the body. */
public record LoanCreateRequest(
        @NotNull(message = "bookId is required") Long bookId
) {
}
