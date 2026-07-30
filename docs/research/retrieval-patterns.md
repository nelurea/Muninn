# Retrieval Patterns

Derived from Issue #7.

The goal is to identify recurring retrieval behaviors rather than image categories.

---

# Pattern 1: Creator Retrieval

Example:

> The image from an artist I follow.

Required Context:

- creator
- creator_aliases
- source
- related_images

Frequency:

High

---

# Pattern 2: Temporal Retrieval

Example:

> The image I saved yesterday.

Required Context:

- save_timestamp
- save_session
- chronological_neighbors

Frequency:

Very High

---

# Pattern 3: Emotional Retrieval

Example:

> The image that comforted me.

Required Context:

- emotional_state
- save_reason
- revisit_history

Frequency:

High

---

# Pattern 4: Intent Retrieval

Example:

> The image I planned to share later.

Required Context:

- save_intent
- future_intention

Frequency:

High

---

# Pattern 5: Acquisition Retrieval

Example:

> The image I found while searching for something else.

Required Context:

- acquisition_context
- source
- search_path

Frequency:

Medium

---

# Pattern 6: Memory Fragment Retrieval

Example:

> The image with that phrase I vaguely remember.

Required Context:

- OCR
- captions
- notes
- remembered_fragments

Frequency:

High

---

# Pattern 7: Relationship Retrieval

Example:

> The original image behind this derivative work.

Required Context:

- parent_image
- child_image
- related_images

Frequency:

Medium

---

# Pattern 8: Collection Session Retrieval

Example:

> The image saved around the same time.

Required Context:

- session_id
- session_topic
- session_timespan

Frequency:

Very High

---

# Pattern 9: Mood Retrieval

Example:

> Show me images that fit my current mood.

Required Context:

- mood_association
- revisit_patterns

Frequency:

Medium

---

# Key Finding

Most retrieval attempts are not image-centric.

They are context-centric.

Users frequently remember:

- why they saved an image
- when they saved it
- what they were doing
- how they felt

more accurately than the image contents themselves.

Therefore image retrieval should be modeled as context reconstruction rather than metadata lookup.