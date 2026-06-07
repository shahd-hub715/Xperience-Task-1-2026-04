# Xperience Task — Event RSVP Manager

> **Xperience Educational Program — Task 01**
> Practice guide: [Building a Design File from Scratch with an AI Partner](https://xperience.works/educational-content/building-a-design-file-from-scratch-with-an-ai-partner)

---

## What is this task?

You are given an empty full-stack scaffold (Spring Boot + React).
Your job is **not** to just build features—it is to first produce a serious design file by following the 18-step guide above, using an AI partner (GitHub Copilot, ChatGPT, Claude, etc.).

Only after your design file is solid should you move on to implementation.

---

## The Feature: Event RSVP Manager

A small web application where users can host events and collect RSVPs from invitees.

### Raw feature brief

- A user can create an event with a title, description, date/time, location, and optional max-capacity.
- The creator becomes the **host** of that event.
- The host can invite people by email.
- Each invitee receives a unique link and can respond: **Yes / No / Maybe**.
- The host sees a live attendance dashboard with counts and a list of attendees.
- If the event has a max-capacity and it is reached, new "Yes" RSVPs go to a **waitlist**.
- A waitlisted attendee automatically moves to confirmed if a confirmed attendee changes their RSVP to No.
- The host can cancel the event or close it to further responses at any time.
- An invitee can change their RSVP at any point **before** the event starts.
- After the event start time, all RSVPs are locked.

Use this brief as input for **Step 02 – Capture the raw feature brief** in the guide.

---

## Tech Stack (already set up for you)

| Layer     | Technology                        |
|-----------|-----------------------------------|
| Backend   | Java 17, Spring Boot 4, Spring MVC, Spring Data JPA |
| Database  | PostgreSQL                        |
| Frontend  | React 19, TypeScript, Vite        |

---

## Prerequisites

Make sure the following are installed on your machine:

- [ ] Java 17+
- [ ] Maven (or use the included `mvnw` wrapper)
- [ ] Node.js 20+
- [ ] PostgreSQL (running locally on port 5432)
- [ ] pgAdmin (PostgreSQL GUI client)
- [ ] Git
- [ ] VS Code
- [ ] GitHub Copilot extension for VS Code

### Install VS Code

Download and install from [code.visualstudio.com](https://code.visualstudio.com/). Accept the defaults.

### Install pgAdmin

Download and install from [pgadmin.org](https://www.pgadmin.org/download/). During installation, **set the PostgreSQL superuser password to `1234`**. Use pgAdmin to create the database and run SQL in the Setup step below.

### Install GitHub Copilot

1. Open VS Code → press `Ctrl+Shift+X` to open the Extensions panel
2. Search for **GitHub Copilot** and click **Install**
3. When prompted, sign in with your GitHub account
4. Also install **GitHub Copilot Chat** for the chat interface

You'll use Copilot as your AI partner throughout the design phase.

---

## Setup

### 1. Clone the repository

```bash
git clone git@github.com:RamiY123/Xperience-Task-1-2026-04.git
cd Xperience-Task-1-2026-04
```

### 2. Create the database

Open your PostgreSQL client and run:

```sql
CREATE DATABASE hero;
CREATE SCHEMA hero;
```

The default credentials expected by the app are `postgres / 1234`.
If you used a different password, update `hero-backend/src/main/resources/application.yml`.

### 3. Start the application

On Windows (PowerShell):

```powershell
.\start.ps1
```

This opens two terminal windows:
- **Backend** → http://localhost:8280
- **Frontend** → http://localhost:5171

---

## Your Task

### Phase 1 — Design (mandatory)

Open `DESIGN.md` at the root of the repo. This is your design file.

Work through all **18 steps** of the guide with your AI partner:

| # | Step |
|---|------|
| 01 | Choose the setup |
| 02 | Capture the raw feature brief |
| 03 | Write the problem statement |
| 04 | Define goals and non-goals |
| 05 | Capture context and constraints |
| 06 | Separate facts, assumptions, and open questions |
| 07 | Identify actors and workflows |
| 08 | Define invariants |
| 09 | Generate first-pass architecture |
| 10 | Define data ownership and state model |
| 11 | Add trust boundaries and security notes |
| 12 | Add concurrency and correctness notes |
| 13 | Add scalability and multi-tenancy notes |
| 14 | Add risks and failure notes |
| 15 | Generate alternatives and tradeoffs |
| 16 | Add rollout / migration notes |
| 17 | Assemble the first complete design draft |
| 18 | Run the pre-review weakness check |

Each step produces a section you copy into `DESIGN.md`.
Working notes and AI back-and-forth stay **out** of the file.

> A good design file for this feature should cover: a clear problem statement, explicit state machines for both Event and RSVP, named invariants (capacity, lock-after-start, waitlist promotion), trust boundaries between host and invitee, and at least one concurrency scenario (two simultaneous RSVPs filling the last spot).

### Phase 2 — Implementation (stretch goal)

Once your design file passes your own pre-review check (Step 18), implement the feature in the scaffold:

- Add JPA entities and repositories in `hero-backend/`
- Add REST endpoints in a new controller
- Wire up the React frontend in `hero-frontend/src/`

Commit your design file and code separately so reviewers can read the design before the code.

---

## Deliverables

| Deliverable | Where |
|-------------|-------|
| Completed design file | `DESIGN.md` |
| (Stretch) Working implementation | `hero-backend/` and `hero-frontend/` |

---

## Evaluation Criteria

Your design file will be reviewed against the **Definition of Success** from the guide:

- [ ] Clear problem statement
- [ ] Bounded scope (explicit non-goals)
- [ ] Visible assumptions, separated from facts
- [ ] Explicit workflows for all key actors
- [ ] Named invariants
- [ ] Real architecture boundaries
- [ ] Explicit state ownership
- [ ] First-pass trust / concurrency / scale treatment
- [ ] Visible risks and tradeoffs
- [ ] Unresolved open questions listed

---

## Tips

- Follow the guide steps **in order**. Jumping to architecture before invariants produces weak design.
- Interrogate AI output—do not copy it directly into `DESIGN.md`.
- Open questions are a first-class section, not a cosmetic one.
- Fluent AI output is not the same as disciplined output.

Good luck.
