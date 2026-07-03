package com.library.loan;

import com.library.common.DownstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * HTTP client for Service B (the loans service). Every method here can fail because
 * Service B is unreachable, times out, or rejects the request as a business error
 * (e.g. no copies available) — each case is translated into a
 * {@link DownstreamServiceException} carrying the HTTP status this service should
 * answer the original client with, so a Service B outage never looks like a bug in
 * Service A.
 */
@Component
public class ServiceBClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceBClient.class);

    private final RestClient restClient;

    public ServiceBClient(RestClient serviceBRestClient) {
        this.restClient = serviceBRestClient;
    }

    public LoanDto createLoan(Long userId, Long bookId) {
        return execute(() -> restClient.post()
                .uri("/loans")
                .body(new ServiceBLoanRequest(userId, bookId))
                .retrieve()
                .body(LoanDto.class));
    }

    public LoanDto returnLoan(Long loanId) {
        return execute(() -> restClient.post()
                .uri("/loans/{id}/return", loanId)
                .retrieve()
                .body(LoanDto.class));
    }

    public List<LoanDto> getActiveLoansByUser(Long userId) {
        return execute(() -> restClient.get()
                .uri("/loans/active?userId={userId}", userId)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<LoanDto>>() {
                }));
    }

    public List<LoanDto> getHistory(Long userId) {
        return execute(() -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/loans/history");
                    if (userId != null) {
                        uriBuilder.queryParam("userId", userId);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<LoanDto>>() {
                }));
    }

    private <T> T execute(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException ex) {
            // 4xx from Service B is a business rejection (not found, no copies available, etc.)
            log.warn("Service B rejected the request: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new DownstreamServiceException(
                    "Loan service rejected the request: " + ex.getResponseBodyAsString(),
                    (HttpStatus) ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            log.error("Service B returned a server error: {}", ex.getStatusCode(), ex);
            throw new DownstreamServiceException(
                    "Loan service is currently failing", HttpStatus.BAD_GATEWAY, ex);
        } catch (ResourceAccessException ex) {
            // Connection refused, DNS failure, or the configured timeout was exceeded.
            log.error("Service B is unreachable or timed out", ex);
            throw new DownstreamServiceException(
                    "Loan service is unavailable, please try again later", HttpStatus.SERVICE_UNAVAILABLE, ex);
        }
    }
}
