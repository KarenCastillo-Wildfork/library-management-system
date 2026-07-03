package com.library.loan;

import com.library.common.NotFoundException;
import com.library.user.Role;
import com.library.user.User;
import com.library.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Client-facing loan endpoints. Every method here resolves the caller's identity
 * from the JWT and delegates the actual loan bookkeeping to Service B through
 * {@link LoanService}; this controller never talks to Service B directly.
 */
@RestController
@RequestMapping("/api/loans")
@Tag(name = "Loans", description = "Loan operations (proxied to Service B)")
public class LoanController {

    private final LoanService loanService;
    private final UserRepository userRepository;

    public LoanController(LoanService loanService, UserRepository userRepository) {
        this.loanService = loanService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @Operation(summary = "Register a loan for the authenticated user")
    public ResponseEntity<LoanDto> create(@Valid @RequestBody LoanCreateRequest request, Authentication authentication) {
        Long userId = currentUserId(authentication);
        LoanDto loan = loanService.createLoan(userId, request.bookId());
        return ResponseEntity.status(HttpStatus.CREATED).body(loan);
    }

    @PostMapping("/{id}/return")
    @Operation(summary = "Return a loan (must belong to the caller, unless ADMIN)")
    public LoanDto returnLoan(@PathVariable Long id, Authentication authentication) {
        return loanService.returnLoan(id, currentUserId(authentication), isAdmin(authentication));
    }

    @GetMapping("/me")
    @Operation(summary = "List the authenticated user's active loans")
    public List<LoanDto> myLoans(Authentication authentication) {
        return loanService.myActiveLoans(currentUserId(authentication));
    }

    @GetMapping("/history")
    @Operation(summary = "Loan history: own history for a normal user; ADMIN may pass userId, or omit it for everyone's history")
    public List<LoanDto> history(@RequestParam(required = false) Long userId, Authentication authentication) {
        return loanService.history(currentUserId(authentication), isAdmin(authentication), userId);
    }

    private Long currentUserId(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found: " + authentication.getName()));
        return user.getId();
    }

    private boolean isAdmin(Authentication authentication) {
        String expected = "ROLE_" + Role.ADMIN.name();
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(expected::equals);
    }
}
