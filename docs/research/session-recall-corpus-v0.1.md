# Session Recall Corpus v0.1

## Purpose

This corpus collects examples of how users remember previously saved images or image-like items.

The goal is to identify which cues help users recall a collection session later.

## Corpus examples

### 1.
Three years ago, during a painful period, I found this illustration on pixiv: a chill girl drinking hot cocoa in the rain. It felt so good.

### 2.
A photo of the food before eating it, from when I went out with a friend and it was really delicious.

### 3.
A screenshot I took when I was deeply into that game, to ask for攻略 information and show my current situation.

### 4.
A photo of a neppuri printout of a favorite work I was into for a while.

### 5.
An image that hit me hard when I was looking at a band’s social media after becoming really into a band that was trending.

### 6.
That illustration from a meme that went viral on X.

### 7.
An illustration I found while scrolling Twitter late at night when I could not sleep.

### 8.
That illustration I had drawn for me on a YouTube stream where the creator offered “I will draw your icon!”

### 9.
That illustration I liked before I changed my smartphone.

### 10.
An illustration I was looking at during the period when I was playing that game.

### 11.
An illustration I used as my wallpaper until about two years ago.

## Observed recall pattern

The corpus shows a recurring structure:

```text
[temporal episode] + [activity / situation] + [source] + [item type] + [distinctive trait]
```

Examples:
- Three years ago, during a painful period + pixiv + illustration + rain / hot cocoa / chill
- When I was deeply into the game + strategy info + screenshot + current situation
- When I went out with a friend + food + photo before eating

## Strong retrieval signals

### Temporal
- three years ago
- during a certain period
- until about two years ago
- that time / that period

### Activity
- while deeply into a game
- while looking for strategy information
- while scrolling Twitter
- while looking at a band’s social media
- while using it as a wallpaper

### Affective
- painful
- could not sleep
- hit me hard
- I liked it

### Source
- pixiv
- X
- Twitter
- YouTube stream
- band social media
- neppuri

### Social
- when I went out with a friend
- drawn for me on a stream

### Lifecycle
- before I changed my smartphone
- used as my wallpaper until about two years ago

## Interim conclusion

Users do not usually remember a saved image through image content alone.
They remember it through a combined cue built from time, activity, emotion, source, and social context.

This supports the hypothesis that Muninn should capture session-level retrieval signals, not only image-level metadata.
