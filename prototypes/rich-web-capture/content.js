console.log(
  "[Muninn] content.js loaded:",
  location.href
);


const MESSAGE_SOURCE =
  "MUNINN_PIXIV_PAGE_HOOK";

const COMMAND_SOURCE =
  "MUNINN_CONTENT_SCRIPT";


const observedArtworkData =
  new Map();

const pendingPageRequests =
  new Map();


window.addEventListener(
  "message",
  (event) => {
    if (
      event.source !== window
    ) {
      return;
    }

    if (
      event.origin !==
      location.origin
    ) {
      return;
    }

    const message =
      event.data;


    /*
     * Handle explicit failure of a /pages request.
     */
    if (
      message?.source ===
        MESSAGE_SOURCE &&
      message?.type ===
        "PIXIV_PAGES_REQUEST_FAILED"
    ) {
      const pending =
        pendingPageRequests.get(
          message.artworkId
        );

      if (pending) {
        clearTimeout(
          pending.timeoutId
        );

        pendingPageRequests.delete(
          message.artworkId
        );

        pending.reject(
          new Error(
            message.error ??
            "Pixiv pages request failed."
          )
        );
      }

      return;
    }


    /*
     * Ignore unrelated window messages.
     */
    if (
      !message ||
      message.source !==
        MESSAGE_SOURCE ||
      message.type !==
        "PIXIV_RESPONSE"
    ) {
      return;
    }


    if (
      typeof message.artworkId !==
        "string" ||
      !/^\d+$/.test(
        message.artworkId
      )
    ) {
      return;
    }


    if (
      message.endpointType !==
        "detail" &&
      message.endpointType !==
        "pages"
    ) {
      return;
    }


    const artworkId =
      message.artworkId;


    const current =
      observedArtworkData.get(
        artworkId
      ) ?? {
        detail: null,
        pages: null
      };


    current[
      message.endpointType
    ] =
      message.payload;


    observedArtworkData.set(
      artworkId,
      current
    );


    console.log(
      `[Muninn] Received Pixiv ${message.endpointType} response for artwork ${artworkId}`
    );


    /*
     * Resolve an outstanding /pages request.
     */
    if (
      message.endpointType ===
      "pages"
    ) {
      const pending =
        pendingPageRequests.get(
          artworkId
        );

      if (pending) {
        clearTimeout(
          pending.timeoutId
        );

        pendingPageRequests.delete(
          artworkId
        );

        pending.resolve(
          message.payload
        );
      }
    }
  }
);


chrome.runtime.onMessage.addListener(
  (
    message,
    sender,
    sendResponse
  ) => {
    if (
      message.type !==
      "MUNINN_CAPTURE"
    ) {
      return;
    }


    captureCurrentArtwork()
      .then(
        (capturePackage) => {
          console.log(
            "[Muninn] CapturePackage:",
            capturePackage
          );

          console.log(
            "[Muninn] CapturePackage JSON:\n" +
            JSON.stringify(
              capturePackage,
              null,
              2
            )
          );

          sendResponse({
            ok: true,
            capturePackage
          });
        }
      )
      .catch(
        (error) => {
          console.error(
            "[Muninn] Capture failed:",
            error
          );

          sendResponse({
            ok: false,

            error:
              error instanceof Error
                ? error.message
                : String(error)
          });
        }
      );


    /*
     * Keep the Chrome message channel open
     * because multi-image capture may need
     * an asynchronous /pages request.
     */
    return true;
  }
);


async function captureCurrentArtwork() {
  const artworkId =
    extractArtworkId(
      location.href
    );


  if (!artworkId) {
    throw new Error(
      `This is not a Pixiv artwork page: ${location.href}`
    );
  }


  let observed =
    observedArtworkData.get(
      artworkId
    );


  if (!observed?.detail) {
    throw new Error(
      `No observed Pixiv detail response for artwork ${artworkId}.`
    );
  }


  const pageCount =
    Number(
      observed.detail.body
        ?.pageCount ??
      1
    );


  if (
    !Number.isInteger(
      pageCount
    ) ||
    pageCount < 1
  ) {
    throw new Error(
      `Invalid pageCount for artwork ${artworkId}.`
    );
  }


  /*
   * Pixiv does not necessarily request /pages
   * just by opening a multi-image artwork.
   *
   * If it has not already been observed,
   * request it at capture time.
   */
  if (
    pageCount > 1 &&
    !observed.pages
  ) {
    console.log(
      `[Muninn] Artwork ${artworkId} has ${pageCount} images; requesting pages data.`
    );

    await requestPixivPages(
      artworkId
    );


    observed =
      observedArtworkData.get(
        artworkId
      );


    if (!observed?.pages) {
      throw new Error(
        `Pixiv pages data was not retained for artwork ${artworkId}.`
      );
    }
  }


  return createCapturePackage(
    artworkId,
    observed
  );
}


function requestPixivPages(
  artworkId
) {
  return new Promise(
    (
      resolve,
      reject
    ) => {
      const existing =
        pendingPageRequests.get(
          artworkId
        );


      if (existing) {
        reject(
          new Error(
            `Pages request already pending for artwork ${artworkId}.`
          )
        );

        return;
      }


      const timeoutId =
        setTimeout(
          () => {
            pendingPageRequests.delete(
              artworkId
            );

            reject(
              new Error(
                `Timed out waiting for Pixiv pages data for artwork ${artworkId}.`
              )
            );
          },
          5000
        );


      pendingPageRequests.set(
        artworkId,
        {
          resolve,
          reject,
          timeoutId
        }
      );


      window.postMessage(
        {
          source:
            COMMAND_SOURCE,

          type:
            "REQUEST_PIXIV_PAGES",

          artworkId
        },
        location.origin
      );
    }
  );
}


function createCapturePackage(
  artworkId,
  observed
) {
  const detailResponse =
    observed.detail;


  if (
    detailResponse.error ||
    !detailResponse.body
  ) {
    throw new Error(
      `Pixiv detail response is invalid for artwork ${artworkId}.`
    );
  }


  const body =
    detailResponse.body;


  const pageCount =
    Number(
      body.pageCount ??
      1
    );


  if (
    !Number.isInteger(
      pageCount
    ) ||
    pageCount < 1
  ) {
    throw new Error(
      `Invalid pageCount for artwork ${artworkId}.`
    );
  }


  const media =
    buildMediaList({
      artworkId,
      pageCount,
      detailBody:
        body,
      pagesResponse:
        observed.pages
    });


  return {
    schemaVersion: 1,

    source: {
      type:
        "pixiv",

      id:
        String(
          body.illustId ??
          body.id ??
          artworkId
        ),

      canonicalUrl:
        `https://www.pixiv.net/artworks/${artworkId}`
    },

    capturedAt:
      new Date().toISOString(),

    content: {
      author: {
        id:
          body.userId != null
            ? String(
                body.userId
              )
            : null,

        name:
          body.userName ??
          null
      },

      title:
        body.illustTitle ??
        body.title ??
        null,

      caption:
        body.illustComment ??
        body.description ??
        null,

      tags:
        extractTags(
          body
        )
    },

    media
  };
}


function extractTags(
  body
) {
  const rawTags =
    body.tags?.tags;


  if (
    !Array.isArray(
      rawTags
    )
  ) {
    return [];
  }


  return rawTags
    .map(
      (tag) => {
        if (
          typeof tag ===
          "string"
        ) {
          return tag;
        }

        return tag?.tag;
      }
    )
    .filter(
      (tag) =>
        typeof tag ===
          "string" &&
        tag.length > 0
    );
}


function buildMediaList({
  artworkId,
  pageCount,
  detailBody,
  pagesResponse
}) {
  /*
   * Single-image artwork.
   */
  if (
    pageCount === 1
  ) {
    const originalUrl =
      detailBody.urls
        ?.original;


    if (!originalUrl) {
      throw new Error(
        `Original image URL was not found for artwork ${artworkId}.`
      );
    }


    return [
      createMediaEntry(
        0,
        originalUrl
      )
    ];
  }


  /*
   * Multi-image artwork.
   *
   * Do not guess URLs by changing _p0 into _p1, etc.
   * Use Pixiv's /pages response instead.
   */
  if (
    !pagesResponse ||
    pagesResponse.error ||
    !Array.isArray(
      pagesResponse.body
    )
  ) {
    throw new Error(
      `Artwork ${artworkId} has ${pageCount} images, but valid Pixiv pages data is unavailable.`
    );
  }


  const media =
    pagesResponse.body.map(
      (
        page,
        index
      ) => {
        const originalUrl =
          page?.urls
            ?.original;


        if (!originalUrl) {
          throw new Error(
            `Original URL for image ${index} was not found.`
          );
        }


        return createMediaEntry(
          index,
          originalUrl
        );
      }
    );


  if (
    media.length !==
    pageCount
  ) {
    throw new Error(
      `Expected ${pageCount} images but observed ${media.length}.`
    );
  }


  return media;
}


function createMediaEntry(
  index,
  sourceUrl
) {
  const extension =
    getFileExtension(
      sourceUrl
    );


  return {
    index,

    sourceUrl,

    mimeType:
      inferMimeType(
        extension
      ),

    fileName:
      extension
        ? `image-${index}.${extension}`
        : `image-${index}`
  };
}


function inferMimeType(
  extension
) {
  switch (
    extension
  ) {
    case "jpg":
    case "jpeg":
      return "image/jpeg";

    case "png":
      return "image/png";

    case "gif":
      return "image/gif";

    case "webp":
      return "image/webp";

    default:
      return null;
  }
}


function getFileExtension(
  url
) {
  try {
    const pathname =
      new URL(
        url
      ).pathname;


    const match =
      pathname.match(
        /\.([a-zA-Z0-9]+)$/
      );


    return (
      match?.[1]
        ?.toLowerCase() ??
      null
    );
  } catch {
    return null;
  }
}


function extractArtworkId(
  url
) {
  const match =
    url.match(
      /\/artworks\/(\d+)/
    );

  return (
    match?.[1] ??
    null
  );
}