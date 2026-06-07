# Design File — Event RSVP Manager

**Status:** First-pass design draft — pre-implementation
**Prepared via:** 18-step design process (Steps 01–17 complete; Step 18 pre-review weakness check pending)

---

### Document Purpose

This document captures the full first-pass design for the Event RSVP Manager feature. It is the authoritative reference for what has been decided, what remains unresolved, what risks have been identified, and what assumptions are still pending validation.

The document does not resolve every question. Unresolved items are marked explicitly and must be addressed before the relevant implementation phase begins.

---

### Key Unresolved Items

The following items materially affect implementation and must be resolved before the phases that depend on them:

| ID | Item | Blocks |
|---|---|---|
| OQ-01 | Host identity mechanism — login or link-based? | AI1 enforcement; host surface security; Alternative 1 viability |
| OQ-02 | Is email delivery in scope? | C4 external dependency; DR-01 severity |
| OQ-03 | Deployment target | Infrastructure assumptions; OR-04 severity |
| OQ-05 | What happens to Maybe RSVPs at lock time? | Lock behaviour; dashboard display |
| OQ-09 | What happens to waitlisted invitees on cancel? | UF5 implementation |
| A1 | Does Maybe count toward capacity? | IS1, IS2, UF7, CONFIRMED → MAYBE transition, entire RSVP state machine |
| CC-01 | Locking mechanism for concurrent Yes writes | Phase 4 production readiness; BI1 |
| F5 | Does "live" dashboard require push or poll? | Phase 6 architecture |

---

### Table of Contents

1. [Problem Statement](#problem-statement)
2. [Goals and Non-Goals](#goals-and-non-goals)
3. [Context and Constraints](#context-and-constraints)
4. [Facts, Assumptions, and Open Questions](#facts-assumptions-and-open-questions)
5. [Actors and Workflows](#actors-and-workflows)
6. [Invariants](#invariants)
7. [Architecture](#architecture)
8. [Data Ownership and State Model](#data-ownership-and-state-model)
9. [Trust Boundaries and Security Notes](#trust-boundaries-and-security-notes)
10. [Concurrency and Correctness Notes](#concurrency-and-correctness-notes)
11. [Scalability and Multi-Tenancy Notes](#scalability-and-multi-tenancy-notes)
12. [Risks and Failure Notes](#risks-and-failure-notes)
13. [Alternatives and Tradeoffs](#alternatives-and-tradeoffs)
14. [Rollout and Migration Notes](#rollout-and-migration-notes)

---

## Problem Statement

A host organising an event has no reliable way to collect attendance responses, track who is coming, or manage capacity across multiple invitees. Invitees have no reliable way to respond and update their answer before the event begins.

---

## Goals and Non-Goals

**Goals:**

- G1: A host can create an event and invite specific people by email
- G2: An invitee can submit and update a Yes / No / Maybe response before the event starts
- G3: The host can see how many invitees have confirmed, declined, responded Maybe, or not yet responded — whether this view updates in real time or on page refresh is unresolved pending the interpretation of F5
- G4: Confirmed attendance never exceeds capacity; when a confirmed invitee drops out, the first waitlisted person is promoted automatically
- G5: All RSVPs are locked once the event start time passes
- G6: The host can cancel or close the event at any time

**Non-Goals:**

- NG1: Public event discovery — events are invite-only
- NG2: Ongoing communication and post-event features — no reminders, follow-ups, check-in, or attendance reports; the system covers invitation and RSVP only
- NG3: Calendar integration — adding to Google Calendar, iCal, etc.
- NG4: Payment or ticketing — attendance is free
- NG5: Recurring events — each event is a single independent occurrence
- NG6: Event co-hosts or shared host management — one host per event
- NG7: Event editing after invitations are sent — details are fixed once invites go out

---

## Context and Constraints

**Technical**
- TC1: Tech stack is fixed — Java 17, Spring Boot 4, Spring Data JPA, PostgreSQL, React 19, TypeScript, Vite
- TC2: PostgreSQL is the only datastore — no secondary stores assumed
- TC3: Invitee identity is token-based in the current design — each invitee is identified by a unique link token scoped to their event; this is a committed architectural choice, not a pending decision (see TN-01). Host identity remains unresolved — the mechanism for identifying and verifying the host is not yet defined (OQ-01)
- TC4: RSVP locking is time-based — every RSVP write must check current time against event start time

**Product**
- PC1: Capacity is optional per event — the system must work correctly with and without a cap
- PC2: Events are invite-only — no public registration surface to design for

**Operational**
- OC1: The app runs locally — backend on 8280, frontend on 5171, PostgreSQL on 5432
- OC2: Deployment target is unknown (OQ-03) — no infrastructure assumptions can be made yet

**Organizational**
- RG1: Tech stack was pre-selected and scaffolded — no architectural decision needed there
- RG2: This is an educational project — design must be explainable and defensible, not just functional

---

## Facts, Assumptions, and Open Questions

**Confirmed Facts**

- F1: An event has: title, description, date/time, location, and optional max-capacity
- F2: The creator of an event becomes its host
- F3: The host invites people by email; each invitee receives a unique link
- F4: Invitees respond Yes / No / Maybe
- F5: The host sees a live dashboard with attendance counts and a list of attendees
- F6: When max-capacity is reached, new "Yes" RSVPs go to a waitlist
- F7: A waitlisted attendee is automatically promoted when a confirmed attendee changes their RSVP to No
- F8: The host can cancel the event or close it to further responses at any time
- F9: An invitee can change their RSVP at any point before the event starts
- F10: After the event start time, all RSVPs are locked
- F11: Tech stack is fixed — Java 17, Spring Boot 4, Spring Data JPA, PostgreSQL, React 19, TypeScript, Vite

**Working Assumptions**

- A1 ⚠️ PRIORITY — Capacity counts "Yes" responses only; a "Maybe" does not hold a spot. If false, the capacity check, waitlist trigger, promotion rule, and lock behaviour all change. Must be validated before the data model is finalised.
- A2: Waitlist order is first-in, first-out — "first waitlisted" implies arrival order, but the ordering rule is not stated
- A3: Cancellation and closing are distinct states — cancellation means the event will not happen; closing means it will happen but no further RSVPs are accepted
- A4: A closed or cancelled event still shows existing RSVPs to the host
- A5: The unique link is specific to one invitee and one event — it cannot be reused across events

**Open Questions**

- OQ-01: Is there a login/authentication system, or is identity handled via unique links only? *(shapes the entire identity and trust model)*
- OQ-02: Is actual email delivery in scope, or only generating the unique link? *(determines whether there is an external service dependency)*
- OQ-03: What is the deployment target? *(determines infrastructure assumptions)*
- OQ-05: What happens to "Maybe" responses when RSVPs lock — do they stay as Maybe, or are they treated as No? *(shapes lock behaviour and dashboard display)*
- OQ-06: Can the host RSVP to their own event as an invitee? *(affects whether host and invitee are ever the same identity)*
- OQ-09: What happens to waitlisted invitees when the event is cancelled or closed — are they automatically declined, or left in an ambiguous state?

---

## Actors and Workflows

**Actors**
- **Host** — creates events, sends invitations, monitors dashboard, controls event lifecycle
- **Invitee** — receives a unique link, submits and updates their RSVP
- **System** — enforces capacity, promotes from waitlist, locks RSVPs at start time

*Note: If OQ-01 resolves to "login required," an unauthenticated visitor becomes a fourth actor with its own flow.*

---

### User-Facing Flows

**UF1 — Create Event**
- Trigger: Host submits a new event form
- Steps: Host provides title, description, date/time, location, optional capacity → system validates input → event is created → host is assigned as owner
- State changes: Event: non-existent → OPEN
- Dependencies: None

**UF2 — Invite People**
- Trigger: Host submits one or more email addresses against an existing event
- Steps: Host provides email addresses → system checks for duplicates → system generates a unique link per new invitee → invitations are dispatched → each invitee's RSVP status is initialised to No Response
- State changes: Per invitee: non-existent → INVITED (No Response)
- Dependencies: Event must be OPEN; OQ-02 (email delivery mechanism unknown)

**UF3 — View Attendance Dashboard**
- Trigger: Host navigates to the event dashboard
- Steps: System retrieves all invitees and their current RSVP states → aggregates counts by state → displays list and totals
- State changes: None — read-only
- Dependencies: Event must exist; invitees must have been added; F5 interpretation unresolved — if "live" requires push, the trigger and delivery model for this flow change

**UF4 — Close Event**
- Trigger: Host chooses to close the event to further responses
- Steps: Host confirms close action → event state set to CLOSED → all subsequent RSVP writes are rejected
- State changes: Event: OPEN → CLOSED
- Dependencies: Event must be OPEN

**UF5 — Cancel Event**
- Trigger: Host chooses to cancel the event
- Steps: Host confirms cancellation → event state set to CANCELLED → RSVPs are locked
- State changes: Event: OPEN or CLOSED → CANCELLED
- Dependencies: None beyond the event existing; OQ-09 (what happens to waitlisted invitees is unresolved)

**UF6 — Submit RSVP**
- Trigger: Invitee opens their unique link and submits a response for the first time
- Steps: System validates link and event state → invitee selects Yes / No / Maybe → if Yes, IS1 runs capacity check → response recorded or waitlisted
- State changes: Invitee RSVP: No Response → YES / NO / MAYBE; additional outcome: YES → WAITLISTED if capacity is full
- Dependencies: Valid unique link; event must be OPEN; current time must be before start time; IS1 triggered if response is Yes

**UF7 — Update RSVP**
- Trigger: Invitee opens their unique link and changes an existing response
- Steps: System validates link, event state, and current time → invitee selects new response → the following transitions are defined:
  - Any → No: response recorded; if previous state was CONFIRMED, IS2 triggers waitlist promotion
  - Any → Yes: IS1 runs capacity check; invitee becomes CONFIRMED or WAITLISTED
  - WAITLISTED → No: invitee is removed from waitlist; no promotion triggered
  - CONFIRMED → Maybe: **blocked by A1** — behavior depends on whether Maybe holds a confirmed spot; undefined until A1 is resolved; until A1 is resolved, this transition must be rejected at the write boundary
  - WAITLISTED → Maybe: **undefined** — whether a waitlisted invitee can change to Maybe and what that means for their waitlist position is not defined; until resolved, this transition must be rejected at the write boundary
  - Other → Maybe: response recorded; capacity impact remains governed by A1
- State changes: Invitee RSVP moves from current state → new state per transition above; CONFIRMED → MAYBE and WAITLISTED → MAYBE are undefined and must be rejected until A1 is resolved
- Dependencies: Valid unique link; event must be OPEN; current time before start; IS1 and IS2 may be triggered; A1 must be resolved before CONFIRMED → MAYBE can be fully specified

---

### Internal System Flows

**IS1 — Capacity Enforcement**
- Trigger: An invitee submits or updates their RSVP to Yes
- Steps: Check whether event has a max-capacity → if yes, count current confirmed Yes RSVPs → if count ≥ max-capacity, route invitee to WAITLISTED instead of CONFIRMED
- State changes: Invitee RSVP: → CONFIRMED or WAITLISTED depending on capacity
- Dependencies: A1 ⚠️ (capacity counts Yes only — priority assumption); PC1 (capacity is optional)

**IS2 — Waitlist Promotion**
- Trigger: A confirmed invitee changes their RSVP to No
- Steps: Detect CONFIRMED → DECLINED transition → check for waitlisted invitees → promote the first waitlisted invitee to CONFIRMED
- State changes: First waitlisted invitee: WAITLISTED → CONFIRMED; triggering invitee: CONFIRMED → DECLINED
- Dependencies: IS1 must have placed invitees on the waitlist; A2 (FIFO order assumed)

**IS3 — RSVP Lock Check**
- Trigger: Any RSVP write attempt (submit or update)
- Steps: Compare current time against event start time → if current time ≥ start time, reject the write and return a locked error
- State changes: None — write is rejected; RSVP state unchanged
- Dependencies: TC4 (time-based locking); event start time must be set

---

### Background Flows

**BF1 — RSVP Lock Enforcement**
- Trigger: Event start time is reached
- Steps: Either (a) lazy — no background job; lock is checked per write via IS3; or (b) eager — a scheduled job detects events past their start time and sets a locked flag. Mechanism not yet decided.
- State changes: (a) no state change until a write is attempted; (b) event or RSVP records transition to LOCKED proactively
- Dependencies: Reliable time source; design decision between lazy and eager enforcement is open

---

### Failure Flows

**FF1 — RSVP Rejected: Event Locked**
- Trigger: Invitee attempts to submit or update RSVP after event start time
- Steps: IS3 detects time has passed → write is rejected → invitee receives a locked-state response
- State changes: None
- Dependencies: IS3

**FF2 — RSVP Rejected: Event Not Open**
- Trigger: Invitee attempts to submit RSVP on a closed or cancelled event
- Steps: System checks event state → event is not OPEN → write is rejected
- State changes: None
- Dependencies: Event state must be tracked (OPEN / CLOSED / CANCELLED)

**FF3 — Race Condition on Last Confirmed Spot**
- Trigger: Two invitees simultaneously submit Yes when one spot remains
- Steps: Both writes pass the capacity check at the same time → without concurrency control, both may be confirmed → capacity is exceeded
- State changes: Indeterminate without a concurrency control mechanism
- Dependencies: IS1; concurrency control strategy (revisited in Step 12)

**FF4 — Invalid or Unknown Link**
- Trigger: Invitee opens a link that is invalid or does not exist
- Steps: System looks up the link → no matching record found → access denied; RSVP form is not shown
- State changes: None
- Dependencies: TC3 (link-based identity)

**FF5 — No Waitlisted Invitee to Promote**
- Trigger: Confirmed invitee changes to No, but the waitlist is empty
- Steps: IS2 checks for waitlisted invitees → none found → no promotion occurs → spot remains open
- State changes: Triggering invitee: CONFIRMED → DECLINED; no other changes
- Dependencies: IS2

**FF6 — Duplicate Invitation**
- Trigger: Host submits an email address that has already been invited to the same event
- Steps: System checks whether the email already has an invitation record for this event → duplicate detected → write is rejected; existing invitation and unique link are preserved unchanged
- State changes: None — existing invitee record is untouched
- Dependencies: UF2; the system must enforce uniqueness of email per event at the point of invitation

---

## Invariants

*Note on tenant isolation: this system has no separate organisations, but events are isolated units — one host cannot see or affect another's event. That isolation is the relevant boundary here.*

### Business Invariants

**BI1 — Confirmed count never exceeds max-capacity**
- Statement: The number of CONFIRMED RSVPs for an event must never exceed the event's max-capacity when one is set
- Break scenario: Two invitees simultaneously claim the last spot; or IS1 miscounts confirmed RSVPs before committing
- Trigger: Any RSVP write that results in a CONFIRMED state
- Protection note: IS1 must count and commit atomically; A1 must be resolved first — if Maybe counts toward capacity, the threshold calculation changes

**BI2 — RSVPs are immutable after event start**
- Statement: No RSVP can be created or modified once the event start time has passed
- Break scenario: IS3 is bypassed, skipped, or working with a stale clock
- Trigger: Any RSVP write attempt
- Protection note: IS3 must run on every write path without exception; TC4 requires a reliable time source

**BI3 — Waitlist promotion is automatic and ordered**
- Statement: When a confirmed spot opens, the first waitlisted invitee is promoted immediately — not manually, not eventually
- Break scenario: IS2 is not triggered atomically with the CONFIRMED → DECLINED transition; or FIFO order (A2) is violated
- Trigger: Any CONFIRMED → DECLINED RSVP transition
- Protection note: IS2 must execute within the same transaction as the triggering state change

**BI4 — An invitee holds exactly one RSVP state per event**
- Statement: An invitee cannot be simultaneously CONFIRMED and WAITLISTED, or hold two competing responses for the same event
- Break scenario: A race condition creates two RSVP records for the same invitee on the same event
- Trigger: Concurrent RSVP submissions from the same link
- Protection note: A uniqueness constraint on (invitee, event) must be enforced at the database level

**BI5 — Only invited people can RSVP**
- Statement: Only an invitee with a valid unique link for that specific event can submit an RSVP
- Break scenario: A non-invited person guesses a valid link, or link validation is skipped
- Trigger: Any RSVP submission
- Protection note: Link validation must precede every RSVP write; links must be cryptographically unguessable (TC3)

### Data Integrity Invariants

**DI1 — Every RSVP belongs to exactly one event and one invitee**
- Statement: An RSVP record must always reference a valid, existing event and a valid, existing invitee — orphaned RSVPs are not permitted
- Break scenario: An event or invitee record is deleted without cascading
- Trigger: Any deletion of an event or invitee record
- Protection note: Referential integrity enforced via foreign key constraints in PostgreSQL

**DI2 — Every event has exactly one host**
- Statement: An event must always have a non-null, single host; the host assignment cannot be empty or duplicated
- Break scenario: The host record is deleted; or a bug assigns multiple hosts at creation
- Trigger: Event creation; host record modification
- Protection note: NOT NULL and single-assignment constraint on the host relationship at the database level

**DI3 — A unique link maps to exactly one invitee on exactly one event**
- Statement: No two invitees share the same link token; a token cannot be reused across events
- Break scenario: Link generation produces a collision; A5 is violated
- Trigger: Invitation creation (UF2)
- Protection note: Unique constraint on the link token column; token generation must use sufficient entropy

### Authorization Invariants

**AI1 — Only the host can manage the event lifecycle**
- Statement: Only the host of an event can invite people, close it, or cancel it
- Break scenario: An invitee or anonymous actor accesses a host-only action via a guessed or manipulated URL
- Trigger: Any lifecycle write — UF2, UF4, UF5
- Protection note: ⚠️ This invariant cannot be fully enforced until OQ-01 is resolved. No host identity mechanism is currently defined. Until the identity model is established — whether login-based, token-based, or otherwise — there is no concrete way to verify that the actor performing a lifecycle action is the event's host. This is the weakest enforcement point in the current design.

**AI2 — An invitee can only act on their own RSVP**
- Statement: An invitee cannot submit or change an RSVP on behalf of another invitee
- Break scenario: An invitee forwards their unique link; the system has no way to detect this under the current identity model
- Trigger: Any RSVP write
- Protection note: The unique link is the credential; link-sharing is undetectable by design until OQ-01 is resolved

### Concurrency Invariants

**CI1 — Capacity must not be exceeded under concurrent writes**
- Statement: If two invitees simultaneously submit Yes when one spot remains, exactly one becomes CONFIRMED and the other WAITLISTED — never both CONFIRMED
- Break scenario: Both writes pass the IS1 capacity check before either commits (FF3)
- Trigger: Concurrent IS1 executions on the same event
- Protection note: The capacity check and the RSVP write must be atomic; requires a database-level lock or optimistic concurrency control on the confirmed count — mechanism is open and has not been decided; see CC-01 in Concurrency and Correctness Notes

**CI2 — Waitlist promotion must fire exactly once per vacancy**
- Statement: When one spot opens, exactly one waitlisted invitee is promoted — not zero, not two
- Break scenario: Two CONFIRMED → DECLINED transitions fire simultaneously; IS2 runs twice and promotes two invitees against one vacancy
- Trigger: Concurrent IS2 executions on the same event
- Protection note: IS2 must be serialised per event; the promotion must be committed atomically with the triggering decline — mechanism is open and has not been decided; see CC-03 in Concurrency and Correctness Notes

### Tenant Isolation Invariants

**TI1 — A unique link grants access only to its own event**
- Statement: A valid link for Event A must not grant any access to Event B
- Break scenario: Link lookup does not scope by event; a token collision across events resolves against the wrong event
- Trigger: Any RSVP submission (UF6, UF7)
- Protection note: Link lookup must always scope by both token and event ID; a cross-event collision must be treated as invalid

**TI2 — A host can only access their own events**
- Statement: The host of Event A has no read or write access to Event B unless they are also its host
- Break scenario: Event lookup returns data or accepts writes without verifying ownership
- Trigger: Any dashboard view or lifecycle action
- Protection note: Every event read and write must verify the requesting actor is the event's host; depends on OQ-01 for how identity is established

---

## Architecture

### Components

**C1 — Host UI**
- Responsibility: Renders event creation, invitation management, and attendance dashboard; sends requests to the backend on behalf of the host
- Inputs: Host interactions; HTTP responses from backend
- Outputs: HTTP requests to Event API and Invitation API
- Ownership: Frontend — React SPA (port 5171)
- Notes: Host identity mechanism undefined until OQ-01 is resolved; AI1 has no enforcement here yet

**C2 — Invitee UI**
- Responsibility: Renders the RSVP form; reads the unique token from the URL and attaches it to RSVP requests
- Inputs: Unique token from URL; HTTP responses from RSVP Management
- Outputs: HTTP requests to RSVP Management carrying the token and response
- Ownership: Frontend — React SPA (port 5171)
- Notes: Token in URL is the only invitee identity signal under the current model (OQ-01 open)

**C3 — Event API**
- Responsibility: Handles event creation and lifecycle actions — close and cancel; enforces that only the host may perform these actions
- Inputs: HTTP requests from Host UI
- Outputs: Event state changes written to database; HTTP responses to Host UI
- Ownership: Backend — Spring MVC controller + service (port 8280)
- Notes: Host identity verification required but undefined until OQ-01 is resolved; most exposed point for AI1

**C4 — Invitation API**
- Responsibility: Generates unguessable unique tokens for new invitees; enforces no duplicate invitations per event; validates tokens on incoming RSVP requests
- Inputs: HTTP requests from Host UI (invite by email); token lookup requests from RSVP Management
- Outputs: Invitation records and tokens written to database; validation results returned to RSVP Management
- Ownership: Backend — Spring MVC controller + service (port 8280)
- Notes: Token generation must use sufficient entropy (BI5, DI3); invitation record must persist independently of any email dispatch (OQ-02 unresolved)

**C5 — RSVP Management**
- Responsibility: Handles RSVP submissions and updates; internally enforces lock checking, capacity enforcement, and waitlist promotion in the correct order — IS3 before IS1, IS2 within the same transaction as a CONFIRMED → DECLINED transition
- Inputs: HTTP requests from Invitee UI carrying a token and Yes / No / Maybe; token validation from Invitation API
- Outputs: RSVP state changes written to database; HTTP responses to Invitee UI
- Ownership: Backend — Spring MVC controller + service (port 8280)
- Notes:
  - Lock, capacity, and waitlist rules are internal enforcement logic, not separate components
  - Concurrency strategy for CI1 and CI2 to be decided in Step 12
  - Unresolved — CONFIRMED → MAYBE behaviour: expected behaviour is not defined until A1 is resolved
  - Unresolved — concurrent IS1 and IS2 interaction: if capacity enforcement and waitlist promotion execute simultaneously, the confirmed count can become inconsistent; not covered by CI1 or CI2 alone
  - Unresolved — IS3 timing edge: "current time ≥ start time" must be evaluated at the point of the database write, not at request receipt; a request arriving just before start and processed just after has an undefined outcome
  - Unresolved — lifecycle action concurrent with in-flight RSVP write: if a cancel or close (UF4, UF5) and an RSVP write arrive simultaneously, the ordering and outcome are not defined; an RSVP could complete against a cancelled event
  - Unresolved — transaction boundary: it is not specified whether IS3, IS1, and IS2 execute within a single database transaction or across multiple; if the sequence is interrupted mid-way, the resulting partial state is undefined

**C6 — PostgreSQL Database**
- Responsibility: Persists all system state — events, hosts, invitees, unique tokens, RSVP states, waitlist positions
- Inputs: Write operations from C3, C4, C5
- Outputs: Read results to all backend components
- Ownership: Database layer (port 5432)
- Notes: Enforces DI1 (foreign keys), DI2 (NOT NULL host), DI3 (unique token), BI4 (unique invitee + event); row-level locking strategy for CI1 and CI2 to be decided in Step 12

---

**Unresolved external boundary — Email Dispatch**
Shape unknown (OQ-02). If email delivery is in scope, C4 gains an outbound dependency on an external mail service. If out of scope, no component is needed. C4 is designed so invitation records persist regardless of this decision.

---

### Key Design Choices

- **Single SPA for both surfaces** — Host UI and Invitee UI are two views in one React app; URL routing determines which surface is rendered
- **REST over HTTP** — boundary between frontend and backend; Spring MVC handles routing and input validation
- **Lock, capacity, and waitlist are internal to RSVP Management** — not separate components; they are ordered enforcement rules within one service boundary
- **Promotion is synchronous and transactional** — IS2 executes within the same database transaction as the triggering state change, not deferred
- **Email dispatch is isolated** — invitation records are written to the database before any dispatch attempt; the outcome of dispatch does not affect record creation

---

## Data Ownership and State Model

### Entities and Ownership

**Event**
- Source of truth: PostgreSQL — one record per event
- Who mutates it: Host only — creates via UF1; closes via UF4; cancels via UF5
- How it is read: Host reads via dashboard (C1 → C3); RSVP Management reads event state and start time on every RSVP write (C5)
- Derived state:
  - Attendance counts (confirmed / declined / Maybe / no response) — derived by aggregating RSVP records at read time; not stored on the event record
  - "Is at capacity?" — derived by comparing confirmed count to max-capacity at write time; not stored

**Invitation**
- Source of truth: PostgreSQL — one record per invitee per event; carries the unique token
- Who mutates it: Host only — created via UF2; no update path defined
- How it is read: RSVP Management reads it to validate incoming tokens (C5 → C4); Host reads the invitee list via dashboard
- Derived state: none — the token is stored directly; RSVP state is a separate record

**RSVP**
- Source of truth: PostgreSQL — one record per invitee per event
- Who mutates it:
  - Invitee — submits and updates response via C5
  - System — promotes WAITLISTED → CONFIRMED automatically via IS2; the invitee does not initiate this mutation
- How it is read: Invitee reads their own current state via the RSVP form; Host reads all RSVPs via dashboard
- Derived state:
  - "Confirmed count" — derived by counting RSVP records in CONFIRMED state for an event; computed at write time by IS1 and at read time for the dashboard; not stored separately

**Waitlist Order**
- Source of truth: PostgreSQL — ordering among WAITLISTED RSVP records; A2 assumes FIFO
- Who mutates it: System only — IS1 assigns position when routing to WAITLISTED; IS2 removes the front position on promotion
- How it is read: System reads it during IS2 to determine the next invitee to promote
- Derived state: "First waitlisted invitee" — derived from ordering; not a separately stored pointer

---

### State Machines

**Event states:**
```
OPEN   → CLOSED     (host closes — UF4)
OPEN   → CANCELLED  (host cancels — UF5)
CLOSED → CANCELLED  (host cancels a closed event — UF5)
```
Unresolved: can a CLOSED event be re-opened? Can a CANCELLED event be reversed? The brief does not say — these transitions are undefined and should not be assumed possible.

**RSVP states:**
```
NO_RESPONSE → CONFIRMED   (invitee submits Yes, capacity available)
NO_RESPONSE → WAITLISTED  (invitee submits Yes, capacity full — system routes)
NO_RESPONSE → DECLINED    (invitee submits No)
NO_RESPONSE → MAYBE       (invitee submits Maybe)

CONFIRMED   → DECLINED    (invitee changes to No — triggers IS2)
CONFIRMED   → MAYBE       (UNDEFINED — blocked by A1)
CONFIRMED   → WAITLISTED  (not a valid transition)

WAITLISTED  → CONFIRMED   (system promotes via IS2)
WAITLISTED  → DECLINED    (invitee changes to No — no IS2 trigger)
WAITLISTED  → MAYBE       (UNDEFINED — whether a waitlisted invitee can change to Maybe, and what that means for their waitlist position, is not defined)

DECLINED    → CONFIRMED   (invitee changes to Yes, capacity available)
DECLINED    → WAITLISTED  (invitee changes to Yes, capacity full)
DECLINED    → MAYBE       (invitee changes to Maybe — capacity impact governed by A1)

MAYBE       → CONFIRMED   (invitee changes to Yes, capacity available)
MAYBE       → WAITLISTED  (invitee changes to Yes, capacity full)
MAYBE       → DECLINED    (invitee changes to No)

ANY STATE   → locked      (event start time passes — no further transitions permitted; under lazy enforcement (BF1), this is a computed gate on each write, not a stored state transition — the notation is only accurate under eager enforcement)
```

---

### Lock State

Unresolved (BF1). Two options:
- **Lazy** — not a stored field; derived on each write by comparing current time to event start time
- **Eager** — stored as a flag on the event; set by a background job when start time is reached

The choice affects whether "locked" is a state in the event state machine or a computed gate on every RSVP write.

---

### Ownership and Source-of-Truth Ambiguities

These are identified gaps that must be addressed in later steps before implementation begins.

**Ambiguity 1 — "Can an RSVP be written?" has no single owner.**
The answer depends on two separate conditions: event status (OPEN/CLOSED/CANCELLED — owned by C3) and lock state (time-based — owned by C5 or BF1). Both gate the same write but are tracked separately. If they contradict — an event is OPEN but past its start time — no component currently arbitrates the combined check. This must be resolved when the write path is fully specified.

**Ambiguity 2 — Confirmed count is computed in two places.**
C5 reads it to enforce IS1 at write time; the dashboard read path computes it for display. Under concurrent writes, the count C5 reads before committing may be stale by the time the write lands. The database owns the underlying records, but enforcement of BI1 lives in C5. These are not yet coordinated. Must be addressed in the concurrency step (Step 12).

**Ambiguity 3 — A WAITLISTED invitee's RSVP is mutated without their action.**
IS2 changes a waitlisted invitee's record as a side effect of another invitee's write. Ownership of that record is effectively shared between the invitee and the system, with no defined authority boundary. This affects how the write path for IS2 should be authorised and logged.

**Ambiguity 4 — Waitlist ordering has no defined source of truth.**
A2 assumes FIFO, but whether "first" is derived from a creation timestamp or stored as an explicit position column is not specified. A timestamp is vulnerable to clock skew and transaction ordering ambiguity. A position column requires an owner that assigns and maintains it. Neither is resolved. Must be decided before the data model is implemented.

---

### Unresolved Ownership Boundary

**Host identity** depends entirely on OQ-01. Until the identity mechanism is defined, it is not possible to specify the source of truth for who the host is, how host identity is stored, or how C3 verifies it. This boundary must be resolved before the ownership model is complete.

---

## Trust Boundaries and Security Notes

### Trust Levels

Three distinct trust levels exist in this system:

- **Host** — trusted to manage events they own; trust establishment is undefined until OQ-01 is resolved
- **Invitee** — trusted to RSVP on a specific event via possession of a valid unique token; token possession is the only trust signal under the current model
- **Anonymous** — no defined access to any event data; currently no barrier exists on the host surface because OQ-01 is unresolved

---

### Where Trust Enters

**Boundary 1 — Host surface (C1 → C3)**
All event lifecycle operations — create, close, cancel, invite — enter here. This is the highest-privilege boundary in the system. Currently unprotected: no identity mechanism is defined for the host (OQ-01, AI1). Any actor who can reach C3's endpoints can perform host actions without verification.

**Boundary 2 — Invitee surface (C2 → C5)**
RSVP submissions and updates enter here. The unique token extracted from the URL is the sole trust signal. C5 delegates token validation to C4 before processing any write. Trust is established by token possession alone — no secondary verification exists.

**Boundary 3 — Internal backend (C5 → C4)**
C5 calls C4 to validate tokens. This is an internal call within the same backend process — both components operate at the same trust level. No cross-boundary authentication is required here, but it is a logical verification step that must not be skippable.

---

### Where Authorization Must Be Enforced

**Host-facing operations — authorization undefined, pending OQ-01:**

| Operation | Required check | Current status |
|---|---|---|
| Create event | Actor is a known host | Undefined — OQ-01 open |
| Invite people | Actor is the host of this event | Undefined — OQ-01 open |
| Close / cancel event | Actor is the host of this event | Undefined — OQ-01 open |
| View dashboard | Actor is the host of this event | Undefined — OQ-01 open |

**Invitee-facing and system operations — authorization defined:**

| Operation | Required check | Current status |
|---|---|---|
| Submit / update RSVP | Token is valid and scoped to this event | Defined — enforced by C4 |
| Waitlist promotion (IS2) | System-only — no invitee action | Defined — internal to C5 |

All host-facing authorization is blocked pending OQ-01. All invitee-facing authorization is token-based and defined, subject to token strength.

---

### Where Tenant Scope Matters

Each event is an isolated scope. Tenant isolation must be enforced at two points:

- **Token validation** — a token must be validated against a specific (token, event ID) pair, not as a globally valid token. A token valid for Event A must never grant access to Event B (TI1).
- **Event data reads** — the host dashboard and RSVP records for Event A must not be readable by the host of Event B or by any invitee (TI2). Currently no mechanism enforces this because OQ-01 is unresolved.

---

### Sensitive Data and Privileged Operations

**Sensitive data:**
- **Email addresses** — PII collected at invitation time and stored in the database; access should be scoped to the host of the event
- **Unique tokens** — function as credentials; if stored in plaintext and the database is compromised, every invitee across every event can be impersonated; hashing is a design option to evaluate in the risk step
- **Attendance data** — who is attending, declining, or waitlisted is private to the event; must not be readable outside the host surface

**Privileged operations:**
- Invitation generation — creates credentials; only the host may trigger this
- Event cancellation/closure — affects all invitees; only the host may trigger this
- Waitlist promotion (IS2) — mutates an invitee's RSVP record without their direct action; must remain system-only and must not be externally callable

---

### Security Concerns to Carry Forward

- **Token guessability** — tokens must use cryptographically random generation with sufficient entropy; what "sufficient" means is not yet defined (BI5, DI3)
- **Link sharing is undetectable** — a forwarded token is an impersonated invitee; the system cannot distinguish the real invitee from someone they shared the link with; accepted risk under the current model (AI2)
- **Host surface is currently open** — C3 has no defined access control; until OQ-01 is resolved, the host surface must be treated as unprotected; this is the most critical security gap in the current design

---

### Identified Trust Risks and Verification Gaps

**TR-01 — C5 over-trusts C4's token validation result.**
C5 validates a token via C4, then writes an RSVP. What C5 writes must be derived entirely from the (invitee, event) pair C4 returns — not from values supplied separately in the request. If C5 uses a caller-supplied event ID alongside C4's validated invitee ID, a caller can authenticate as one invitee and write an RSVP against a different event. C5 must treat C4's returned pair as the sole authoritative identity for the write.

**TR-02 — C5 over-trusts that event state is stable during a write.**
C5 reads event status and start time at the start of an RSVP write, then commits the change. If the event is cancelled or closed between the read and the commit, C5 writes against state that no longer holds. The current design does not hold event state stable across the operation — the assumption of stability is silent, not guaranteed.

**TR-03 — IS2 over-trusts that its selected invitee is still WAITLISTED at promotion time.**
IS2 selects the first waitlisted invitee, then promotes them. Between selection and commit, that invitee may have already changed their RSVP to DECLINED via UF7. IS2 trusts the snapshot it read at selection time. If it commits a promotion against a record that is no longer WAITLISTED, it either writes incorrect state or silently wastes a vacancy. IS2 must re-verify the target state at write time.

**TR-04 — The frontend is silently treated as a security boundary.**
C1 renders the host surface and C2 renders the invitee surface, but both are views in a React SPA. C3's backend endpoints are reachable by any HTTP client regardless of what the UI renders. The design currently implies access control by routing — only showing the dashboard to the host — but that is a UI convention, not a server-side enforcement. C3 must enforce authorization independently of what the frontend exposes.

---

## Concurrency and Correctness Notes

### Capacity-Related Races

**CC-01 — Two invitees simultaneously claim the last confirmed spot**
- Workflow / state: IS1, UF6 / UF7, BI1, CI1
- Risk: High — directly violates the capacity invariant
- What can go wrong: Both invitees read confirmed count < max-capacity before either write commits. Both proceed to CONFIRMED. Capacity is exceeded by one.
- Control note: ⚠️ HIGHEST PRIORITY — this is the only risk where a safety invariant (BI1) currently holds purely by timing luck, with no protective mechanism in place. Must be resolved before implementation. The confirmed count read and the RSVP write must be atomic. No other write to the same event's confirmed count may interleave. Mechanism — pessimistic row lock on the event record during IS1, or optimistic locking with a version field — is not yet chosen.

**CC-02 — IS1 and IS2 execute concurrently on the same event**
- Workflow / state: IS1 and IS2 running simultaneously, BI1, CI1, CI2
- Risk: Medium — does not overcrowd but creates a correctness gap
- What can go wrong: IS1 routes a new Yes invitee to WAITLISTED (capacity full). Simultaneously IS2 promotes the first waitlisted invitee because another invitee just declined. After both complete: a spot was available when the new invitee submitted Yes, but they were waitlisted rather than confirmed. No invariant is broken but a spot goes unfilled when a claimant was present.
- Control note: Not a safety violation (BI1 holds) but a liveness issue. The under-filled state may persist until the next IS2 trigger. Must be acknowledged explicitly; severity depends on how long the gap persists.

---

### Waitlist Promotion Races

**CC-03 — Two concurrent IS2 promotions reading a stale waitlist**
- Workflow / state: IS2, two concurrent CONFIRMED → DECLINED transitions, CI2
- Risk: High — results in a vacancy going unfilled and CI2 violated
- What can go wrong: Two IS2 runs execute concurrently, each reading the same stale snapshot of the waitlist. Both identify the same invitee as "first waitlisted" and attempt the same promotion. The first succeeds; the second targets a record that is no longer WAITLISTED and is wasted or fails silently. The second vacancy goes unfilled — no further promotion is attempted for it.
- Control note: IS2 must be serialised per event. The promotion and the triggering decline must be committed in the same transaction. IS2 must re-read the waitlist head inside the transaction, not from a snapshot taken before the transaction began.

**CC-04 — IS2 selects a waitlisted invitee who has since declined**
- Workflow / state: IS2, WAITLISTED → DECLINED concurrent, TR-03
- Risk: Medium — writes an incorrect state for the promoted invitee
- What can go wrong: IS2 selects invitee X as the first waitlisted. Concurrently, X changes their own RSVP to DECLINED. IS2 promotes X to CONFIRMED against a record that is no longer WAITLISTED. X is confirmed against their own will.
- Control note: IS2 must re-verify the target invitee is still WAITLISTED at write time, not just at selection time. The write must fail and retry — or skip to the next waitlisted invitee — if the state has changed since selection.

**CC-05 — IS2 triggered by a WAITLISTED → CONFIRMED promotion**
- Workflow / state: IS2 trigger condition, BI3
- Risk: Low — a design ambiguity that could cause a cascade
- What can go wrong: If IS2 fires on any transition that results in CONFIRMED (including WAITLISTED → CONFIRMED), it triggers recursively and promotes the entire waitlist for one vacancy.
- Control note: IS2 must only fire on CONFIRMED → DECLINED transitions, never on WAITLISTED → CONFIRMED. The trigger condition must be explicit and one-directional.

---

### Duplicate and Retry Issues

**CC-06 — Duplicate RSVP submission from the same invitee**
- Workflow / state: UF6, UF7, BI4
- Risk: Medium — can violate BI4 or cause IS1 to run twice
- What can go wrong: A network timeout causes the client to retry. Two identical writes arrive for the same (invitee, event). IS1 runs twice, potentially routing the invitee to CONFIRMED on the first write and attempting it again on the second.
- Control note: The (invitee, event) unique constraint prevents duplicate records. The write must be idempotent: if the RSVP already exists with the submitted response, the second write is a no-op and IS1 does not re-run.

**CC-07 — Retry of a CONFIRMED → DECLINED write after IS2 has already fired**
- Workflow / state: UF7, IS2, CI2
- Risk: High — causes IS2 to fire twice for one vacancy
- What can go wrong: Invitee X successfully changes CONFIRMED → DECLINED and IS2 promotes invitee Y. X's client retries due to a timeout. The retry is processed as a new CONFIRMED → DECLINED on X (already DECLINED) and IS2 fires again, promoting a second waitlisted invitee against a vacancy that no longer exists.
- Control note: The CONFIRMED → DECLINED write must be idempotent. If the invitee is already DECLINED, a retry must be a no-op and must not re-trigger IS2.

---

### Event Lifecycle Races

**CC-08 — Event closed or cancelled while an RSVP write is in flight**
- Workflow / state: UF5 concurrent with UF6 / UF7, FF2, TR-02
- Risk: Medium — bypasses the event-state gate in FF2
- What can go wrong: C5 reads event status as OPEN at the start of an RSVP write. The host concurrently closes or cancels the event. C5 commits an RSVP against an event that is no longer open.
- Control note: C5 must read event state within the same transaction as the RSVP write, or use optimistic locking on the event record. Reading state before the transaction begins is not sufficient.

---

### Time-Based Issues

**CC-09 — RSVP request arrives just before the lock, processed just after**
- Workflow / state: IS3, BI2, G5
- Risk: Medium — bypasses the lock and violates BI2
- What can go wrong: A request passes IS3's time check at receipt but by the time the write commits the event start time has passed. If IS3 checks the clock at request arrival rather than at write time, the lock is bypassed.
- Control note: The time comparison in IS3 must happen as close to the commit as possible — ideally within the same transaction. Clock skew between application server and database server is an additional risk if they are separate processes.

---

### Out-of-Order Updates

**CC-10 — Two RSVP updates from the same invitee arrive out of order**
- Workflow / state: UF7, RSVP state machine
- Risk: Low — no invariant is broken but the final state may not reflect the invitee's intent
- What can go wrong: An invitee submits Yes then immediately submits No. Due to network conditions No is processed first and Yes arrives second. The final stored state is Yes, not the intended No. Last-write-wins does not mean last-submitted-wins.
- Control note: No ordering mechanism is currently defined. A client-side sequence number or submission timestamp would allow the server to reject out-of-order writes. Without one the system is vulnerable on flaky connections. No mechanism is chosen yet.

---

## Scalability and Multi-Tenancy Notes

### Likely Growth Axes

- **Invitees per event** — a small social event might have 10 invitees; a conference or public-facing event could have thousands. Most write-path logic (IS1, IS2, IS3) runs per event, so this is the dominant scaling dimension.
- **Concurrent RSVP submissions** — a popular event with limited capacity will produce a burst of simultaneous Yes submissions when invitations go out. This directly amplifies CC-01.
- **Number of simultaneous events** — more events means more concurrent write activity across the database. Each event is isolated by event_id but all share the same tables and the same write node.
- **Dashboard read frequency** — if the host polls for updates, repeated aggregation queries over RSVP records accumulate. Under modest load this is trivial; under high polling frequency or large invitee lists it becomes a bottleneck.
- **Email dispatch volume** — if OQ-02 resolves to real email delivery, a large invite blast is a bursty external call that could block the invitation write path.

---

### Likely First Bottlenecks

**Bottleneck 1 — The RSVP write path under concurrent load.**
IS1 runs a confirmed count query on every Yes submission. Under a simultaneous burst, many concurrent count queries hit the same event's RSVP rows. Without a covering index on (event_id, status) this becomes a table scan under load. Even with an index, the event row becomes a hot contention point when a lock is added to fix CC-01.

**Bottleneck 2 — The confirmed count query as a shared hot read.**
IS1 counts CONFIRMED RSVPs inside the write transaction. Under concurrent load, multiple transactions count the same rows simultaneously. A pessimistic lock serialises them; optimistic locking causes retries under high contention. Either way, the count query is load-bearing and must be fast.

**Bottleneck 3 — The dashboard aggregation under polling.**
The host dashboard derives attendance counts by aggregating RSVP records at read time. For small events this is trivial. For large events with frequent polling it becomes a repeated aggregation over a growing table. No caching or materialized count exists in the current model.

**Bottleneck 4 — Single PostgreSQL write node.**
All writes go to one database instance. This is sufficient for local and development use. Any horizontal scaling of the backend requires that all concurrency controls be enforced at the database level, not in application memory, because multiple backend instances cannot share in-process locks.

---

### What Is Sufficient Now

- A single PostgreSQL instance handles all reads and writes for a small-to-moderate number of events with modest invitee counts and low concurrent load
- A single Spring Boot instance is sufficient for local and development use
- No caching layer is needed for small invitee lists
- No async messaging is needed if IS2 runs synchronously within the RSVP write transaction
- Lazy lock enforcement (IS3 as a per-request check) avoids the need for a background job at this scale

---

### What Would Require Later Architectural Change

| Pressure | Current approach | Change required |
|---|---|---|
| High concurrent Yes submissions | Count query with no lock | Database-level row lock or optimistic concurrency on event record |
| Frequent dashboard polling | Aggregation query on every read | Read replica or materialized confirmed count on event record |
| Large invitation blasts (if OQ-02 resolves to real email) | Synchronous dispatch in C4 | Async email queue to decouple dispatch from the write path |
| Multiple backend instances | No shared state yet | Concurrency controls must be database-level, not in-process |
| Very large events (thousands of invitees) | Full RSVP table scan per aggregation | Covering index on (event_id, status); possible materialized count |

---

### Day-One Risks Framed as Growth Concerns

**Risk 1 — The hot-event burst is a day-one correctness and scale problem, not a future growth concern.**
The scalability framing above implies concurrent RSVP pressure arrives gradually as the system grows. It does not. A single event with limited capacity and a large invitation list produces the thundering herd on its first use. The moment 500 invitees receive their link for a 20-spot event, the first 20 Yes submissions race CC-01 and hundreds of IS1 count queries hit the same rows simultaneously. CC-01 (already marked highest priority) and the confirmed count bottleneck are not separate concerns arriving at different times — they converge at the moment of the first large event's invitation dispatch. This is a day-one risk that must be resolved before any event of meaningful size is run.

**Risk 2 — "Live dashboard" is an unresolved interpretation of F5, not a scalability bottleneck.**
F5 states the host sees a live attendance dashboard. The current architecture (REST over HTTP, request-response) supports only pull — the host must refresh to see updates. If "live" means the count updates without a page refresh, the architecture needs a push mechanism (WebSockets, server-sent events) that does not currently exist. This is not a load problem that emerges at scale — it is a feature interpretation gap present from the first event. The scalability notes treat polling frequency as a future concern, but if F5 requires true real-time push, the correct architectural boundary between C1 and C3 has not been decided. Must be resolved before frontend implementation begins.

---

### Multi-Tenancy Notes

The natural "tenant" in this system is the event. Current isolation is **logical** — rows are filtered by event_id — not physical. This means:

- All events share the same tables, the same write node, and the same connection pool
- One high-traffic event creates lock contention and query load that affects all other events running simultaneously
- No per-event resource quotas or rate limits exist
- Physical isolation (separate schema or database per event) would eliminate cross-event interference but is not warranted at the current stated scale

Logical isolation is appropriate for a local or small-scale deployment. It becomes a liability if the system is ever used for many simultaneous high-traffic events.

---

## Risks and Failure Notes

### Correctness Risks

**CR-01 — Capacity exceeded under concurrent writes**
- Risk: Confirmed attendance exceeds max-capacity
- Failure shape: More CONFIRMED RSVPs than max-capacity; invitees who should be WAITLISTED are confirmed; host sees an overcrowded event
- Cause: No atomicity between the confirmed count check and the RSVP write; two concurrent Yes writes both pass IS1 before either commits (CC-01)
- Note: Highest priority; must be resolved before any capacity-constrained event is run; database-level locking or optimistic concurrency required

**CR-02 — Double waitlist promotion**
- Risk: Two invitees promoted for one vacancy, or IS2 fires twice on a retry
- Failure shape: Confirmed count exceeds capacity by one; or a second vacancy goes unfilled because both IS2 runs targeted the same waitlisted invitee (CC-03, CC-07)
- Cause: IS2 runs are not serialised per event; or a retry of CONFIRMED → DECLINED re-triggers IS2 when the invitee is already DECLINED
- Note: IS2 must be idempotent and serialised per event; retried writes must not re-trigger IS2

**CR-03 — RSVP written against a cancelled or closed event**
- Risk: An RSVP is accepted for an event that is no longer open
- Failure shape: An invitee appears confirmed or declined on a cancelled event; host sees incorrect attendance data (CC-08, TR-02)
- Cause: C5 reads event state before the transaction begins; the host cancels or closes concurrently; C5 commits against stale state
- Note: Event state must be verified within the same transaction as the RSVP write

**CR-04 — Lock bypassed at the event start boundary**
- Risk: An RSVP change is accepted after the event has started
- Failure shape: The attendance record is not final; an invitee changes their response after the event begins, violating BI2 (CC-09)
- Cause: IS3 time check runs at request receipt; the database write commits after the start time has passed
- Note: Time comparison must occur as close to the commit as possible — ideally within the same transaction

**CR-05 — CONFIRMED → MAYBE produces undefined system state**
- Risk: A confirmed invitee changes to Maybe and the system takes an unintended action
- Failure shape: The confirmed spot is either silently held (contradicting A1) or silently released without triggering IS2; either corrupts the capacity model (UF7)
- Cause: A1 is unresolved; the CONFIRMED → MAYBE transition has no defined outcome
- Note: Must be explicitly defined before the RSVP update endpoint is implemented; blocked by A1

---

### Dependency Risks

**DR-01 — Email delivery fails or is out of scope**
- Risk: Invitees never receive their unique links
- Failure shape: Invitations are recorded in the database but invitees have no way to access their RSVP form; the host believes invitations were sent; the event receives no responses
- Cause: OQ-02 is unresolved; if dispatch fails silently, the invitation record exists but the invitee is unreachable
- Note: C4 must persist invitation records independently of dispatch outcome; the host needs visibility into dispatch status; entirely unaddressed until OQ-02 is resolved

**DR-02 — PostgreSQL unavailability**
- Risk: The entire system becomes unavailable
- Failure shape: All reads and writes fail; hosts cannot view dashboards; invitees cannot submit RSVPs
- Cause: Single database instance with no replica or failover
- Note: Acceptable for local and development use; any hosted deployment must address database availability

**DR-03 — Clock drift between application server and database**
- Risk: IS3 time comparison is inconsistent
- Failure shape: RSVPs accepted after the event starts if the application clock is behind; or rejected before the event starts if it is ahead
- Cause: IS3 uses one clock at request time; the database commits under its own clock; if they diverge, the lock boundary is imprecise
- Note: A single authoritative time source — preferably the database server's clock, evaluated within the transaction — eliminates this risk

---

### Operational Risks

**OR-01 — No recovery path for a lost invitation link**
- Risk: An invitee who never receives or loses their link cannot RSVP
- Failure shape: Invitee contacts the host; the host has no self-service way to resend; the invitee is locked out unless re-invited, which may generate a new token and invalidate the old one
- Cause: No link recovery path is defined; OQ-01 provides no login fallback under the current model
- Note: Operational burden falls entirely on the host; must be addressed before rollout

**OR-02 — No audit trail for host actions**
- Risk: A host cancels an event and there is no record of when or why
- Failure shape: No ability to diagnose what happened; disputed cancellations cannot be investigated
- Cause: No event log or audit trail is defined in the current data model
- Note: Not a correctness risk at this stage but an operational gap; relevant if the system handles events of consequence

**OR-03 — Host loses access to their identity mechanism**
- Risk: The event becomes unmanageable if the host cannot be identified
- Failure shape: No one can close, cancel, or invite people; the event is frozen in its current state
- Cause: OQ-01 is unresolved; no recovery path for host identity is defined
- Note: Severity depends entirely on how OQ-01 resolves; must be addressed alongside it

**OR-04 — Token-in-URL logged by infrastructure**
- Risk: Unique link tokens — which function as credentials — are captured in server logs, browser history, and Referer headers from the first request
- Failure shape: Any actor with server log access can read valid tokens in plaintext and impersonate any invitee for any event; BI5 ("links must be unguessable") is satisfied technically but undermined operationally; the security model is not broken by an attacker — it is broken by routine infrastructure
- Cause: Tokens appear in the request URL; web server access logs, application request logs, reverse proxies, browser history, and Referer headers all capture URLs automatically
- Note: Arises directly from the token-based identity model (TC3); "never put credentials in URLs" is a canonical security antipattern; token placement strategy must be decided before the invitation link format is finalised

---

### Assumption Failures

**AF-01 — A1 is false: Maybe counts toward capacity**
- Risk: The entire capacity model is wrong
- Failure shape: Events appear at capacity with few confirmed attendees; the CONFIRMED → MAYBE transition invalidates the state machine; IS1, IS2, UF7, and the state machine all require redesign
- Cause: A1 assumed Maybe does not hold a spot; if the product decision is the opposite, the capacity threshold and all transition logic change
- Note: Marked priority; must be resolved before IS1 or the RSVP state machine is implemented

**AF-02 — A2 is false: Waitlist order is not FIFO**
- Risk: The wrong invitee is promoted; promotion order is disputed
- Failure shape: A host expects to prioritise certain invitees; FIFO promotes by submission time, which may not match intent; the waitlist position data model changes
- Cause: A2 assumed FIFO; if host-controlled ordering is required, IS2 and the data model both change
- Note: Must be resolved before IS2 is implemented

**AF-03 — A3 is false: Cancellation and closing are the same state**
- Risk: The event state machine is wrong from the start
- Failure shape: A closed event is treated as cancelled; RSVPs locked permanently when they should only be paused; OQ-09 produces the wrong outcome for waitlisted invitees
- Cause: A3 assumed two distinct states with different semantics; if they collapse, UF4 and UF5 merge and OQ-09 resolution changes
- Note: Must be resolved before the event lifecycle endpoints are implemented

**AF-04 — A5 is false: Unique links are reusable or transferable across events**
- Risk: The identity model and tenant isolation break
- Failure shape: A token from Event A is used to RSVP on Event B; TI1 is violated; cross-event RSVP writes become possible
- Cause: A5 assumed per-invitee, per-event token uniqueness; TI1 depends on this being true
- Note: Scoping token lookup by (token, event_id) in C4 mitigates this regardless of whether A5 holds; low risk if that scoping is enforced

---

### Accepted Product Consequences

**AP-01 — Waitlist promotion is invisible to the promoted invitee**
- Risk: A promoted invitee does not attend because they do not know they were confirmed
- Failure shape: Confirmed count appears full; the promoted invitee stays away; the event is underattended despite looking at capacity on the host's dashboard
- Cause: IS2 promotes silently; NG2 explicitly excludes notifications and follow-up communication; no push or pull signal informs the invitee of their new state unless they check their unique link unprompted
- Note: This is a deliberate product consequence of NG2, not a correctness bug. The decision to exclude notifications is explicit. The consequence — that the core waitlist promotion feature silently fails in the most likely usage pattern — must be acknowledged and accepted by the product owner before the system is used for events of any consequence.

---

## Alternatives and Tradeoffs

### Proposed Design (baseline)

Token-based invitee identity, synchronous RSVP write path (IS3 → IS1 → IS2 in one transaction), derived confirmed count, lazy lock enforcement, pull-based dashboard, single PostgreSQL instance.

---

### Alternative 1 — Login-Based Identity Instead of Token-Based Links

**What it is:** Users create accounts and log in. The host authenticates as themselves; invitees authenticate before RSVPing. Unique links still exist to deliver the invitation, but clicking the link prompts login or registration rather than granting direct access.

| Dimension | Proposed (token-based) | Alternative (login-based) |
|---|---|---|
| Correctness | Invitee identity is token possession; link sharing is undetectable (AI2); host identity has no mechanism until OQ-01 resolves (AI1) | Identity is account-bound; AI1 and AI2 are solvable; host identity enforcement is concrete |
| Complexity | No auth system; low frontend complexity; no session management | Requires registration, login, password storage or OAuth, session management; significant added scope |
| Operational burden | Lost links require host re-invite; no account recovery path (OR-01, OR-03) | Standard account recovery; operationally familiar but more infrastructure |
| Scalability | Stateless token validation scales horizontally; no session state | Sessions add statefulness; mitigated with JWT or distributed session store |
| Future changeability | Hard to add identity enforcement later without breaking existing links | Auth system is a foundation; adding permissions, roles, or audit trails is straightforward |

**When to choose this:** If host identity enforcement (AI1) must be concrete before launch, or if the system needs any form of audit trail or per-user event management.

---

### Alternative 2 — Async RSVP Processing via a Queue

**What it is:** Invitees submit RSVPs to a queue rather than directly to the database. A worker processes the queue sequentially per event, applying IS3, IS1, and IS2 in order. The invitee receives a "your RSVP is being processed" response and checks back for their confirmed state.

| Dimension | Proposed (synchronous) | Alternative (async queue) |
|---|---|---|
| Correctness | CC-01, CC-03, CC-07 require careful locking; concurrent writes race | Queue serialises all RSVP writes per event; CC-01, CC-03, CC-07 are eliminated |
| Complexity | Single synchronous path; one transaction per write; easier to reason about locally | Requires message queue infrastructure; worker process; dead-letter handling; eventual consistency |
| Operational burden | A failed write fails immediately; invitee sees the error | A failed queue message may retry silently; the invitee may see "processing" indefinitely; queue monitoring required |
| Scalability | Write path serialises under lock; hot events queue behind a single row lock | Queue naturally absorbs burst; worker processes at its own pace |
| Future changeability | Easy to reason about; hard to parallelise per-event writes without the identified concurrency risks | Async model handles scale well but makes the live dashboard problem (Risk 2, Step 13) worse — confirmed state is eventually consistent |

**When to choose this:** If the hot-event burst (Step 13, Day-One Risk 1) is a hard requirement and CC-01 must be eliminated rather than mitigated. The cost is UX regression and significantly more operational infrastructure.

---

### Alternative 3 — Materialized Confirmed Count vs Derived Count

**What it is:** Instead of deriving the confirmed count by querying RSVP records on every IS1 check, store a `confirmed_count` column directly on the event record. IS1 reads and increments it atomically; IS2 decrements it.

| Dimension | Proposed (derived count) | Alternative (materialized count) |
|---|---|---|
| Correctness | COUNT query under concurrent writes may read stale data before a lock is chosen | Atomic increment on a single column is a well-understood operation; capacity enforcement reduces to one atomic row update |
| Complexity | No extra column to maintain; count is always derivable from RSVP records | Count column must be kept consistent with RSVP records at all times; divergence is silent |
| Operational burden | Count is always recoverable by re-querying RSVP records | Count and RSVP records can diverge; reconciliation requires detecting the mismatch |
| Scalability | COUNT query grows with RSVP volume; multiple transactions scan the same rows under concurrency | Single-row read and atomic update is fast regardless of event size |
| Future changeability | Derived state is always correct by definition | Materialized count is a second source of truth (Ambiguity 2, Step 10); any future logic that modifies RSVPs without updating the count introduces a bug |

**When to choose this:** If the COUNT query under concurrent load is measured as the primary bottleneck. Only worthwhile if the count column is treated as the single authoritative source for capacity checks, not as a cache alongside the RSVP table.

---

### Summary

| Alternative | Main benefit | Main cost | Recommended if |
|---|---|---|---|
| Login-based identity | Resolves AI1 concretely; enables audit trails | Large scope increase; registration friction for invitees | Host identity must be enforced before launch |
| Async queue | Eliminates CC-01, CC-03 entirely | Eventual consistency; worse UX; more infrastructure | Hot-event burst is a hard requirement |
| Materialized confirmed count | Fast, simple capacity enforcement | New source of truth; silent divergence risk | COUNT query is measured as a bottleneck |

---

### Tradeoff Notes

**TN-01 — The design has already committed to token-based invitee identity.**
OQ-01 is framed as "identity mechanism not yet determined," which implies the decision is still open for both host and invitee. It is not. Every invitee-facing component — C2, C4, C5, DI3, BI5, AI2, OR-04 — is structurally built around token possession. The invitee identity model has been decided. OQ-01 is only genuinely open for host identity. Alternative 1 is available for host identity; it is not realistically available for invitee identity without redesigning the invitee-facing surface. The design should acknowledge this commitment explicitly rather than presenting token-based identity as a pending decision.

**TN-02 — Resolving CC-01 through locking introduces a throughput tradeoff.**
Whichever locking mechanism is chosen to fix CC-01 — pessimistic or optimistic — it converts "incorrect results" into either serialised writes (pessimistic) or retry pressure (optimistic). The fix has a throughput cost. For the hot-event burst scenario, that cost may be significant. This tradeoff must be acknowledged when the locking mechanism is chosen.

---

## Rollout and Migration Notes

### Context

This is a greenfield application with no existing users, no existing data, and no live system to migrate from. There are no data migrations to run and no backwards compatibility constraints for existing users. Rollout concerns are about implementation sequencing, component dependencies, and what happens if key design decisions change after implementation begins.

---

### Implementation Sequencing

**Phase 1 — Data foundation**
Database schema for events, invitations, and RSVP records.
- Depends on: nothing
- Blocks: everything
- Note: schema must reflect the RSVP state machine from Step 10, including all defined states; undefined states (CONFIRMED → MAYBE) should not be encoded until A1 is resolved

**Phase 2 — Event creation and invitation generation**
C3 (Event API) and C4 (Invitation API) — host can create an event and generate tokens for invitees.
- Depends on: Phase 1
- Blocks: Phase 3 (no valid tokens exist until invitations are created)
- Note: token generation and (token, event_id) scoping must be correct before any RSVP flow can be tested

**Phase 3 — Basic RSVP submission without capacity**
C5 (RSVP Management) with IS3 (lock check) only. Invitee can submit Yes / No / Maybe on an event with no max-capacity.
- Depends on: Phase 2
- Blocks: Phase 4
- Note: this phase can be fully tested without resolving A1 or choosing a locking mechanism; capacity logic is not yet involved

**Phase 4 — Capacity enforcement and waitlist**
IS1 and IS2 added to the RSVP write path. Events with max-capacity enforce the ceiling; waitlist promotion fires on decline.
- Depends on: Phase 3; A1 resolved; all Step 12 concurrency decisions affecting IS1 and IS2 chosen and implemented
- Blocks: meaningful end-to-end testing of the full RSVP state machine
- Note: Phase 4 is the first phase where correctness depends on concurrency decisions. No capacity-constrained event should be considered production-ready until all Step 12 decisions affecting IS1 and IS2 — including CC-01, CC-02, CC-03, CC-04, CC-06, and CC-07 — are implemented and tested. Shipping Phase 4 without these in place means shipping a system that can silently violate BI1.

**Phase 5 — Event lifecycle**
UF4 (close) and UF5 (cancel).
- Depends on: Phase 2 (events must exist)
- Note: OQ-09 (what happens to waitlisted invitees on cancel) must be resolved before UF5 is implemented; UF4 can proceed independently

**Phase 6 — Host dashboard**
C1 / C3 read path — attendance counts and attendee list.
- Depends on: Phase 3 (RSVP records must exist to aggregate)
- Note: the interpretation of F5 ("live") must be resolved before the dashboard frontend is built; if "live" requires push, this phase requires a different architecture than pull

**Phase 7 — Email dispatch (conditional)**
C4 gains an outbound dependency on an email service.
- Depends on: OQ-02 resolved; Phase 2 complete
- Note: C4 is designed to persist invitation records independently of dispatch; this phase can be added without changing Phase 2's data model

---

### What Must Exist Before Testing

| What is being tested | Must exist first |
|---|---|
| Any RSVP flow | Phase 1 + Phase 2 (valid token in database) |
| Capacity enforcement (IS1) | Phase 3 working; A1 resolved; all relevant CC items from Step 12 implemented |
| Waitlist promotion (IS2) | IS1 working; at least one WAITLISTED record in the database |
| RSVP lock (IS3) | An event with a start time in the past |
| Host dashboard | RSVP records for at least one event |
| End-to-end cancel flow | OQ-09 resolved |
| End-to-end live dashboard | F5 interpretation resolved |

---

### Operational Assumptions

- PostgreSQL must be running on port 5432 with the `hero` database and schema created before any backend component starts
- Default credentials (`postgres / 1234`) or an updated `application.yml` must be in place before the backend starts
- Both backend (port 8280) and frontend (port 5171) must be running for any end-to-end test
- No cloud infrastructure is assumed; all rollout notes apply to local and development environments unless OQ-03 is resolved

---

### Rollback Concerns by Decision

**If OQ-01 resolves to "login required for invitees" after Phases 2–3 are built:**
C2, C4, and C5 are built around token-based invitee identity (TN-01). A login requirement changes the invitee-facing surface entirely — token generation, token validation, and URL structure all change. Any frontend code built around token-in-URL must be rebuilt. This is the highest-impact late decision in the design; the earlier it is resolved, the smaller the rollback cost.

**If A1 resolves to "Maybe counts toward capacity" after Phase 4 is built:**
IS1, IS2, the RSVP state machine, and any tests written against the current capacity model are invalid. Existing Maybe RSVPs in the database would retroactively affect capacity counts on live events — which could overflow capacity that was previously valid. This is a data integrity risk if the decision changes post-launch, not just a code change.

**If the CC-01 locking mechanism must change after Phase 4 is built:**
Business logic in IS1 changes but the data model does not. Medium rollback risk — behaviour changes but no schema migration is required.

**If F5 ("live") requires push after Phase 6 is built as pull:**
The dashboard frontend and the C1–C3 boundary must be rebuilt for WebSockets or server-sent events. Pull and push are different architectural patterns with no incremental migration path between them. Resolving F5's interpretation before Phase 6 begins eliminates this risk entirely.

**If OQ-02 resolves to "email required" after the system ships without it:**
Existing invitation records were created but never dispatched. Historical invitees have no link. Those records may need to be re-sent or considered invalid depending on event status. C4's design — records persist independently of dispatch — means the data is intact; the operational question is what to do with undispatched invitations.
