# Session Model v0.1

## Issue #8: Research Collection Session Types and Boundaries

### Goal

Determine whether image retrieval in Muninn is organized around individual images or around broader collection sessions.

This document summarizes the current research result.

## 1. Working definition

A **Collection Session** is a coherent stretch of collecting behavior in which a user saves, inspects, compares, follows up, or remembers items under a shared intention, mood, topic, or browsing flow.

A session is not defined only by the content of a single image. It may also be defined by the surrounding context of collection behavior.

## 2. Main finding

Image retrieval is not explained well by image content alone.

The collected examples show that users often remember and retrieve items through a broader session context, such as:

* what they were browsing
* why they were collecting
* what mood they were in
* what they intended to do later
* what flow led them to the item
* what happened after the save

This means that **session-level context is a real retrieval object candidate**, not just incidental metadata.

## 3. Preliminary session taxonomy

The observed examples support the following session types.

### 3.1 Exploratory browsing

The user browses feeds, rankings, recommendations, or follow lists. Saving is secondary to browsing.

Typical examples:

* checking a follow list
* browsing recommendations
* looking at rankings

### 3.2 Immediate save

The user encounters an item and saves it immediately because it feels strong, attractive, useful, or worth keeping.

Typical examples:

* saving a striking illustration
* saving an appealing outfit
* saving a promising post

### 3.3 Goal-oriented collection

The user is trying to solve a problem, learn something, complete a task, or keep a procedural reference.

Typical examples:

* researching a concept
* saving setup steps
* collecting advice while stuck

### 3.4 Follow-up tracking

The user starts from one item and continues into related material, updates, derivatives, or surrounding context.

Typical examples:

* tracking a new release
* following an event
* looking for related works or fan art

### 3.5 Comparison and selection

The user compares near-duplicate or alternative items and keeps only the most relevant ones.

Typical examples:

* selecting among visually similar images
* comparing outfit options
* choosing among multiple candidates

### 3.6 Incidental observation

The user saves something encountered outside the original browsing goal, often through walking, being outside, or noticing something unexpected.

Typical examples:

* street scenes
* graffiti
* flowers
* unexpected visual discoveries

### 3.7 Recall and affect regulation

The user saves or revisits items to recover a feeling, soothe themselves, remember a situation, or preserve an emotional state.

Typical examples:

* healing or comfort-oriented saving
* nostalgia-driven saving
* revisiting items that felt emotionally supportive

### 3.8 Social sharing and narrative

The user saves items to show someone, talk about them, or preserve them as part of an interaction or narrative.

Typical examples:

* chat screenshots
* posts worth sharing
* conversation records
* experience albums

### 3.9 Preservation and protection

The user saves items because they might disappear, become hard to find, or be needed later.

Typical examples:

* disappearing posts
* registration or password references
* long-term retention of useful material

### 3.10 Proof and record

The user saves items as evidence of an action, result, or event.

Typical examples:

* game results
* good play screenshots
* transaction or process confirmation
* records of what happened

## 4. Session-level modifiers

These are not separate main types. They are properties that can attach to any session type.

* **Emotional regulation**: healing, comfort, relief, stress reduction
* **Future use**: saved for later viewing or reuse
* **Record keeping**: saved as evidence or personal log
* **Protection**: saved before loss or deletion
* **Relationship memory**: saved because of another person or interaction
* **Procedure support**: saved to avoid getting stuck later
* **Learning**: saved to understand a concept
* **Deepening**: saved as a starting point for further exploration

## 5. Image-level context vs session-level context

### Image-level context

This belongs to the individual image.

* what is shown in the image
* style, character, composition, scene, design
* the immediate reaction to that specific image

### Session-level context

This belongs to the collecting episode as a whole.

* why the user was browsing
* what flow the user came from
* what topic or mood held the collection together
* what the user intended to do later
* how the user later remembers the session

## 6. Explicit, implicit, and hybrid sessions

### Explicit

The user consciously starts a collection session.

Example: searching for outfits for a trip.

### Implicit

The collection session emerges from browsing without a clearly stated plan.

Example: feed browsing that gradually turns into saving.

### Hybrid

The user starts with a goal, but accidental discovery and side-tracking become part of the same session.

Example: browsing one item, then following related items into a deeper collection flow.

## 7. Boundary hypotheses

A session may end when one or more of the following changes:

* topic changes
* purpose changes
* source changes
* attention pattern changes
* the user explicitly stops
* the user subjectively feels the flow is over

Boundary confidence should be treated as a field in the model, not as a binary fact.

* **high**: clear start and end
* **medium**: likely one session, but some ambiguity remains
* **low**: boundary is unstable or retrospective

## 8. Automatic inference hypothesis

Sessions may be inferred from behavior signals such as:

* source continuity
* time gaps
* repeated topic terms
* repeated save patterns
* source transitions
* emotional or procedural cues in saved metadata

This suggests that session detection may be partially automatic, but the model should still allow user-confirmed sessions.

## 9. Preliminary data model

```text
CollectionSession
├─ session_id
├─ start_trigger
├─ source
├─ main_type
├─ modifiers[]
├─ saved_items[]
├─ retrieval_cues[]
├─ end_condition
├─ boundary_confidence
└─ inferred_or_user_confirmed
```

## 10. Summary of findings

* Users do not only save images because they like them.
* Users also save images to learn, remember, prove, protect, share, compare, and continue exploring.
* The same image-saving action can belong to different sessions depending on the collecting flow.
* Session-level context is therefore required for later retrieval.

## 11. Result for Issue #8

Issue #8 is satisfied if the research produces:

* a preliminary session taxonomy
* a distinction between image-level and session-level context
* boundary hypotheses
* an explicit / implicit / hybrid distinction
* an initial model for automatic session inference

## 12. Next step

Use this model as the research artifact for Issue #8 and carry the resulting terms into the next UX and data-model design step.
