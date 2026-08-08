(() => {
  const MESSAGE_SOURCE = "MUNINN_PIXIV_PAGE_HOOK";
  const ORIGINAL_FETCH = window.fetch;
  const OriginalXhr = window.XMLHttpRequest;

  console.log("[Muninn/PageHook] installed");

  /*
   * Observe responses returned to Pixiv's own fetch() calls.
   *
   * Important:
   * Muninn does not initiate the request here.
   * It only clones a response that Pixiv itself already received.
   */
  window.fetch = async function (...args) {
    const response = await ORIGINAL_FETCH.apply(this, args);

    try {
      observeFetchResponse(response);
    } catch (error) {
      console.warn(
        "[Muninn/PageHook] Failed to inspect fetch response:",
        error
      );
    }

    return response;
  };

  /*
   * Also observe XMLHttpRequest in case Pixiv uses XHR
   * instead of fetch for some requests.
   */
  class MuninnXMLHttpRequest extends OriginalXhr {
    constructor() {
      super();

      this.addEventListener("load", () => {
        try {
          observeXhrResponse(this);
        } catch (error) {
          console.warn(
            "[Muninn/PageHook] Failed to inspect XHR response:",
            error
          );
        }
      });
    }
  }

  window.XMLHttpRequest = MuninnXMLHttpRequest;


  async function observeFetchResponse(response) {
    const match = parseArtworkEndpoint(response.url);

    if (!match) {
      return;
    }

    const clone = response.clone();

    let payload;

    try {
      payload = await clone.json();
    } catch {
      return;
    }

    publishObservedResponse({
      transport: "fetch",
      endpointType: match.endpointType,
      artworkId: match.artworkId,
      url: response.url,
      status: response.status,
      payload
    });
  }


  function observeXhrResponse(xhr) {
    const match = parseArtworkEndpoint(xhr.responseURL);

    if (!match) {
      return;
    }

    let payload = null;

    if (xhr.responseType === "json") {
      payload = xhr.response;
    } else if (
      xhr.responseType === "" ||
      xhr.responseType === "text"
    ) {
      try {
        payload = JSON.parse(xhr.responseText);
      } catch {
        return;
      }
    } else {
      return;
    }

    publishObservedResponse({
      transport: "xhr",
      endpointType: match.endpointType,
      artworkId: match.artworkId,
      url: xhr.responseURL,
      status: xhr.status,
      payload
    });
  }


  function parseArtworkEndpoint(rawUrl) {
    if (!rawUrl) {
      return null;
    }

    let url;

    try {
      url = new URL(rawUrl, location.href);
    } catch {
      return null;
    }

    if (url.origin !== "https://www.pixiv.net") {
      return null;
    }

    const pagesMatch =
      url.pathname.match(
        /^\/ajax\/illust\/(\d+)\/pages$/
      );

    if (pagesMatch) {
      return {
        endpointType: "pages",
        artworkId: pagesMatch[1]
      };
    }

    const detailMatch =
      url.pathname.match(
        /^\/ajax\/illust\/(\d+)$/
      );

    if (detailMatch) {
      return {
        endpointType: "detail",
        artworkId: detailMatch[1]
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
        source: MESSAGE_SOURCE,
        type: "PIXIV_RESPONSE",
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