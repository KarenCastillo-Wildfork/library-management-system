package com.library.book;

import org.springframework.data.jpa.domain.Specification;

/** Composable filter predicates for {@code GET /api/books} (author, genre, availability). */
public final class BookSpecifications {

    private BookSpecifications() {
    }

    public static Specification<Book> hasAuthor(String author) {
        return (root, query, cb) -> author == null || author.isBlank()
                ? null
                : cb.like(cb.lower(root.get("author")), "%" + author.toLowerCase() + "%");
    }

    public static Specification<Book> hasGenre(String genre) {
        return (root, query, cb) -> genre == null || genre.isBlank()
                ? null
                : cb.like(cb.lower(root.get("genre")), "%" + genre.toLowerCase() + "%");
    }

    public static Specification<Book> isAvailable(Boolean available) {
        return (root, query, cb) -> {
            if (available == null) {
                return null;
            }
            return available
                    ? cb.greaterThan(root.get("availableCopies"), 0)
                    : cb.equal(root.get("availableCopies"), 0);
        };
    }

    public static Specification<Book> filter(String author, String genre, Boolean available) {
        return Specification.allOf(hasAuthor(author), hasGenre(genre), isAvailable(available));
    }
}
