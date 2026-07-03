package com.library.loan;

import com.library.book.Book;
import com.library.book.BookRepository;
import com.library.common.DownstreamServiceException;
import com.library.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the cross-service orchestration in {@link LoanService}. The
 * Service B HTTP client is mocked so these tests run in isolation and also exercise
 * the "Service B is down/rejects the request" paths that are hard to trigger with a
 * real HTTP call in a unit test.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private ServiceBClient serviceBClient;

    @Mock
    private BookRepository bookRepository;

    private LoanService loanService;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(serviceBClient, bookRepository);
    }

    @Test
    void createLoan_decrementsAvailableCopies_onlyAfterServiceBConfirms() {
        Book book = new Book("Clean Code", "Robert C. Martin", "978-1", 2008, "Software", 2);
        LoanDto loanDto = new LoanDto(10L, 1L, 5L, Instant.now(), Instant.now(), null, LoanStatus.ACTIVE);

        when(bookRepository.existsById(5L)).thenReturn(true);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(serviceBClient.createLoan(1L, 5L)).thenReturn(loanDto);

        LoanDto result = loanService.createLoan(1L, 5L);

        assertThat(result).isEqualTo(loanDto);
        assertThat(book.getAvailableCopies()).isEqualTo(1);
        verify(bookRepository).save(book);
    }

    @Test
    void createLoan_throwsNotFound_whenBookDoesNotExistLocally_andNeverCallsServiceB() {
        when(bookRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> loanService.createLoan(1L, 999L)).isInstanceOf(NotFoundException.class);

        verifyNoInteractions(serviceBClient);
    }

    @Test
    void createLoan_propagatesDownstreamException_whenServiceBRejectsAsUnavailable() {
        when(bookRepository.existsById(5L)).thenReturn(true);
        when(serviceBClient.createLoan(1L, 5L))
                .thenThrow(new DownstreamServiceException("no copies available", HttpStatus.CONFLICT));

        assertThatThrownBy(() -> loanService.createLoan(1L, 5L))
                .isInstanceOf(DownstreamServiceException.class)
                .extracting(ex -> ((DownstreamServiceException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        // Copies must NOT be touched locally if Service B never confirmed the loan.
        verify(bookRepository, never()).save(any());
    }

    @Test
    void createLoan_propagatesDownstreamException_whenServiceBIsUnreachable() {
        when(bookRepository.existsById(5L)).thenReturn(true);
        when(serviceBClient.createLoan(anyLong(), anyLong()))
                .thenThrow(new DownstreamServiceException("unavailable", HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> loanService.createLoan(1L, 5L))
                .isInstanceOf(DownstreamServiceException.class);

        verify(bookRepository, never()).save(any());
    }

    @Test
    void returnLoan_incrementsAvailableCopies_whenOwnedByCaller() {
        Book book = new Book("Clean Code", "Robert C. Martin", "978-1", 2008, "Software", 2);
        book.borrowOneCopy();
        LoanDto returned = new LoanDto(10L, 1L, 5L, Instant.now(), Instant.now(), Instant.now(), LoanStatus.RETURNED);

        when(serviceBClient.returnLoan(10L)).thenReturn(returned);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));

        LoanDto result = loanService.returnLoan(10L, 1L, false);

        assertThat(result.status()).isEqualTo(LoanStatus.RETURNED);
        assertThat(book.getAvailableCopies()).isEqualTo(2);
    }

    @Test
    void returnLoan_deniesAccess_whenLoanBelongsToAnotherUserAndCallerIsNotAdmin() {
        LoanDto loanOfSomeoneElse = new LoanDto(10L, 2L, 5L, Instant.now(), Instant.now(), Instant.now(), LoanStatus.RETURNED);
        when(serviceBClient.returnLoan(10L)).thenReturn(loanOfSomeoneElse);

        assertThatThrownBy(() -> loanService.returnLoan(10L, 1L, false))
                .isInstanceOf(AccessDeniedException.class);
    }
}
