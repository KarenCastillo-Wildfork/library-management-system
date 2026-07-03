package com.library.book;

public record BookResponse(
        Long id,
        String title,
        String author,
        String isbn,
        Integer year,
        String genre,
        Integer totalCopies,
        Integer availableCopies
) {
    public static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(),
                book.getPublicationYear(), book.getGenre(), book.getTotalCopies(), book.getAvailableCopies());
    }
}
