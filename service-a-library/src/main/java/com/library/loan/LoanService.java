package com.library.loan;

import com.library.book.Book;
import com.library.book.BookRepository;
import com.library.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates the cross-service loan flow.
 *
 * <p><b>Consistency model:</b> Service A calls Service B to create the loan record;
 * only once Service B confirms it does Service A decrement {@code availableCopies}
 * on its own {@link Book}. Service B independently re-validates availability against
 * Service A before persisting (see Service B's {@code libraryclient} package), so a
 * stale/incorrect count on Service A's side cannot by itself cause an overbooking.
 *
 * <p><b>Known gap</b> (documented, not fixed, in the interest of time): if Service A
 * crashes after Service B responds success but before the copy count is decremented,
 * the two services' views of "copies available" can drift. A production system would
 * close this with a saga/outbox pattern or by having Service A compute availability
 * from Service B's active-loan count instead of maintaining its own counter.</p>
 */
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final ServiceBClient serviceBClient;
    private final BookRepository bookRepository;

    public LoanService(ServiceBClient serviceBClient, BookRepository bookRepository) {
        this.serviceBClient = serviceBClient;
        this.bookRepository = bookRepository;
    }

    @Transactional
    public LoanDto createLoan(Long userId, Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw new NotFoundException("Book not found with id " + bookId);
        }

        LoanDto loan = serviceBClient.createLoan(userId, bookId);

        // Only decrement our own copy once Service B has confirmed the loan exists.
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found with id " + bookId));
        book.borrowOneCopy();
        bookRepository.save(book);

        log.info("Loan {} created for user {} / book {}", loan.id(), userId, bookId);
        return loan;
    }

    @Transactional
    public LoanDto returnLoan(Long loanId, Long callerUserId, boolean callerIsAdmin) {
        LoanDto loan = serviceBClient.returnLoan(loanId);

        if (!callerIsAdmin && !loan.userId().equals(callerUserId)) {
            // The loan was returned in Service B (it doesn't know about our roles), but we
            // still refuse to expose/act on someone else's loan through this endpoint.
            throw new org.springframework.security.access.AccessDeniedException(
                    "Loan " + loanId + " does not belong to the current user");
        }

        bookRepository.findById(loan.bookId()).ifPresent(book -> {
            book.returnOneCopy();
            bookRepository.save(book);
        });

        log.info("Loan {} returned by user {}", loanId, callerUserId);
        return loan;
    }

    public List<LoanDto> myActiveLoans(Long userId) {
        return serviceBClient.getActiveLoansByUser(userId);
    }

    public List<LoanDto> history(Long callerUserId, boolean callerIsAdmin, Long queryUserId) {
        if (!callerIsAdmin) {
            return serviceBClient.getHistory(callerUserId);
        }
        return serviceBClient.getHistory(queryUserId);
    }
}
