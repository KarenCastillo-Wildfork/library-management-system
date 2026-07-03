package com.library.book;

import com.library.common.ConflictException;
import com.library.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(bookRepository);
    }

    @Test
    void create_throwsConflict_whenIsbnAlreadyExists() {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "978-0132350884", 2008, "Software", 3);
        when(bookRepository.existsByIsbn("978-0132350884")).thenReturn(true);

        assertThatThrownBy(() -> bookService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("978-0132350884");

        verify(bookRepository, never()).save(any());
    }

    @Test
    void create_savesBookWithAvailableCopiesEqualToTotal() {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "978-0132350884", 2008, "Software", 3);
        when(bookRepository.existsByIsbn("978-0132350884")).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookResponse response = bookService.create(request);

        assertThat(response.totalCopies()).isEqualTo(3);
        assertThat(response.availableCopies()).isEqualTo(3);
        assertThat(response.isbn()).isEqualTo("978-0132350884");
    }

    @Test
    void findById_throwsNotFound_whenBookDoesNotExist() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_rejectsLoweringTotalCopiesBelowCopiesCurrentlyOnLoan() {
        Book existing = new Book("Clean Code", "Robert C. Martin", "978-0132350884", 2008, "Software", 5);
        existing.borrowOneCopy();
        existing.borrowOneCopy();
        existing.borrowOneCopy(); // 3 on loan, 2 available
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));

        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "978-0132350884", 2008, "Software", 2);

        assertThatThrownBy(() -> bookService.update(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("on loan");
    }

    @Test
    void delete_throwsNotFound_whenBookDoesNotExist() {
        when(bookRepository.existsById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> bookService.delete(1L)).isInstanceOf(NotFoundException.class);
        verify(bookRepository, never()).deleteById(any());
    }
}
