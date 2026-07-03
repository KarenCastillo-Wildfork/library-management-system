package com.library.book;

import com.library.common.ConflictException;
import com.library.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business logic for the book catalog, including the filtered/paginated listing used by the client. */
@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public Page<BookResponse> findAll(String author, String genre, Boolean available, Pageable pageable) {
        return bookRepository.findAll(BookSpecifications.filter(author, genre, available), pageable)
                .map(BookResponse::from);
    }

    @Transactional(readOnly = true)
    public BookResponse findById(Long id) {
        return BookResponse.from(getOrThrow(id));
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new ConflictException("A book with ISBN " + request.isbn() + " already exists");
        }
        Book book = new Book(request.title(), request.author(), request.isbn(),
                request.year(), request.genre(), request.totalCopies());
        return BookResponse.from(bookRepository.save(book));
    }

    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = getOrThrow(id);

        if (!book.getIsbn().equals(request.isbn()) && bookRepository.existsByIsbn(request.isbn())) {
            throw new ConflictException("A book with ISBN " + request.isbn() + " already exists");
        }

        int copiesOnLoan = book.getTotalCopies() - book.getAvailableCopies();
        if (request.totalCopies() < copiesOnLoan) {
            throw new ConflictException("Cannot set totalCopies below the " + copiesOnLoan + " copies currently on loan");
        }

        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setIsbn(request.isbn());
        book.setPublicationYear(request.year());
        book.setGenre(request.genre());
        book.setAvailableCopies(request.totalCopies() - copiesOnLoan);
        book.setTotalCopies(request.totalCopies());

        return BookResponse.from(bookRepository.save(book));
    }

    @Transactional
    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new NotFoundException("Book not found with id " + id);
        }
        bookRepository.deleteById(id);
    }

    private Book getOrThrow(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Book not found with id " + id));
    }
}
