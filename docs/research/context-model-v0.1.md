# Muninn Context Model v0.1

Status: Research Draft

Derived from Issue #7.

---

# Principle

Muninn is not primarily an image-management system.

Muninn is a context-reconstruction system.

The primary object is not the image.

The primary object is the user's remembered context.

Users often fail to remember exact files, locations, or metadata.

Instead, they remember fragments of context surrounding the moment an image became meaningful.

Muninn aims to preserve and reconstruct that context.

---

# Context Dimensions

## Retrieval Anchor

Information directly remembered by the user and used as the initial retrieval cue.

Examples:

* creator
* character
* source
* phrase
* visual feature

---

## Acquisition Context

How the image was encountered.

Examples:

* website
* social platform
* search query
* recommendation
* browsing path

---

## Save Intent

Why the image was saved.

Examples:

* inspiration
* reference
* preservation
* sharing
* later review

---

## Future Intention

Expected future use.

Examples:

* revisit
* compare
* share
* create
* archive

---

## Emotional State

Emotional context during saving.

Examples:

* comfort
* curiosity
* excitement
* nostalgia

---

## Mood Dependency

Situations in which retrieval becomes more likely.

Examples:

* feeling tired
* seeking inspiration
* seeking comfort
* reminiscing

---

## Temporal Context

Time-related information remembered by the user.

Examples:

* yesterday
* years ago
* late at night
* during an event
* around the same period

Temporal context is often remembered approximately rather than precisely.

Users rarely recall exact timestamps but frequently recall relative periods and situations.

---

## Relationship

Connections between images.

Examples:

* original
* derivative
* sequel
* alternative version
* same collection

---

## Memory Cue

Partial fragments remembered later.

Examples:

* phrase
* color
* composition
* atmosphere

Memory cues are often incomplete and ambiguous, yet highly effective as retrieval triggers.

---

## Collection History

The broader collection activity surrounding the image.

Examples:

* collecting period
* topic wave
* event
* fandom phase

Collection history provides context that extends beyond individual images.

---

# Observation

Analysis of retrieval queries revealed that many retrieval attempts are not directed toward a single image.

Users frequently search for images through memories of a broader activity or collecting period.

Examples:

* the image saved around the same time
* the image found while searching for something else
* the image from that collecting phase
* the image saved during a particular event

This suggests that context often exists above the image level.

---

# Session Layer

To represent context that spans multiple images, Muninn introduces the concept of a Collection Session.

A Collection Session represents:

* a time period
* a browsing activity
* a collecting intention
* a contextual state

Examples:

* an evening spent browsing artwork
* a period of researching a topic
* a fandom phase
* a temporary collecting obsession

Images may belong to zero or more sessions.

Sessions become first-class retrieval objects rather than simple metadata.

This allows retrieval based on remembered activities rather than individual files.

---

# Design Implications

Search should support:

* creator memory
* emotional memory
* temporal memory
* intention memory
* session memory
* relationship memory

Search should not assume users remember:

* filenames
* folders
* exact timestamps
* manually assigned tags

Muninn should prioritize reconstructing remembered context over navigating stored files.

---

# Core Insight

People rarely remember:

* file names
* storage locations
* exact timestamps

People frequently remember:

* what they were doing
* why they saved something
* how they felt
* what happened around it
* what they intended to do with it

Image retrieval is therefore not primarily a metadata problem.

It is a context reconstruction problem.

Muninn should store those memories.
