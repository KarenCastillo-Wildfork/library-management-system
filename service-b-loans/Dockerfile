# --- Build stage ---
FROM golang:1.25-alpine AS builder
WORKDIR /app

# go.mod/go.sum are committed and fully resolved (verified locally with `go build`,
# `go vet` and `go test ./...` before this was written), so the build here is
# reproducible: `go mod download` verifies against go.sum, it never re-resolves.
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /bin/service-b-loans ./cmd/server

# --- Runtime stage ---
FROM alpine:3.20
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY --from=builder /bin/service-b-loans .

EXPOSE 8081
ENTRYPOINT ["./service-b-loans"]
