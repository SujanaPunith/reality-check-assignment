# Reality Check Service --- Verification Report

## Summary

The final solution was verified through automated tests, build/package
checks, real MySQL integration, fresh Liquibase migration, multi-replica
startup, Swagger/API testing, scheduler observation, and direct database
inspection.

### Final verification status

  Area                            Result
  ------------------------------- ------------
  Maven test suite                PASS
  Maven package build             PASS
  Real MySQL integration test     PASS
  Fresh Liquibase migration       PASS
  Two-replica scale-out startup   PASS
  Swagger/OpenAPI availability    PASS
  Manual functional scenarios     25/25 PASS
  Five assignment tickets         Covered

------------------------------------------------------------------------

## Automated Tests

The final automated Maven run used Java 25.

``` bash
mvn clean test
```

Result:

-   39 tests executed by the normal suite
-   0 failures
-   0 errors
-   1 environment-gated MySQL integration test skipped in the normal
    local run

The package build was also verified:

``` bash
mvn clean package
```

Result: **BUILD SUCCESS**

The application JAR was produced successfully.

------------------------------------------------------------------------

## Real MySQL Integration Verification

The environment-gated MySQL repository integration test was executed
separately against MySQL 8.4.

Result:

``` text
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Liquibase successfully applied all seven configured changesets to the
test database.

------------------------------------------------------------------------

## Fresh Scale-Out / Migration Verification

A fresh named Compose project and fresh MySQL volume were used to verify
scale-out startup.

Observed final state:

``` text
mysql       → healthy
migrate     → Exited (0)
app-scale-1 → Up
app-scale-2 → Up
```

The migration logs confirmed that all seven Liquibase changesets ran
successfully before the application replicas started.

The MySQL healthcheck was also verified using an authenticated TCP
`SELECT 1`, ensuring that the database, application user, and TCP
listener were ready before migration began.

------------------------------------------------------------------------

## Manual Test Results

The following scenarios were manually verified through Swagger, IntelliJ
application logs, and direct H2 database queries.

  ----------------------------------------------------------------------------------------------
                     \# Scenario          Verification                          Result
  --------------------- ----------------- ------------------------------------- ----------------
                      1 Default H2        Application started successfully on   PASS
                        application       port 8080                             
                        startup                                                 

                      2 Swagger UI        Swagger UI loaded and API operations  PASS
                                          were visible                          

                      3 Retrieve seeded   Player 1001 returned ACTIVE session   PASS
                        ACTIVE session    with negative net amount              

                      4 Positive net      Player 1002 returned                  PASS
                        amount            `netAmountMinor = 15000`              

                      5 Player with no    Player 1003 returned                  PASS
                        ACTIVE session    `404 ACTIVE_SESSION_NOT_FOUND`        

                      6 Create session    Player 1003 created with interval 10  PASS
                                          and HTTP 201                          

                      7 Duplicate ACTIVE  Second create returned                PASS
                        session           `409 ACTIVE_SESSION_ALREADY_EXISTS`   

                      8 Retrieve newly    Same session returned with increasing PASS
                        created session   elapsed time                          

                      9 Change interval   Interval changed from 10 to 20 and    PASS
                                          next check was rescheduled            

                     10 Persist interval  Subsequent GET returned the updated   PASS
                        update            interval and deadline                 

                     11 First             Acknowledgement created with HTTP 201 PASS
                        acknowledgement                                         

                     12 Second            Second acknowledgement accepted and   PASS
                        acknowledgement   latest timestamp updated              

                     13 Acknowledgement   Direct DB query showed two separate   PASS
                        history           history rows                          

                     14 Latest            Session `acknowledged_at` matched     PASS
                        acknowledgement   newest history timestamp              
                        on session                                              

                     15 Stop ACTIVE       Session transitioned to STOPPED and   PASS
                        session           final state was preserved             

                     16 GET after stop    Returned                              PASS
                                          `404 ACTIVE_SESSION_NOT_FOUND`        

                     17 PATCH after stop  Returned                              PASS
                                          `404 ACTIVE_SESSION_NOT_FOUND`        

                     18 Acknowledge after Returned                              PASS
                        stop              `404 ACTIVE_SESSION_NOT_FOUND`        

                     19 Stop twice        Repeated stop returned                PASS
                                          `404 ACTIVE_SESSION_NOT_FOUND`        

                     20 Start new session New ACTIVE session created while old  PASS
                        after stop        STOPPED history remained              

                     21 Scheduled         Scheduler emitted reminder events at  PASS
                        reminder          the configured 1-minute interval      

                     22 Persist reminder  GET showed updated `lastPromptAt` and PASS
                        state             next one-minute deadline              

                     23 Invalid interval  Interval 0 returned                   PASS
                                          `400 INVALID_INTERVAL_MINUTES`        

                     24 Unknown player    Player 9999 returned                  PASS
                                          `404 PLAYER_NOT_FOUND`                

                     25 Session-history   DB showed old STOPPED session plus    PASS
                        integrity         exactly one new ACTIVE session        
  ----------------------------------------------------------------------------------------------

------------------------------------------------------------------------

## Manual Evidence Highlights

### Net win/loss

Both directions were verified:

``` text
Player 1001 → netAmountMinor = -4200
Player 1002 → netAmountMinor = 15000
```

### Acknowledgement history

Two acknowledgements were made for the same session. Direct database
inspection showed two separate rows in `reality_check_acknowledgement`.

The session's `acknowledged_at` value matched the latest
acknowledgement, confirming that complete history and convenient
current-state representation coexist.

### Session lifecycle

For player 1003:

``` text
Session 3 → STOPPED
Session 4 → ACTIVE
```

Direct database inspection confirmed that the STOPPED session remained
available as history while only one ACTIVE session existed.

### Scheduler

A one-minute session interval was used to observe reminder execution
directly.

The logs showed consecutive reminder events where the previous event's
`nextCheckAt` became the following event's `promptedAt`, confirming
correct schedule advancement.

------------------------------------------------------------------------

## Verification Against the Five Tickets

### Ticket 1 --- Engineering

Verified through lifecycle tests, repository/service automated tests,
concurrency-focused tests, clean Maven builds, scheduler behavior, and
database state inspection.

**Result: PASS**

### Ticket 2 --- QA

Verified through Swagger UI and manual use of the resource-oriented API.
Success and error paths returned meaningful HTTP status codes and typed
error bodies.

**Result: PASS**

### Ticket 3 --- Tech Ops

Verified with MySQL 8.4, a fresh Liquibase migration, a dedicated
migration container, authenticated TCP readiness, real MySQL integration
testing, and two running application replicas.

**Result: PASS**

### Ticket 4 --- Compliance

Verified by making multiple acknowledgements and querying the database
directly. Every acknowledgement remained stored as a separate history
row.

**Result: PASS**

### Ticket 5 --- Frontend

Verified through API responses containing player-local formatted
timestamps in the expected readable format:

``` text
d MMMM yy HH:mm
```

**Result: PASS**

------------------------------------------------------------------------

## Notes

-   The interval validation range is 1--1440 minutes. The 24-hour upper
    bound is a defensive implementation choice.
-   The API intentionally uses resource-oriented `/api/v1/players/...`
    endpoints to improve QA usability and generated API documentation.
-   Scale-out correctness is database-backed and does not depend on
    replica-local memory.
-   The default H2 mode remained operational after the MySQL/scale-out
    additions.
