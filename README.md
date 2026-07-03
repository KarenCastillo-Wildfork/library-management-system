# Library Management System

Prueba técnica - Backend (multi-lenguaje). Dos servicios independientes que se
comunican por HTTP:

- **Service A** (`service-a-library`) — Java 17 / Spring Boot 3. Cliente-facing:
  libros, usuarios, autenticación JWT y orquestación de préstamos.
- **Service B** (`service-b-loans`) — Go 1.25. Gestiona préstamos exclusivamente,
  persiste sus propios datos, y valida contra Service A antes de crear un préstamo.

Elegí **Java/Spring Boot** para el Servicio A porque es donde tengo más
experiencia como Software Analyst (Java y Spring Boot son mi stack principal), y
Go para el Servicio B, tal como exige la prueba.

## Instrucciones de levantamiento

### Requisitos previos

- Docker y Docker Compose (v2, el comando es `docker compose`, no `docker-compose`).
- Nada más es estrictamente necesario: ambos servicios se compilan dentro de sus
  propios contenedores. Java 17 + Maven y Go 1.25 solo hacen falta si quieres
  correr algún servicio fuera de Docker (ver más abajo).

### Levantar todo con un solo comando

1. Clona el repo y ubícate en la raíz (donde está este `README.md`).
2. Copia el archivo de variables de entorno de ejemplo:

   ```bash
   cp .env.example .env
   ```

   Los valores por defecto en `.env.example` ya funcionan para correr todo en
   local; solo necesitas cambiarlos si quieres otras credenciales.
3. Levanta todo el sistema:

   ```bash
   docker compose up --build
   ```

   Esto construye y levanta 4 contenedores:

   | Contenedor | Descripción | Puerto en tu máquina |
   |---|---|---|
   | `postgres-library` | Base de datos de Service A | `5433` (interno `5432`) |
   | `postgres-loans` | Base de datos de Service B | `5434` (interno `5432`) |
   | `service-a` | API Java/Spring Boot | `8080` |
   | `service-b` | API Go | `8081` |

4. Espera a que los logs muestren ambos servicios arriba (docker-compose espera a
   que cada Postgres pase su healthcheck antes de arrancar el servicio que depende
   de él, y Service A espera a que Service B esté saludable antes de arrancar).
5. Un usuario **admin** se crea automáticamente en el primer arranque de Service A
   (usuario `admin`, clave `Admin123!` por defecto — configurable con
   `ADMIN_USERNAME` / `ADMIN_PASSWORD` en tu `.env`).

### Verificar que todo quedó arriba

```bash
curl http://localhost:8080/actuator/health   # Service A -> {"status":"UP"}
curl http://localhost:8081/health            # Service B -> {"status":"UP"}
```

O abre en el navegador:

| Recurso | URL |
|---|---|
| Service A - Swagger UI | http://localhost:8080/swagger-ui.html |
| Service A - OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Service B - Swagger UI | http://localhost:8081/swagger/index.html |

Para probar la API sin escribir `curl` a mano, importa en Postman:
[`docs/postman/Library-System.postman_collection.json`](docs/postman/Library-System.postman_collection.json)
y [`docs/postman/Library-System.postman_environment.json`](docs/postman/Library-System.postman_environment.json).

### Apagar todo

```bash
docker compose down          # detiene y elimina los contenedores
docker compose down -v       # además borra los volúmenes (datos de Postgres)
```

### Levantar cada servicio por separado (sin Docker)

Útil si quieres correr un servicio directo desde tu IDE. Primero necesitas las
bases de datos disponibles (la forma más simple es dejar que Docker solo las
levante a ellas):

```bash
docker compose up postgres-library postgres-loans
```

Luego, en otra terminal, cada servicio:

```bash
# Service A (necesita JDK 17 + Maven)
cd service-a-library
cp .env.example .env
mvn spring-boot:run

# Service B (necesita Go 1.25+)
cd service-b-loans
cp .env.example .env
go run ./cmd/server
```

Cada `.env.example` de cada servicio trae los puertos/credenciales ya apuntando a
`localhost:5433` / `localhost:5434` (los puertos que Docker expone para cada
Postgres), así que no hace falta tocar nada más.

### Correr los tests

```bash
cd service-a-library && mvn test        # 23 tests (unitarios + integración con H2)
cd service-b-loans    && go test ./...  # 11 tests (unitarios + HTTP)
```

## Arquitectura

```
                    ┌────────────────────┐
   Cliente  ───────▶│   Service A (Java)  │
                    │  books / users /    │
                    │  auth JWT / roles   │
                    └─────────┬────────────┘
                              │ HTTP (RestClient)
                              │ POST /loans
                              │ POST /loans/{id}/return
                              │ GET  /loans/active /history
                              ▼
                    ┌────────────────────┐
                    │   Service B (Go)     │
                    │  loans only          │
                    └─────────┬────────────┘
                              │ HTTP (X-Internal-Api-Key)
                              │ GET /internal/books/{id}/availability
                              ▼
                    (vuelve a Service A)

   postgres-library ◀── Service A         Service B ──▶ postgres-loans
   (books, users)                                       (loans)
```

**División de responsabilidades:** Service A es dueño de la identidad (usuarios,
roles, JWT) y del catálogo (libros, `availableCopies`). Service B es dueño
exclusivamente del ciclo de vida de un préstamo (activo/devuelto, fechas). Ninguno
accede a la base de datos del otro directamente — toda interacción es HTTP.

### Por qué bases de datos separadas (no una tabla compartida)

Cada servicio tiene su propio Postgres (`postgres-library`, `postgres-loans`). Elegí
esto en vez de una tabla compartida porque:

- Es la única opción consistente con "dos servicios independientes que se
  comunican por HTTP" — compartir una tabla acopla los servicios a nivel de
  esquema y rompe la independencia de despliegue que pide el enunciado.
- Cada servicio puede evolucionar su esquema (migraciones) sin coordinar con el
  otro.
- El costo es el que la prueba espera que asumas: consistencia eventual entre
  `books.available_copies` (Service A) y las filas de `loans` (Service B), resuelta
  con el flujo síncrono descrito abajo.

### Flujo de un préstamo y consistencia de datos

Este es probablemente el punto más evaluado de la prueba, así que lo detallo:

1. Cliente autenticado → `POST /api/loans` en **Service A** con `{ bookId }`.
2. Service A verifica localmente que el libro existe (fail-fast, evita una llamada
   de red innecesaria) y llama a **Service B**: `POST /loans { userId, bookId }`.
3. **Service B**, antes de persistir nada, llama de vuelta a Service A:
   `GET /internal/books/{id}/availability` (autenticado con un header de secreto
   compartido `X-Internal-Api-Key`, no JWT — Service B no actúa en nombre de un
   usuario, así que un JWT no aplica aquí). Si el libro no existe o no tiene
   copias, Service B rechaza la petición (404/409) **sin** tocar su base de datos.
4. Si Service B confirma (crea la fila `loans` y responde 201), **solo entonces**
   Service A decrementa `available_copies` en su propia base de datos.
5. Devolución: `POST /api/loans/{id}/return` en Service A → Service A llama a
   Service B para marcar el préstamo devuelto → si Service B confirma, Service A
   incrementa `available_copies`.

**Por qué en este orden:** decrementar copias en Service A *antes* de confirmar
con Service B arriesga contar un préstamo que nunca se creó (p. ej. si Service B
está caído). Decrementar solo después de la confirmación evita ese caso.

**Qué pasa si el otro servicio está caído:**
- Si Service B no responde a Service A (timeout configurable, default 5s, o
  connection refused): Service A no crea nada localmente y responde `503` al
  cliente ("Loan service is unavailable, please try again later").
- Si Service A no responde a Service B durante la validación: Service B rechaza
  la petición con `502` ("fail closed" — nunca crea un préstamo que no pudo
  validar).
- Ningún lado reintenta automáticamente: un timeout se reporta como error al
  cliente en vez de reintentar en silencio y arriesgar duplicados. Retries con
  backoff serían el siguiente paso natural, ver "Lo que no alcancé a hacer".

**Gap conocido, no resuelto (por tiempo):** si Service A se cae *entre* recibir la
confirmación 201 de Service B y decrementar `available_copies`, los conteos pueden
quedar inconsistentes (Service B cree que el préstamo existe, Service A nunca lo
reflejó en `available_copies`). Con más tiempo, la solución real sería un patrón
saga/outbox (Service A publica un evento "loan requested", un worker procesa la
creación en B y la actualización de copias como una transacción de outbox, con
reintentos e idempotencia), o directamente calcular `available_copies` en Service A
como `totalCopies - (préstamos activos según Service B)` en vez de mantener un
contador propio — más simple pero acopla la disponibilidad de A a la
disponibilidad de B incluso para lecturas.

## Decisiones técnicas por servicio

### Service A (Java / Spring Boot 3)

- **Spring Boot 3.3 + Java 17**, Maven.
- **Spring Data JPA + Flyway** para el esquema versionado (`src/main/resources/db/migration`).
- **Spring Security + JJWT** para JWT stateless (`SecurityConfig`, `JwtService`,
  `JwtAuthFilter`). Sesiones deshabilitadas (`STATELESS`), CSRF deshabilitado (no
  hay cookies que proteger).
- **`RestClient`** (Spring 6.1/Boot 3.2+) como cliente HTTP hacia Service B, no
  `RestTemplate` (deprecado) ni `WebClient` (esta app es 100% bloqueante/servlet;
  `WebClient` obligaría a `.block()` en todos lados solo para traer Reactor sin
  necesitarlo). Timeouts explícitos vía el `HttpClient` del JDK
  (`JdkClientHttpRequestFactory`), sin dependencias HTTP adicionales.
- **Admin seed vía `CommandLineRunner`** (`AdminUserSeeder`), no vía migración SQL:
  así el hash bcrypt se genera con el `PasswordEncoder` real en tiempo de arranque
  en vez de commitear un hash (y por lo tanto una contraseña fija) al repo.
  Configurable con `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
- **Autorización de `/internal/**`** vía un secreto compartido
  (`InternalApiKeyFilter`, header `X-Internal-Api-Key`) en vez de JWT: es una
  llamada servicio-a-servicio sin usuario final, un JWT de usuario no tiene
  sentido ahí, y OAuth2 client-credentials es más infraestructura de la que esta
  prueba justifica.
- **Tests contra H2** (`application-test.yml`, perfil `test`) en vez de
  Testcontainers: corre `mvn test` sin depender de un Docker daemon disponible en
  el entorno de CI/desarrollador. Testcontainers sería la elección de producción
  (valida contra el motor real de Postgres); lo dejo anotado aquí como el upgrade
  obvio si el entorno lo permite.
- **springdoc-openapi** para Swagger UI + esquema de seguridad Bearer JWT.
- **`maven-javadoc-plugin`** configurado (`mvn javadoc:javadoc`, salida en
  `target/reports/apidocs/index.html`); las clases públicas tienen Javadoc
  explicando el *por qué*, no el qué.
- **`RequestTraceFilter`**: agrega/propaga un `X-Trace-Id` y lo mete en el MDC de
  SLF4J, para correlacionar logs de una misma request entre servicios.

### Service B (Go 1.25)

- **Estructura idiomática**: `cmd/server` (wiring), `internal/domain` (modelo +
  errores, sin dependencias), `internal/repository` (pgx), `internal/service`
  (lógica de negocio, depende solo de interfaces), `internal/httpapi` (HTTP),
  `internal/libraryclient` (cliente hacia Service A), `internal/migrate`
  (migraciones embebidas).
- **Router: [chi](https://github.com/go-chi/chi)**, no Gin/Echo. chi se queda
  sobre los tipos estándar `http.Handler`/`http.HandlerFunc` (sin un `Context`
  propio, sin binding por reflexión) — es una capa delgada e idiomática sobre
  `net/http`, no un framework con sus propias convenciones.
- **pgx directo (sin ORM)**: para las ~5 queries que este servicio necesita, SQL
  explícito es más fácil de auditar que un ORM, y refuerza el manejo de errores
  idiomático de Go (`fmt.Errorf("...: %w", err)` en cada capa, nunca un panic para
  un caso de negocio esperado).
- **Migraciones embebidas** (`go:embed` + `golang-migrate`), aplicadas
  automáticamente al arrancar — no hay contenedor de migración separado ni paso
  manual en `docker-compose`.
- **`context.Context` propagado** en cada llamada a BD y HTTP (ver las firmas en
  `internal/repository` y `internal/libraryclient`), con timeouts configurables
  (`LIBRARY_SERVICE_TIMEOUT_MS`).
- **Errores como valores**: `internal/domain` define errores centinela
  (`ErrBookNotFound`, `ErrBookUnavailable`, `ErrLibraryServiceDown`, ...);
  `internal/httpapi/errors.go` los traduce a códigos HTTP en un único lugar. Nada
  hace panic para un caso de negocio esperado; `middleware.Recoverer` (chi) es solo
  la red de seguridad de último recurso ante un bug real.
- **Swagger sin swaggo/codegen**: quise UI interactiva, pero `swaggo/swag` genera
  código (`docs/docs.go`) que hay que regenerar con `swag init` cada vez que cambian
  los endpoints, y es fácil olvidarlo y terminar con documentación desactualizada.
  Escribí el spec OpenAPI 3 a mano (`internal/httpapi/docs/openapi.yaml`, embebido
  con `go:embed`) y lo sirvo con Swagger UI vía CDN (`internal/httpapi/swagger.go`):
  mismo resultado para quien lo usa, sin paso de build adicional ni riesgo de que
  el spec generado quede desincronizado del código.
- **`slog` (stdlib) con salida JSON** para logging estructurado — sin dependencia
  extra, disponible desde Go 1.21.
- **Rate limiting** (bonus): un token bucket global de proceso
  (`golang.org/x/time/rate`, ver `internal/httpapi/ratelimit.go`) — deliberadamente
  *no* es por IP: el único cliente esperado de este servicio es Service A, así que
  limitar el throughput total es lo relevante, no la equidad entre clientes.
- **`go.mod`/`go.sum` committeados**: dependencias resueltas y verificadas con
  `go build ./...`, `go vet ./...` y `go test ./...` antes de entregar esto.

## Flujo completo de ejemplo (login → consultar libro → préstamo → devolución)

```bash
# 1. Login como admin (seed automático)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}' | tee /tmp/admin.json
ADMIN_TOKEN=$(node -pe "require('/tmp/admin.json').accessToken")

# 2. Crear un libro (ADMIN)
curl -s -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"title":"Clean Code","author":"Robert C. Martin","isbn":"978-0132350884","year":2008,"genre":"Software","totalCopies":2}' \
  | tee /tmp/book.json
BOOK_ID=$(node -pe "require('/tmp/book.json').id")

# 3. Registrar un usuario normal y loguearse
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"carol","email":"carol@example.com","password":"SuperSecret1"}'
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"carol","password":"SuperSecret1"}' | tee /tmp/carol.json
USER_TOKEN=$(node -pe "require('/tmp/carol.json').accessToken")

# 4. Consultar el libro (lectura pública, no requiere auth)
curl -s http://localhost:8080/api/books/$BOOK_ID

# 5. Registrar un préstamo (Service A llama a Service B, que valida contra Service A)
curl -s -X POST http://localhost:8080/api/loans \
  -H "Content-Type: application/json" -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"bookId\": $BOOK_ID}" | tee /tmp/loan.json
LOAN_ID=$(node -pe "require('/tmp/loan.json').id")

# 6. Ver mis préstamos activos
curl -s http://localhost:8080/api/loans/me -H "Authorization: Bearer $USER_TOKEN"

# 7. Devolver el préstamo
curl -s -X POST http://localhost:8080/api/loans/$LOAN_ID/return \
  -H "Authorization: Bearer $USER_TOKEN"
```

El mismo flujo está en la colección de Postman (carpetas `Auth`, `Books`, `Loans`),
con scripts que capturan `jwtToken` / `adminJwtToken` / `bookId` / `loanId`
automáticamente entre requests.

## Lo que no alcancé a hacer (y por qué)

- **gRPC entre servicios**: es un bonus explícitamente opcional en el enunciado.
  Priorizé dejar bien resuelto el flujo HTTP (con manejo de timeouts/caídas, que sí
  es un criterio de evaluación explícito) antes que sumar un segundo protocolo de
  transporte sin que aportara algo que HTTP+JSON no cubriera ya para este alcance.
- **Rate limiting en Service A**: lo agregué en Service B (barato, sin
  dependencias) pero no en Service A por tiempo; Spring tiene opciones razonables
  (Bucket4j, resilience4j) que agregaría con más margen.
- **Saga/outbox para la consistencia libro↔préstamo**: documentado arriba como el
  gap conocido. Resolver esto "bien" (transaccionalmente) es varios días de
  trabajo por sí solo; para el alcance de esta prueba documenté el riesgo en vez
  de improvisar una solución a medias.
- **Retries con backoff en las llamadas entre servicios**: hoy un timeout se
  reporta como error inmediatamente. Es la decisión correcta para no enmascarar
  fallas ni arriesgar duplicados sin idempotencia, pero un retry con backoff
  acotado (y una key de idempotencia en `POST /loans`) sería la siguiente mejora
  natural.
- **Overdue/multas por atraso**: el modelo de `Loan` en Service B calcula
  `dueDate` (préstamo + `LOAN_PERIOD_DAYS`, default 14 días) pero no hay ningún
  job ni endpoint que actúe sobre préstamos vencidos — el enunciado no lo pedía
  explícitamente y lo dejé fuera para no inflar el alcance.
