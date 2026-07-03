package com.library.book;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoint. Service B calls this before persisting a loan, to
 * confirm the book exists and has available copies, per the PDF requirement:
 * "Valida con el Servicio A que el libro existe y tiene copias disponibles". Secured
 * by {@link com.library.security.InternalApiKeyFilter}, not JWT.
 */
@RestController
@RequestMapping("/internal/books")
@Tag(name = "Internal", description = "Service-to-service endpoints (shared-secret auth, not JWT)")
public class InternalBookController {

    private final BookRepository bookRepository;

    public InternalBookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Check whether a book exists and has available copies (called by Service B)")
    public BookAvailabilityResponse checkAvailability(@PathVariable Long id) {
        return bookRepository.findById(id)
                .map(book -> new BookAvailabilityResponse(book.getId(), true, book.getAvailableCopies()))
                .orElseGet(() -> new BookAvailabilityResponse(id, false, 0));
    }
}
