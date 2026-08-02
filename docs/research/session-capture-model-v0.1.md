# Session Capture Matrix v0.2

## Issue #10: Research Low-Friction Session Signal Capture

### Goal

Determine how Muninn can capture session-level retrieval signals while introducing minimal cognitive overhead.

The purpose of this research is to translate the Retrieval Signal Model from Issue #9 into a practical capture strategy.

---

## 1. Why This Research Exists

Issue #8 showed that users often remember collection sessions rather than isolated images.

Issue #9 showed that those sessions are recalled through signals such as:

- time
- activity
- emotion
- source
- social context
- lifecycle boundaries

The next question is practical:

> How can Muninn capture those signals without interrupting the saving flow?

---

## 2. Core Principle

Muninn should not require users to explain everything at save time.

The capture strategy should follow this order:

1. Capture what is already available.
2. Infer what can be inferred with reasonable confidence.
3. Ask only for information that cannot otherwise be recovered.
4. Allow correction later.

---

## 3. Capture Categories

Each signal belongs to one of three categories.

### Auto

Can be captured directly from metadata or application state.

### Infer

Can be estimated from behavior, context, or surrounding signals.

### Ask

Requires explicit user confirmation or input.

---

## 4. Retrieval Value × Capture Cost Matrix

The matrix evaluates each signal using two dimensions:

- **Retrieval Value** — how useful the signal is for future recall
- **Capture Cost** — how expensive the signal is to obtain

### 4.1 High Retrieval Value, Low Capture Cost

These signals should always be captured.

| Signal Class | Examples | Capture Mode | Rationale |
|--------------|----------|--------------|-----------|
| Temporal | 3 years ago, sometime in the past, until about 2 years ago | Auto | Automatically available through timestamps and session timing. |
| Source | pixiv, X, Twitter, YouTube, Bandcamp | Auto | Usually available from application context. |
| Item Form | illustration, screenshot, photo, scanned print | Auto | Can typically be identified from file characteristics. |

### 4.2 High Retrieval Value, Medium Capture Cost

These signals should primarily be inferred.

| Signal Class | Examples | Capture Mode | Rationale |
|--------------|----------|--------------|-----------|
| Activity | game progression, outfit browsing, feed scrolling, troubleshooting | Infer | Often recoverable from surrounding behavior. |
| Topic | a game, a band, a concept, a trip, a product | Infer | Can often be inferred from content and neighboring saves. |
| Event / Milestone | school trip, first restaurant visit, new purchase, commute, event day | Infer | Often emerges from temporal and contextual patterns. |
| Procedure Support | login steps, registration, setup, troubleshooting | Infer / Ask | Sometimes recoverable from task context. |
| Proof / Record | achievement screenshot, transaction, chat log, completion proof | Infer | Often identifiable from save patterns. |

### 4.3 High Retrieval Value, High Capture Cost

These signals should be requested only when necessary.

| Signal Class | Examples | Capture Mode | Rationale |
|--------------|----------|--------------|-----------|
| Affective State | healing, sadness, excitement, comfort, nostalgia | Ask | Highly valuable for retrieval but difficult to infer reliably. |
| Social Context | friend, partner, creator, audience, DM conversation | Ask / Infer | Some cases are inferable, many require confirmation. |
| Self-Presentation (今これ) | "my new gear", "this is me now", posting for others | Ask / Infer | Strong intent signal, but user meaning matters. |
| Aspiration (はやくこれになりたい) / Future Self | "I want to become this" | Ask | Powerful retrieval cue but difficult to infer safely. |
| Lifecycle Boundary | before device replacement, after graduation, during job search | Ask / Infer | Often known only to the user. |
| Preservation / Protection | disappearing post, temporary content, something worth keeping safe | Ask / Infer | Intent matters more than content. |
| Search Intent | looking for something fun to share, searching for advice, browsing for inspiration | Infer / Ask | Valuable but often difficult to infer with confidence. |

---

## 5. Recommended Capture Policy

### 5.1 Capture Automatically

Capture automatically whenever possible.

Examples:

- timestamp
- source
- file type
- save history
- source continuity

### 5.2 Infer Silently

Infer only when confidence is reasonably high.

Examples:

- activity patterns
- topic clusters
- event-like session structures
- proof / record behavior

### 5.3 Ask Only When Necessary

Ask only for information that is both:

- highly valuable for retrieval
- difficult to infer reliably

Examples:

- affective state
- ambiguous social context
- self-presentation intent
- aspiration / future-self cues
- lifecycle boundaries

---

## 6. UX Implications

The saving flow should remain lightweight.

Preferred principle:

> Save first. Enrich later.

A well-designed system should:

- capture automatically
- infer silently
- ask sparingly
- allow correction

This avoids turning saving into a form-filling task.

### Preferred Timing

#### At Save Time

Capture what is already known.

#### After a Batch of Saves

Infer session structure and optionally ask one compact question.

#### At Search Time

Use captured and inferred signals to improve retrieval quality.

#### During Later Editing

Allow users to review and correct inferred information.

---

## 7. Session Capture Flow

```text
Save
↓
Capture automatic signals
↓
Infer contextual signals
↓
Ask only for missing high-value signals
↓
Store session
↓
Allow later correction
```

---

## 8. Design Conclusions

- Temporal, source, and item-form signals should be captured automatically.
- Activity, topic, event, and proof-related signals can often be inferred.
- Affective, social, aspiration, and self-presentation signals usually require confirmation.
- Session capture should be incremental rather than form-based.
- All inferred signals should remain editable.

---

## 9. Expected Outcome of Issue #10

Issue #10 is complete when the research produces:

- a capture matrix
- a distinction between Auto / Infer / Ask
- a low-friction capture strategy
- a prompting timing model
- a correction model for inferred signals

---

## 10. Next Step

Use this matrix as the foundation for future UX design, session modeling, and retrieval architecture.