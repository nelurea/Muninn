(() => {
  if (window.top !== window) {
    return;
  }

  if (window.__muninnPixivCaptureInstalled) {
    return;
  }

  window.__muninnPixivCaptureInstalled = true;

  const ORIGINAL_FETCH = window.fetch;
  const OriginalXhr = window.XMLHttpRequest;

  const observedArtworkData = new Map();

  window.fetch = async function (...args) {
    const response =
      await ORIGINAL_FETCH.apply(
        this,
        args
      );

    try {
      observeFetchResponse(response);
    } catch (error) {
      console.warn(
        "[Muninn/Pixiv] Failed to inspect fetch response:",
        error
      );
    }

    return response;
  };

  class MuninnXMLHttpRequest
    extends OriginalXhr {

    constructor() {
      super();

      this.addEventListener(
        "load",
        () => {
          try {
            observeXhrResponse(this);
          } catch (error) {
            console.warn(
              "[Muninn/Pixiv] Failed to inspect XHR response:",
              error
            );
          }
        }
      );
    }
  }

  window.XMLHttpRequest =
    MuninnXMLHttpRequest;

  window.__muninnCapturePixiv =
    async function () {
      try {
        const capturePackage =
          await captureCurrentArtwork();

        MuninnBridge.postMessage(
          JSON.stringify({
            type:
              "PIXIV_CAPTURE_PROBE_RESULT",

            ok: true,

            capturePackage
          })
        );
      } catch (error) {
        MuninnBridge.postMessage(
          JSON.stringify({
            type:
              "PIXIV_CAPTURE_PROBE_RESULT",

            ok: false,

            error:
              error instanceof Error
                ? error.message
                : String(error)
          })
        );
      }
    };

  async function observeFetchResponse(
    response
  ) {
    const match =
      parseArtworkEndpoint(
        response.url
      );

    if (!match) {
      return;
    }

    const clone =
      response.clone();

    let payload;

    try {
      payload =
        await clone.json();
    } catch {
      return;
    }

    retainObservedResponse(
      match.endpointType,
      match.artworkId,
      payload
    );
  }

  function observeXhrResponse(xhr) {
    const match =
      parseArtworkEndpoint(
        xhr.responseURL
      );

    if (!match) {
      return;
    }

    let payload;

    if (
      xhr.responseType ===
      "json"
    ) {
      payload =
        xhr.response;
    } else if (
      xhr.responseType === "" ||
      xhr.responseType === "text"
    ) {
      try {
        payload =
          JSON.parse(
            xhr.responseText
          );
      } catch {
        return;
      }
    } else {
      return;
    }

    retainObservedResponse(
      match.endpointType,
      match.artworkId,
      payload
    );
  }

  function retainObservedResponse(
    endpointType,
    artworkId,
    payload
  ) {
    const current =
      observedArtworkData.get(
        artworkId
      ) ?? {
        detail: null,
        pages: null
      };

    current[endpointType] =
      payload;

    observedArtworkData.set(
      artworkId,
      current
    );

    console.log(
      `[Muninn/Pixiv] observed ${endpointType}: ${artworkId}`
    );
  }

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
      console.log(
        `[Muninn/Pixiv] detail data was not observed; requesting detail for ${artworkId}.`
      );

      await requestPixivDetail(
        artworkId
      );

      observed =
        observedArtworkData.get(
          artworkId
        );

      if (!observed?.detail) {
        throw new Error(
          `Pixiv detail data was not retained for artwork ${artworkId}.`
        );
      }
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

    if (
      pageCount > 1 &&
      !observed.pages
    ) {
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

  async function requestPixivDetail(
    artworkId
  ) {
    const response =
      await ORIGINAL_FETCH.call(
        window,
        `/ajax/illust/${artworkId}?lang=ja`,
        {
          credentials:
            "same-origin"
        }
      );

    if (!response.ok) {
      throw new Error(
        `Pixiv detail request failed for artwork ${artworkId}: HTTP ${response.status}`
      );
    }

    let payload;

    try {
      payload =
        await response.json();
    } catch (error) {
      throw new Error(
        `Could not parse Pixiv detail response for artwork ${artworkId}: ${
          error instanceof Error
            ? error.message
            : String(error)
        }`
      );
    }

    retainObservedResponse(
      "detail",
      artworkId,
      payload
    );
  }

  async function requestPixivPages(
    artworkId
  ) {
    const response =
      await ORIGINAL_FETCH.call(
        window,
        `/ajax/illust/${artworkId}/pages?lang=ja`,
        {
          credentials:
            "same-origin"
        }
      );

    if (!response.ok) {
      throw new Error(
        `Pixiv pages request failed for artwork ${artworkId}: HTTP ${response.status}`
      );
    }

    let payload;

    try {
      payload =
        await response.json();
    } catch (error) {
      throw new Error(
        `Could not parse Pixiv pages response for artwork ${artworkId}: ${
          error instanceof Error
            ? error.message
            : String(error)
        }`
      );
    }

    retainObservedResponse(
      "pages",
      artworkId,
      payload
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
          extractTags(body)
      },

      media
    };
  }

  function extractTags(body) {
    const rawTags =
      body.tags?.tags;

    if (!Array.isArray(rawTags)) {
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
    if (pageCount === 1) {
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

  function inferMimeType(extension) {
    switch (extension) {
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

  function getFileExtension(url) {
    try {
      const pathname =
        new URL(url).pathname;

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

  function extractArtworkId(url) {
    const match =
      url.match(
        /\/artworks\/(\d+)/
      );

    return (
      match?.[1] ??
      null
    );
  }

  console.log(
    "[Muninn/Pixiv] capture hook installed"
  );
})();