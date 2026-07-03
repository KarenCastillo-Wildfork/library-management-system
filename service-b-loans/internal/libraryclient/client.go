// Package libraryclient talks to Service A (the Java library service) to validate
// that a book exists and has available copies before a loan is persisted.
package libraryclient

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/domain"
)

// Client is an HTTP client for Service A's internal endpoints. It is not
// authenticated with a JWT (it has none): Service A trusts a shared-secret header
// instead, since this is a fixed service-to-service call, not a user action.
type Client struct {
	baseURL    string
	apiKey     string
	httpClient *http.Client
}

func New(baseURL, apiKey string, timeout time.Duration) *Client {
	return &Client{
		baseURL: baseURL,
		apiKey:  apiKey,
		httpClient: &http.Client{
			Timeout: timeout,
		},
	}
}

type availabilityResponse struct {
	BookID          int64 `json:"bookId"`
	Exists          bool  `json:"exists"`
	AvailableCopies int   `json:"availableCopies"`
}

// CheckAvailability asks Service A whether bookID exists and how many copies are
// free. A network error, timeout, or non-200 response is reported as
// domain.ErrLibraryServiceDown: from this service's point of view Service A being
// slow, down, or misbehaving are all the same failure mode, and the caller should
// refuse to create the loan rather than guess.
func (c *Client) CheckAvailability(ctx context.Context, bookID int64) (*domain.BookAvailability, error) {
	url := fmt.Sprintf("%s/internal/books/%d/availability", c.baseURL, bookID)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("building availability request: %w", err)
	}
	req.Header.Set("X-Internal-Api-Key", c.apiKey)
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", domain.ErrLibraryServiceDown, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("%w: unexpected status %d", domain.ErrLibraryServiceDown, resp.StatusCode)
	}

	var parsed availabilityResponse
	if err := json.NewDecoder(resp.Body).Decode(&parsed); err != nil {
		return nil, fmt.Errorf("%w: decoding response: %v", domain.ErrLibraryServiceDown, err)
	}

	return &domain.BookAvailability{
		BookID:          parsed.BookID,
		Exists:          parsed.Exists,
		AvailableCopies: parsed.AvailableCopies,
	}, nil
}
