# Session Persistence Architecture v0.1

## Goal

Translate the Session Data Model into a practical persistence architecture for Muninn v1.

The purpose of this document is not only to define how Sessions are stored, but also to document the architectural decisions that connect the research findings from previous issues to a working implementation.

---

# Core Question

How should Session exist in application state and persistence storage?

---

# Design Context

Issue #11 defined what a Session is conceptually.

A Session represents a temporary cluster of image-saving activity that approximates a user's short-term context.

Issue #12 focuses on how that concept should exist in software without introducing unnecessary complexity or premature assumptions.

The architecture should remain:

- Minimal
- Testable
- Extensible
- Consistent with Save First, Enrich Later

---

# Architectural Decisions

## Decision 1: Sessions Are System Generated

### Decision

Sessions are created automatically by the system.

Users do not manually create Sessions.

### Rationale

Muninn is designed around low-friction capture.

Requiring users to explicitly create, name, or manage Sessions would introduce cognitive overhead during saving.

The system should capture first and allow interpretation later.

This decision directly supports:

```text
Save First, Enrich Later
```

### Consequences

Benefits:

- Zero setup cost
- Consistent capture behavior
- Lower cognitive load

Trade-offs:

- Session boundaries are inferred rather than explicitly declared
- Future correction mechanisms may be necessary

---

## Decision 2: An Image Belongs to Exactly One Session

### Decision

Each Image belongs to one and only one Session.

```text
Session 1 --- N Images
```

### Alternatives Considered

```text
Image N --- N Session
```

A single Image could belong to multiple Sessions.

### Rationale

During research, it became apparent that apparent multi-session membership is often a symptom of context evolution rather than true multi-membership.

Example:

```text
Apartment Search
        ↓
Desk Setup
        ↓
Home Office
```

This is better represented as multiple Sessions connected through evolving context than as a single Image belonging to many Sessions.

The Session remains a record of the saving context that existed when the Image was captured.

### Consequences

Benefits:

- Simpler persistence model
- Simpler retrieval model
- Clear ownership

Trade-offs:

- Future Session relationships may require additional structures

---

## Decision 3: Session Boundaries Are Approximated

### Decision

Session boundaries are determined using an inactivity threshold.

### Rationale

The ideal Session boundary would represent a shift in user attention or intent.

However, attention and intent cannot be directly observed.

What the system can observe is saving behavior.

Therefore:

```text
True Context Boundary
            ↓
Not Observable

Saving Activity Boundary
            ↓
Observable
```

The persistence layer approximates contextual boundaries using observable inactivity.

### Consequences

Benefits:

- Fully automatic
- Easy to implement
- Easy to test

Trade-offs:

- Session boundaries remain heuristic
- Threshold tuning may require future experimentation

### Important Note

The threshold value itself is not part of this architectural decision.

Only the existence of a threshold-based strategy is defined here.

The exact value should be validated through implementation and testing.

---

## Decision 4: Facts Are Immutable, Interpretations Are Mutable

### Decision

Muninn distinguishes between facts and interpretations.

### Facts

Examples:

- Session creation
- Image save events
- Timestamps
- Image ownership

Facts should not change.

### Interpretations

Examples:

- Session titles
- Tags
- Notes
- Retrieval signals
- AI-generated summaries

Interpretations may evolve over time.

### Rationale

Human memory does not typically revise what happened.

It revises what events mean.

Muninn should preserve historical events while allowing future reinterpretation.

### Consequences

Benefits:

- Stable historical record
- Flexible enrichment
- Cleaner future architecture

---

# Persistence Principles

## Save First, Enrich Later

The persistence layer exists primarily to preserve saved content.

Enrichment is intentionally deferred.

The system should never require enrichment in order to save content.

## Minimal Viable Persistence

Only information required to support Session existence should be stored in v1.

Additional metadata should be introduced only when validated by research or implementation experience.

---

# Entity Model

## SessionEntity

```text
SessionEntity
├ id
└ createdAt
```

### Fields

#### id

Unique Session identifier.

#### createdAt

Timestamp representing Session creation.

---

## ImageEntity

```text
ImageEntity
├ id
├ uri
├ savedAt
└ sessionId
```

### Fields

#### id

Unique Image identifier.

#### uri

Reference to image content.

#### savedAt

Timestamp representing image capture within Muninn.

#### sessionId

Reference to the owning Session.

---

# Relationship Model

## Session → Image

```text
Session 1 --- N Images
```

Rules:

- A Session contains one or more Images
- An Image belongs to exactly one Session

---

# Session Lifecycle

## Session Creation

Sessions are created automatically during image saving.

Users never create Sessions directly.

---

## Session Assignment Flow

```text
Save Image

    ↓

Get Latest Session

    ↓

Check Boundary Rule

    ↓

Reuse Session
      or
Create Session

    ↓

Persist Image
```

---

## Active Session Model

Muninn does not maintain a dedicated activeSession concept.

For v1:

```text
Latest Session
      =
Active Session
```

This minimizes state management complexity while preserving the desired behavior.

---

# Repository Ownership

Session management belongs to the repository layer.

The UI layer should not contain Session creation logic or Session boundary logic.

Repository responsibilities:

- Retrieve latest Session
- Evaluate boundary rule
- Create Sessions when required
- Assign Images to Sessions
- Persist data

---

# Mutation Strategy

## Immutable Data

```text
Session.id
Session.createdAt

Image.id
Image.savedAt
```

These values represent historical facts.

They should never be modified after creation.

---

## Mutable Data

Examples:

```text
Session Membership Adjustments
Session Titles
Tags
Notes
Retrieval Signals
AI Summaries
```

These values represent interpretation and may evolve over time.

---

# Explicit Non-Goals

This version intentionally does not define persistence for:

- Retrieval Enrichment
- AI-generated interpretations
- User annotations
- Session titles
- Session relationships
- Session merging
- Session splitting
- Session hierarchy

### Rationale

Capture and enrichment strategies remain under investigation.

Defining persistence structures before capture mechanisms are understood would introduce premature complexity.

These concerns are deferred to future issues.

---

# Summary

This architecture intentionally prioritizes simplicity.

The goal is not to model every aspect of context, but to create the smallest persistence structure capable of testing the Session hypothesis.

Core architecture:

```text
SessionEntity
├ id
└ createdAt

ImageEntity
├ id
├ uri
├ savedAt
└ sessionId
```

Key decisions:

1. Sessions are system-generated.
2. Images belong to exactly one Session.
3. Session boundaries are approximated using inactivity.
4. Facts are immutable.
5. Interpretations are mutable.
6. Enrichment persistence is intentionally deferred.

This architecture establishes a stable foundation for validating Session-based memory capture before introducing enrichment, inference, or retrieval-specific metadata.