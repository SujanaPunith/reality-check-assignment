# Reality Check Service --- Implementation Summary

## Overview

For this assignment, I focused on the five tickets mentioned in the
README. I tried to keep the existing project structure simple and make
changes only where they were needed for correctness, API usability,
multi-replica support, acknowledgement history, and player-local time
formatting.

Below is a summary of what I implemented and the reasoning behind the
main decisions.

## Ticket 1 --- Engineering: Correctness and Maintainability

For the session lifecycle, I kept the service/repository approach
already used in the project and added the required behavior around it.

### What I implemented

-   Start, retrieve, update, acknowledge, refresh, and stop a
    reality-check session.
-   Prevent more than one ACTIVE session for the same player.
-   Update elapsed session time while the session is active.
-   Recalculate `nextCheckAt` when the player changes the reminder
    interval.
-   Keep STOPPED sessions in the database so previous session history is
    not lost.
-   Use conditional database updates for operations where the session
    state can change concurrently.

### Concurrency handling

For reminder claiming, I used a conditional database update instead of
adding distributed locks.

The claim succeeds only when:

-   the session is still ACTIVE,
-   the reminder is still due, and
-   the interval has not changed since the session was read.

If the interval is changed by another operation first, the stale
reminder claim updates zero rows and no outdated reminder event is sent.

I chose this approach because it keeps the solution relatively simple
and also works when more than one application instance is running.

------------------------------------------------------------------------

## Ticket 2 --- QA: Usable and Documented API

The README mentions that QA frequently has to ask developers how to call
the existing endpoints. For this ticket, I changed the API structure to
make the operations easier to understand from Swagger/OpenAPI.

### API endpoints

I used resource-oriented endpoints based on the player and the player's
current reality-check session:

-   `GET /api/v1/players/{playerId}/reality-check-session`
-   `POST /api/v1/players/{playerId}/reality-check-session`
-   `PATCH /api/v1/players/{playerId}/reality-check-session`
-   `DELETE /api/v1/players/{playerId}/reality-check-session`
-   `POST /api/v1/players/{playerId}/reality-check-session/acknowledgements`

I preferred these names over action-based paths such as `getStatus` or
`stopSession` because the HTTP method already explains the operation and
the resource name stays consistent.

### Responses and validation

I also added clear HTTP status codes and structured error responses.

Examples:

-   `200` --- successful read, update, or stop
-   `201` --- session or acknowledgement created
-   `400` --- invalid request
-   `404` --- player or ACTIVE session not found
-   `409` --- player already has an ACTIVE session

Error codes include:

-   `PLAYER_NOT_FOUND`
-   `ACTIVE_SESSION_NOT_FOUND`
-   `ACTIVE_SESSION_ALREADY_EXISTS`
-   `INVALID_INTERVAL_MINUTES`

The interval is validated between 1 and 1440 minutes. I used 24 hours as
a defensive upper limit; this upper limit is an implementation choice
and is not explicitly specified as a business rule in the README.

Swagger UI and OpenAPI documentation remain available through the
configured application context path.

------------------------------------------------------------------------

## Ticket 3 --- Tech Ops: Multi-Replica / Scale-Out Safety

For the scale-out ticket, I wanted the application to remain correct
even when requests or scheduler executions happen on different replicas.

### What I implemented

-   Added MySQL support for the scale-out profile.
-   Added a database-level rule to prevent two ACTIVE sessions for the
    same player.
-   Used atomic conditional updates when claiming due reminders.
-   Added a real MySQL integration test.
-   Added a separate one-time Liquibase migration container for the
    scale-out setup.
-   Made application replicas wait until migration completes
    successfully.
-   Disabled Liquibase only on the scale-out application replicas,
    because migration is already handled before they start.
-   Updated the MySQL healthcheck to perform an authenticated TCP
    `SELECT 1` against the configured database.

### One ACTIVE session per player

I used a generated `active_player_id` column.

When a session is ACTIVE, this column contains its `player_id`. When the
session is STOPPED, it becomes `NULL`.

A unique index on this column means the database itself prevents two
ACTIVE sessions for the same player while still allowing multiple
historical STOPPED sessions.

I chose the database constraint because an in-memory check would not be
enough when multiple application replicas are running.

### Startup order

The scale-out startup flow is:

``` text
MySQL becomes healthy
        ↓
Liquibase migration runs successfully
        ↓
Application replicas start
```

I tested this setup with two application replicas.

------------------------------------------------------------------------

## Ticket 4 --- Compliance: Acknowledgement History

The requirement here was to keep every acknowledgement timestamp.

For this, I added a separate `reality_check_acknowledgement` table.

Each acknowledgement stores:

-   the session,
-   the player, and
-   the acknowledgement timestamp.

Every acknowledgement creates a new history row, so an older
acknowledgement is not lost when another one is made.

I also kept the latest `acknowledgedAt` value on the session itself
because it is useful when returning the current session through the API.

During manual testing, I acknowledged the same session twice and
verified directly in H2 that both history rows were stored, while the
session contained the latest acknowledgement timestamp.

------------------------------------------------------------------------

## Ticket 5 --- Frontend: Player-Local Time Formatting

For timestamps returned to the frontend, I format the epoch values using
the player's configured timezone.

The response format is:

``` text
d MMMM yy HH:mm
```

For example:

``` text
1 September 26 19:53
```

This gives the frontend a readable player-local value with an English
month name and 24-hour time.

------------------------------------------------------------------------

## Main Design Decisions

### Why I changed the API paths

The README contains example endpoints such as `getStatus`,
`getOrStartSession`, and `stopSession`.

Since one of the tickets specifically asks to make the API and generated
documentation easier for QA to use, I used REST-style
`/api/v1/players/...` endpoints instead of keeping action names in the
URL.

The underlying Reality Check behavior remains the same, but the
operations are easier to identify in Swagger.

### Why I used database-backed concurrency

For a single application instance, an in-memory check may appear
sufficient. With multiple replicas, however, two instances can read the
same state at the same time.

Because of that, I used database constraints and conditional updates for
the important state transitions. This keeps correctness independent of
which replica receives the request or runs the scheduler.

### What happens when the interval changes

When the interval is changed, I reschedule the next reminder from the
time of the change.

For example, if the interval is changed to 20 minutes at 19:44, the new
`nextCheckAt` is approximately 20:04.

### How acknowledgement data is stored

I use two representations for different purposes:

-   the acknowledgement history table keeps every acknowledgement;
-   the session row keeps the latest acknowledgement for the current
    session response.

------------------------------------------------------------------------

## Running the Service

### Default H2 mode

``` bash
docker compose up --build -d
```

Swagger UI:

``` text
http://localhost:8080/reality-check/swagger-ui.html
```

H2 console:

``` text
http://localhost:8080/reality-check/h2-console
```

### MySQL scale-out mode

Example with two application replicas:

``` bash
docker compose --profile scale-out up --build --scale app-scale=2 app-scale
```

The container status can be checked with:

``` bash
docker compose --profile scale-out ps -a
```

------------------------------------------------------------------------

## Testing

I verified the project using Java 25 with:

``` bash
mvn clean test
mvn clean package
```

Final automated verification:

-   39 tests
-   0 failures
-   0 errors
-   the MySQL integration test is environment-gated during the normal
    Maven run
-   the MySQL integration test was also executed separately against
    MySQL 8.4 and passed

I also manually tested the API through Swagger, checked
acknowledgement/session data directly in H2, observed scheduled reminder
events in the application logs, and verified the MySQL/Liquibase
two-replica setup.

The detailed test results are documented in `VERIFICATION.md`.
