(() => {
  const MESSAGE_SOURCE =
    "MUNINN_X_PAGE_HOOK";

  const ORIGINAL_FETCH =
    window.fetch;

  const OriginalXhr =
    window.XMLHttpRequest;

  window.fetch = async function (...args) {
    const response =
      await ORIGINAL_FETCH.apply(
        this,
        args
      );

    try {
      await observeFetchResponse(
        response
      );
    } catch (error) {
      console.warn(
        "[Muninn/X/PageHook] Failed to inspect fetch response:",
        error
      );
    }

    return response;
  };


  class MuninnXXMLHttpRequest
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
              "[Muninn/X/PageHook] Failed to inspect XHR response:",
              error
            );
          }
        }
      );
    }
  }

  window.XMLHttpRequest =
    MuninnXXMLHttpRequest;


  async function observeFetchResponse(
    response
  ) {
    const operation =
      parseRelevantOperation(
        response.url
      );

    if (!operation) {
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
      transport: "fetch",
      operation,
      url: response.url,
      status: response.status,
      payload
    });
  }


  function observeXhrResponse(
    xhr
  ) {
    const operation =
      parseRelevantOperation(
        xhr.responseURL
      );

    if (!operation) {
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

    publishObservedResponse({
      transport: "xhr",
      operation,
      url: xhr.responseURL,
      status: xhr.status,
      payload
    });
  }


  function parseRelevantOperation(
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
      "https://x.com"
    ) {
      return null;
    }

    if (
      url.pathname.includes(
        "/TweetResultByRestId"
      )
    ) {
      return "TweetResultByRestId";
    }

    if (
      url.pathname.includes(
        "/TweetDetail"
      )
    ) {
      return "TweetDetail";
    }

    return null;
  }


  function publishObservedResponse({
    transport,
    operation,
    url,
    status,
    payload
  }) {

    window.postMessage(
      {
        source:
          MESSAGE_SOURCE,

        type:
          "X_RESPONSE",

        transport,
        operation,
        url,
        status,
        payload
      },
      location.origin
    );
  }
})();