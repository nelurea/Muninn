const PIXIV_IMAGE_RULE_ID = 1;


/*
 * Add the Pixiv Referer to requests made to the image CDN.
 */
async function ensurePixivImageRequestRule() {
  await chrome.declarativeNetRequest.updateSessionRules({
    removeRuleIds: [
      PIXIV_IMAGE_RULE_ID
    ],

    addRules: [
      {
        id: PIXIV_IMAGE_RULE_ID,
        priority: 1,

        action: {
          type: "modifyHeaders",

          requestHeaders: [
            {
              header: "Referer",
              operation: "set",
              value: "https://www.pixiv.net/"
            }
          ]
        },

        condition: {
          regexFilter:
            "^https://i\\.pximg\\.net/",

          resourceTypes: [
            "xmlhttprequest"
          ]
        }
      }
    ]
  });

  console.log(
    "[Muninn] Pixiv image request rule installed."
  );
}


ensurePixivImageRequestRule()
  .catch((error) => {
    console.error(
      "[Muninn] Failed to install Pixiv image request rule:",
      error
    );
  });


chrome.runtime.onInstalled.addListener(
  () => {
    ensurePixivImageRequestRule()
      .catch((error) => {
        console.error(
          "[Muninn] Failed to install Pixiv image request rule:",
          error
        );
      });
  }
);


chrome.downloads.onChanged.addListener(
  (delta) => {
    if (delta.state) {
      console.log(
        `[Muninn] Download ${delta.id} state:`,
        delta.state.current
      );
    }

    if (delta.error) {
      console.error(
        `[Muninn] Download ${delta.id} failed:`,
        delta.error.current
      );
    }
  }
);


chrome.action.onClicked.addListener(
  async (tab) => {
    if (!tab.id) {
      console.error(
        "[Muninn] Active tab does not have an ID."
      );

      return;
    }

    try {
      const response =
        await chrome.tabs.sendMessage(
          tab.id,
          {
            type: "MUNINN_CAPTURE"
          }
        );

      if (
        !response?.ok ||
        !response.capturePackage
      ) {
        console.error(
          "[Muninn] CapturePackage was not produced:",
          response
        );

        return;
      }

      const capturePackage =
        response.capturePackage;

      console.log(
        "[Muninn] CapturePackage received:",
        capturePackage
      );

      await ensurePixivImageRequestRule();

      const exportResult =
        await exportCapturePackage(
          capturePackage
        );

      console.log(
        "[Muninn] Capture export complete:",
        exportResult
      );
    } catch (error) {
      console.error(
        "[Muninn] Capture/export failed:",
        error
      );
    }
  }
);


async function exportCapturePackage(
  capturePackage
) {
  validateCapturePackage(
    capturePackage
  );

  const directoryName =
    buildCaptureDirectoryName(
      capturePackage
    );

  /*
   * Download every image first.
   *
   * This ensures we do not write a manifest claiming
   * the package is complete when image acquisition failed.
   */
  const mediaDownloads = [];

  for (
    const media
    of capturePackage.media
  ) {
    const result =
      await acquireAndDownloadMedia(
        directoryName,
        media
      );

    mediaDownloads.push(
      result
    );
  }

  /*
   * Write manifest only after all image acquisition
   * operations have successfully started.
   */
  const manifestDownloadId =
    await downloadManifest(
      directoryName,
      capturePackage
    );

  return {
    directoryName,
    manifestDownloadId,
    mediaDownloads
  };
}


async function acquireAndDownloadMedia(
  directoryName,
  media
) {
  if (!media.sourceUrl) {
    throw new Error(
      `media[${media.index}] does not contain sourceUrl.`
    );
  }

  if (!media.fileName) {
    throw new Error(
      `media[${media.index}] does not contain fileName.`
    );
  }

  console.log(
    `[Muninn] Fetching media ${media.index}:`,
    media.sourceUrl
  );

  const response =
    await fetch(
      media.sourceUrl,
      {
        method: "GET",
        credentials: "omit",
        cache: "no-store"
      }
    );

  console.log(
    `[Muninn] Media ${media.index} response:`,
    response.status,
    response.statusText,
    response.headers.get("content-type")
  );

  if (!response.ok) {
    throw new Error(
      `Failed to acquire media ${media.index}: HTTP ${response.status}`
    );
  }

  const contentType =
    response.headers.get(
      "content-type"
    );

  if (
    !contentType ||
    !contentType.startsWith(
      "image/"
    )
  ) {
    throw new Error(
      `Media ${media.index} returned unexpected content type: ${contentType}`
    );
  }

  const buffer =
    await response.arrayBuffer();

  console.log(
    `[Muninn] Media ${media.index} acquired: ${buffer.byteLength} bytes`
  );

  if (
    buffer.byteLength === 0
  ) {
    throw new Error(
      `Media ${media.index} returned an empty response.`
    );
  }

  const dataUrl =
    arrayBufferToDataUrl(
      buffer,
      contentType
    );

  const downloadId =
    await chrome.downloads.download({
      url: dataUrl,

      filename:
        `${directoryName}/${media.fileName}`,

      conflictAction:
        "overwrite",

      saveAs:
        false
    });

  console.log(
    `[Muninn] Media ${media.index} download started with ID ${downloadId}.`
  );

  return {
    index:
      media.index,

    downloadId,

    fileName:
      media.fileName,

    byteLength:
      buffer.byteLength,

    contentType
  };
}


async function downloadManifest(
  directoryName,
  capturePackage
) {
  const manifestJson =
    JSON.stringify(
      capturePackage,
      null,
      2
    );

  const manifestUrl =
    "data:application/json;charset=utf-8," +
    encodeURIComponent(
      manifestJson
    );

  const downloadId =
    await chrome.downloads.download({
      url:
        manifestUrl,

      filename:
        `${directoryName}/manifest.json`,

      conflictAction:
        "overwrite",

      saveAs:
        false
    });

  console.log(
    "[Muninn] Manifest download started:",
    downloadId
  );

  return downloadId;
}


function arrayBufferToDataUrl(
  buffer,
  mimeType
) {
  const bytes =
    new Uint8Array(
      buffer
    );

  const chunkSize =
    0x8000;

  let binary = "";

  for (
    let offset = 0;
    offset < bytes.length;
    offset += chunkSize
  ) {
    const chunk =
      bytes.subarray(
        offset,
        Math.min(
          offset + chunkSize,
          bytes.length
        )
      );

    binary +=
      String.fromCharCode(
        ...chunk
      );
  }

  return (
    `data:${mimeType};base64,` +
    btoa(binary)
  );
}


function validateCapturePackage(
  capturePackage
) {
  if (
    capturePackage.schemaVersion !== 1
  ) {
    throw new Error(
      "Unsupported CapturePackage schema version."
    );
  }

  if (
    !capturePackage.source?.id
  ) {
    throw new Error(
      "CapturePackage does not contain source.id."
    );
  }

  if (
    !capturePackage.capturedAt
  ) {
    throw new Error(
      "CapturePackage does not contain capturedAt."
    );
  }

  if (
    !Array.isArray(
      capturePackage.media
    ) ||
    capturePackage.media.length === 0
  ) {
    throw new Error(
      "CapturePackage does not contain media."
    );
  }
}


function buildCaptureDirectoryName(
  capturePackage
) {
  const sourceId =
    sanitizePathSegment(
      capturePackage.source.id
    );

  const capturedAt =
    sanitizeTimestamp(
      capturePackage.capturedAt
    );

  return (
    "Muninn/" +
    `capture-${sourceId}-${capturedAt}`
  );
}


function sanitizeTimestamp(
  timestamp
) {
  return timestamp
    .replaceAll(
      ":",
      "-"
    )
    .replaceAll(
      ".",
      "-"
    );
}


function sanitizePathSegment(
  value
) {
  return String(
    value
  ).replace(
    /[<>:"/\\|?*\x00-\x1F]/g,
    "_"
  );
}