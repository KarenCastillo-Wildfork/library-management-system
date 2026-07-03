package com.library.book;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Book catalog. Reads are public (no auth required, per the PDF: "usuario normal:
 * lectura"); writes (create/update/delete) are ADMIN-only.
 */
@RestController
@RequestMapping("/api/books")
@Tag(name = "Books", description = "Book catalog: CRUD, filters and pagination")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    @Operation(summary = "List books, optionally filtered by author/genre/availability, paginated")
    public Page<BookResponse> findAll(
            @Parameter(description = "Case-insensitive partial match on author") @RequestParam(required = false) String author,
            @Parameter(description = "Case-insensitive partial match on genre") @RequestParam(required = false) String genre,
            @Parameter(description = "true = only books with available copies, false = only fully checked out") @RequestParam(required = false) Boolean available,
            Pageable pageable) {
        return bookService.findAll(author, genre, available, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a book by id")
    public BookResponse findById(@PathVariable Long id) {
        return bookService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a book (ADMIN only)")
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a book (ADMIN only)")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a book (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
