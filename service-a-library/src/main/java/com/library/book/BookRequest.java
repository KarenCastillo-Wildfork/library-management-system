package com.library.book;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookRequest(
        @NotBlank(message = "title is required") String title,
        @NotBlank(message = "author is required") String author,
        @NotBlank(message = "isbn is required") String isbn,
        @NotNull(message = "year is required")
        @Min(value = 0, message = "year must be a positive number") Integer year,
        @NotBlank(message = "genre is required") String genre,
        @NotNull(message = "totalCopies is required")
        @Min(value = 0, message = "totalCopies cannot be negative") Integer totalCopies
) {
}
