# Retrieval Signal Model v0.1

## Goal

Identify which session-level signals enable successful recall and retrieval of past collection sessions.

The purpose of this model is to determine what users actually remember about a collection session, which signals remain stable over time, and which signals are worth capturing in Muninn.

## Core signal classes

### 1. Temporal
When the session happened.

Examples:
- three years ago
- during a certain period
- until about two years ago
- that time / that period

### 2. Activity
What the user was doing.

Examples:
- deeply into a game
- looking for strategy information
- scrolling Twitter
- looking at a band’s social media
- using it as a wallpaper

### 3. Affective
How the user felt.

Examples:
- painful
- could not sleep
- hit me hard
- liked it

### 4. Source
Where the item came from.

Examples:
- pixiv
- X
- Twitter
- YouTube stream
- band social media

### 5. Social
Who was involved.

Examples:
- went out with a friend
- drawn for me on a stream

### 6. Lifecycle
A personal or device-related boundary.

Examples:
- before I changed my smartphone
- used as my wallpaper until about two years ago

### 7. Item form
What kind of item it was.

Examples:
- illustration
- photo
- screenshot
- neppuri print photo

## Signal behavior

### Stable signals
These appear to be relatively stable over time:
- temporal anchors
- source / platform
- activity context
- lifecycle boundaries

### Fading signals
These may become weaker over time unless reinforced:
- exact item content
- minor visual details
- precise order of browsing events

### High-value signals
The corpus suggests that the highest-value retrieval signals are:
- temporal episode
- activity context
- affective state
- source
- social context

## Retrieval expression pattern

The recall phrase often looks like this:

```text
[when] + [what I was doing] + [where it came from] + [what kind of item] + [why it mattered]
```

This is enough to reconstruct the session even when the image itself is only vaguely remembered.

## Model implication

Muninn should not store only image metadata.
It should also store session-level retrieval signals, especially:

- temporal anchors
- activity context
- affective state
- source / platform
- social context
- lifecycle boundaries

## Conclusion

Issue #9 should conclude that retrieval is often session-based and that session-level signals are central to later recall.

The next design step is to decide which of these signals should be captured explicitly, inferred automatically, or left optional.
