# Android-Native Pixiv Capture

## Overview

Muninn supports a native Android rich-capture path for Pixiv Web.

The user browses Pixiv inside Muninn's in-app WebView, opens an artwork, and presses `Capture`.

The capture then proceeds entirely on the Android device:

`Pixiv WebView`
→ `Pixiv extraction`
→ `Android bridge`
→ `PixivCapturePayload`
→ `PixivMediaDownloader`
→ `PixivCaptureMapper`
→ `CaptureDraft`
→ `SaveCaptureUseCase`
→ `CapturedWork / Media / Tags`
→ `Collection Session`

This removes the browser-extension and manual PC-to-Android handoff from the primary Pixiv capture path.

`CapturePackage` remains supported as an import/export and transport representation, but Android-native capture does not require an intermediate package archive.

## Responsibilities

### WebCaptureScreen

`WebCaptureScreen` owns the embedded browsing experience and coordinates the capture flow.

It:

- hosts the Pixiv WebView
- supports Pixiv login and browsing
- tracks SPA navigation through `WebViewClient`
- enables Capture only on Pixiv artwork pages
- prevents concurrent Capture actions
- displays basic capture progress and result status
- invokes the Pixiv page capture hook
- receives bridge messages from the Pixiv origin
- coordinates parsing, media acquisition, mapping, and persistence

The screen does not contain Pixiv metadata parsing or media-download implementation details.

## Pixiv capture hook

The Pixiv-specific JavaScript is stored at:

`app/src/main/assets/webcapture/pixiv/capture-hook.js`

It is injected at document start into the Pixiv page execution world.

The hook:

- observes Pixiv `fetch()` and `XMLHttpRequest` responses
- retains relevant artwork detail and page data
- identifies the current artwork from the page URL
- requests missing detail data at capture time
- requests `/pages` data when required for multi-image artworks
- builds the canonical capture metadata
- sends the result to Android through the Web message bridge

Relevant Pixiv endpoints include:

- `/ajax/illust/{artworkId}`
- `/ajax/illust/{artworkId}/pages`

Multi-image URLs are taken from Pixiv's page response rather than inferred by modifying `_p0`, `_p1`, and similar filename segments.

## Android bridge

Communication from the Pixiv page to Android uses AndroidX WebKit Web messaging.

The bridge is restricted to:

`https://www.pixiv.net`

Messages are accepted only from the main frame and expected Pixiv origin.

The JavaScript extraction layer does not directly access Muninn persistence.

It only produces capture metadata for Android to validate and process.

## PixivCapturePayload

`PixivCaptureParser` converts the bridge JSON into a typed `PixivCapturePayload`.

The payload represents metadata before local media files exist.

It includes:

- source type
- source ID
- canonical URL
- capture timestamp
- author ID
- author name
- title
- caption
- tags
- ordered media metadata

Media metadata includes:

- media index
- original source URL
- MIME type when available
- target filename

Potentially missing Pixiv metadata remains nullable at this boundary.

Literal values such as `"N/A"` are preserved and are not treated as missing values.

## Media acquisition

`PixivMediaDownloader` downloads original media to a temporary application cache directory.

Requests use the active WebView context required for Pixiv media retrieval, including the Pixiv referer, current WebView user agent, and applicable cookies.

Each requested file must:

- receive a successful HTTP response
- be written to disk successfully
- contain non-zero data

If any media download fails, the temporary capture directory is removed and the capture does not proceed to persistence.

Media ordering follows the metadata order provided by Pixiv.

## CaptureDraft mapping

`PixivCaptureMapper` converts:

`PixivCapturePayload + downloaded files`

into the source-independent `CaptureDraft` model.

This is the boundary between Pixiv-specific capture behavior and Muninn's common persistence path.

Nullable source metadata is normalized here when required by the existing canonical capture model.

For example:

- missing author ID → empty string
- missing author name → empty string
- missing caption → empty string
- missing MIME type → `application/octet-stream`

The Pixiv-specific parser therefore preserves source information as faithfully as possible, while normalization occurs only at the common-model boundary.

## Persistence

`SaveCaptureUseCase` is shared by Android-native capture and package-based import.

It is responsible for:

- copying temporary media into persistent application storage
- resolving the active Collection Session
- creating `CapturedWorkEntity`
- creating ordered `CapturedMediaEntity` rows
- creating `CapturedTagEntity` rows
- persisting the capture
- touching the active Session

The WebView capture path does not implement a separate persistence model.

## Temporary-file lifecycle

Downloaded Pixiv media first exists under the application cache directory.

The lifecycle is:

`download`
→ `temporary files`
→ `CaptureDraft`
→ `SaveCaptureUseCase`
→ `persistent captured_media storage`
→ `temporary directory cleanup`

The temporary download directory is removed after the persistence attempt through a `finally` cleanup path.

If the downloader itself fails, it also removes the temporary directory before returning failure.

## Session integration

Android-native Pixiv captures use the same Collection Session behavior established for rich capture imports.

A captured Pixiv work is associated with the active Session through `SaveCaptureUseCase`.

Multi-image works remain one captured work containing multiple ordered media items.

No Pixiv-specific Session implementation exists.

## Validation

The Android-native path has been validated on a real Android device.

Confirmed behavior includes:

- Pixiv login inside Muninn WebView
- normal Pixiv artwork browsing
- SPA URL tracking without manual reload
- one-action capture from an artwork page
- single-image metadata extraction
- multi-image metadata extraction
- explicit detail fallback retrieval
- explicit `/pages` retrieval
- typed Android payload parsing
- original Pixiv media downloading
- single-image persistence
- multi-image persistence
- Collection Session association
- correct media ordering
- Session UI display

A 13-image Pixiv artwork was successfully captured, downloaded, persisted, and displayed as 13 ordered media items in the same Collection Session.

Single-image JPEG and PNG captures were also successfully validated.

## Security boundaries

The WebView capture path intentionally limits native exposure.

Current controls include:

- bridge access restricted to the Pixiv origin
- bridge messages accepted only from the main frame
- JavaScript injection restricted to Pixiv
- file access disabled in WebView
- content access disabled in WebView
- mixed content disabled
- page JavaScript does not receive direct database or filesystem APIs

The JavaScript layer can submit capture metadata, but persistence remains entirely under Android control.

## Relationship to CapturePackage

`CapturePackage` remains part of Muninn's architecture.

It is still useful for:

- external capture transport
- import/export
- interoperability
- archived capture packages
- non-native capture sources

However, Android-native sources should not be forced to serialize into a ZIP/package representation before persistence.

The common internal path is:

`source adapter`
→ `CaptureDraft`
→ `SaveCaptureUseCase`

For Pixiv Web:

`Pixiv WebView`
→ `PixivCapturePayload`
→ `CaptureDraft`
→ `SaveCaptureUseCase`

For package import:

`CapturePackage`
→ `CapturePackageMapper`
→ `CaptureDraft`
→ `SaveCaptureUseCase`

This keeps source acquisition independent from persistence while allowing multiple capture mechanisms to share the same canonical save path.