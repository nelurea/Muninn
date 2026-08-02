# Session Data Model v0.1

## Goal

Define the minimal Session data model for Muninn v1.

This research determines the smallest Session structure that can support storage, display, and later retrieval without adding unnecessary capture cost.

## Core Question

What is the smallest Session model that still supports meaningful context reconstruction?

---

## Decision

For v1, Session should be modeled as:

1. **Session Core**
2. **Retrieval Enrichment**
3. **Capture Metadata**

This keeps the save-time model minimal while preserving a path toward richer context later.

---

## Session Core

The minimal Session Core is:

```text id="r9x4cp"
Session
├ id
├ createdAt
└ imageIds
```

### `id`

Unique identifier for the Session.

### `createdAt`

Timestamp representing Session creation.

### `imageIds`

References to Images belonging to the Session.

### Why this core

* `id` is required for identity.
* `createdAt` is required for time-based recall.
* `imageIds` is required because Session without Images is not meaningful.

### Why not larger

A larger core would increase save-time friction.
Retrieval context should not be required at creation time.

---

## Retrieval Enrichment

Retrieval enrichment is separate from the core.

Current signal categories:

* Temporal
* Activity
* Affective
* Source
* Social
* Lifecycle

These support later recall, but do not need to exist at save time.

---

## Capture Metadata

Some information can be collected with low friction.

Examples:

* source metadata
* item form metadata
* inferred context
* user-confirmed context

This layer may be populated through:

* Auto
* Infer
* Ask

---

## Session and Image Relationship

For v1:

```text id="3yq1nx"
Session
└── one or more Images
```

An Image belongs to a Session.

---

## Mutability

* Session Core should remain stable
* Retrieval enrichment may be added later
* Capture metadata may be updated later

The Session object is mutable in practice, but its core identity should not change.

---

## Save-Time vs Enrichment-Time

### Save-time

* id
* createdAt
* imageIds

### Enrichment-time

* activity context
* emotional context
* social context
* lifecycle context
* inferred topic or event information

---

## Usage

### Storage

Session is the primary grouping unit for saved Images.

### Display

Users should browse Sessions as meaningful groups of content.

### Retrieval

Later retrieval should use Session-level context in addition to image-level content.

### Inference

The model should allow inferred context to be attached without changing the Session Core.

---

## Conclusion

The recommended v1 shape is a small Session Core plus expandable context.

This preserves low-friction saving while keeping future retrieval meaningful.

## Summary

```text id="9m2dqw"
Session Core
├ id
├ createdAt
└ imageIds

Retrieval Enrichment
├ Temporal
├ Activity
├ Affective
├ Source
├ Social
└ Lifecycle

Capture Metadata
├ source metadata
├ item form metadata
├ inferred context
└ user-confirmed context
```
