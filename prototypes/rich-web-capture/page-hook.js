(() => {
  const MESSAGE_SOURCE =
    "MUNINN_PIXIV_PAGE_HOOK";

  const COMMAND_SOURCE =
    "MUNINN_CONTENT_SCRIPT";

  const ORIGINAL_FETCH =
    window.fetch;

  const OriginalXhr =
    window.XMLHttpRequest;


  console.log(
    "[Muninn/PageHook] installed"
  );


  /*
   * Observe responses returned to Pixiv's own fetch() calls.
   *
   * Muninn does not initiate these detail requests.
   * The response is cloned so Pixiv can continue using
   * the original response normally.
   */
  window.fetch = async function (...args) {
    const response =
      await ORIGINAL_FETCH.apply(
        this,
        args
      );

    try {
      observeFetchResponse(
        response
      );
    } catch (error) {
      console.warn(
        "[Muninn/PageHook] Failed to inspect fetch response:",
        error
      );
    }

    return response;
  };


  /*
   * Observe XMLHttpRequest as well, in case Pixiv
   * uses XHR for a relevant endpoint.
   */
  class MuninnXMLHttpRequest
    extends OriginalXhr {

    constructor() {
      super();

      this.addEventListener(
        "load",
        () => {
          try {
            observeXhrResponse(
              this
            );
          } catch (error) {
            console.warn(
              "[Muninn/PageHook] Failed to inspect XHR response:",
              error
            );
          }
        }
      );
    }
  }

  window.XMLHttpRequest =
    MuninnXMLHttpRequest;


  /*
   * content.js can request /pages when a multi-image
   * artwork has not caused Pixiv to load that endpoint yet.
   *
   * This happens at capture time only.
   */
  window.addEventListener(
    "message",
    async (event) => {
      if (
        event.source !== window
      ) {
        return;
      }

      if (
        event.origin !== location.origin
      ) {
        return;
      }

      const message =
        event.data;

      if (
        !message ||
        message.source !==
          COMMAND_SOURCE ||
        message.type !==
          "REQUEST_PIXIV_PAGES"
      ) {
        return;
      }

      const artworkId =
        message.artworkId;

      if (
        typeof artworkId !==
          "string" ||
        !/^\d+$/.test(
          artworkId
        )
      ) {
        return;
      }

      console.log(
        `[Muninn/PageHook] requesting pages for ${artworkId}`
      );

      try {
        /*
         * Use the page's own fetch environment.
         *
         * Because window.fetch is already wrapped above,
         * the response will also pass through
         * observeFetchResponse().
         */
        const response =
          await window.fetch(
            `/ajax/illust/${artworkId}/pages?lang=ja`,
            {
              credentials:
                "same-origin"
            }
          );

        if (!response.ok) {
          throw new Error(
            `HTTP ${response.status}`
          );
        }
      } catch (error) {
        console.error(
          `[Muninn/PageHook] pages request failed for ${artworkId}:`,
          error
        );

        window.postMessage(
          {
            source:
              MESSAGE_SOURCE,

            type:
              "PIXIV_PAGES_REQUEST_FAILED",

            artworkId,

            error:
              error instanceof Error
                ? error.message
                : String(error)
          },
          location.origin
        );
      }
    }
  );


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

    publishObservedResponse({
      transport:
        "fetch",

      endpointType:
        match.endpointType,

      artworkId:
        match.artworkId,

      url:
        response.url,

      status:
        response.status,

      payload
    });
  }


  function observeXhrResponse(
    xhr
  ) {
    const match =
      parseArtworkEndpoint(
        xhr.responseURL
      );

    if (!match) {
      return;
    }

    let payload = null;

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

    publishObservedResponse({
      transport:
        "xhr",

      endpointType:
        match.endpointType,

      artworkId:
        match.artworkId,

      url:
        xhr.responseURL,

      status:
        xhr.status,

      payload
    });
  }


  function parseArtworkEndpoint(
    rawUrl
  ) {
    if (!rawUrl) {
      return null;
    }

    let url;

    try {
      url =
        new URL(
          rawUrl,
          location.href
        );
    } catch {
      return null;
    }

    if (
      url.origin !==
      "https://www.pixiv.net"
    ) {
      return null;
    }

    const pagesMatch =
      url.pathname.match(
        /^\/ajax\/illust\/(\d+)\/pages$/
      );

    if (pagesMatch) {
      return {
        endpointType:
          "pages",

        artworkId:
          pagesMatch[1]
      };
    }

    const detailMatch =
      url.pathname.match(
        /^\/ajax\/illust\/(\d+)$/
      );

    if (detailMatch) {
      return {
        endpointType:
          "detail",

        artworkId:
          detailMatch[1]
      };
    }

    return null;
  }


  function publishObservedResponse({
    transport,
    endpointType,
    artworkId,
    url,
    status,
    payload
  }) {
    console.log(
      `[Muninn/PageHook] observed ${endpointType}:`,
      artworkId,
      payload
    );

    window.postMessage(
      {
        source:
          MESSAGE_SOURCE,

        type:
          "PIXIV_RESPONSE",

        transport,
        endpointType,
        artworkId,
        url,
        status,
        payload
      },
      location.origin
    );
  }
})();