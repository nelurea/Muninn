(() => {
  const ORIGINAL_FETCH =
    window.fetch;

  const OriginalXhr =
    window.XMLHttpRequest;

  const observedTweets =
    new Map();


  /*
   * Observe X GraphQL responses as early as possible.
   *
   * X is a SPA, so the information needed for capture
   * may have arrived before the user presses Capture.
   */

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
        "[Muninn/X] Failed to inspect fetch response:",
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
              "[Muninn/X] Failed to inspect XHR response:",
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
    if (
      !parseRelevantOperation(
        response.url
      )
    ) {
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

    collectObservedTweets(
      payload
    );
  }


  function observeXhrResponse(
    xhr
  ) {
    if (
      !parseRelevantOperation(
        xhr.responseURL
      )
    ) {
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

    collectObservedTweets(
      payload
    );
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


  /*
   * TweetResultByRestId has a simple result location.
   * TweetDetail may contain multiple tweet objects nested
   * inside timeline instructions.
   *
   * Traverse only the relevant GraphQL response and cache
   * tweet-shaped objects by rest_id.
   */

  function collectObservedTweets(
    root
  ) {
    const visited =
      new Set();

    function visit(
      value
    ) {
      if (
        value === null ||
        typeof value !==
          "object"
      ) {
        return;
      }

      if (
        visited.has(
          value
        )
      ) {
        return;
      }

      visited.add(
        value
      );

      const candidate =
        unwrapTweetResult(
          value
        );

      if (
        isTweetResult(
          candidate
        )
      ) {
        observedTweets.set(
          candidate.rest_id,
          candidate
        );
      }

      if (
        Array.isArray(
          value
        )
      ) {
        for (
          const item
          of value
        ) {
          visit(
            item
          );
        }

        return;
      }

      for (
        const child
        of Object.values(
          value
        )
      ) {
        visit(
          child
        );
      }
    }

    visit(
      root
    );
  }


  function unwrapTweetResult(
    value
  ) {
    let current =
      value;

    /*
     * X occasionally wraps tweet data in result/tweet
     * objects such as TweetWithVisibilityResults.
     */

    for (
      let index = 0;
      index < 4;
      index += 1
    ) {
      if (
        isTweetResult(
          current
        )
      ) {
        return current;
      }

      if (
        current &&
        typeof current ===
          "object" &&
        current.tweet &&
        typeof current.tweet ===
          "object"
      ) {
        current =
          current.tweet;

        continue;
      }

      if (
        current &&
        typeof current ===
          "object" &&
        current.result &&
        typeof current.result ===
          "object"
      ) {
        current =
          current.result;

        continue;
      }

      break;
    }

    return current;
  }


  function isTweetResult(
    value
  ) {
    return (
      value !== null &&
      typeof value ===
        "object" &&
      typeof value.rest_id ===
        "string" &&
      /^\d+$/.test(
        value.rest_id
      ) &&
      value.legacy !== null &&
      typeof value.legacy ===
        "object" &&
      value.core
        ?.user_results
        ?.result
    );
  }


  /*
   * Called from Android WebCaptureScreen.
   */

  window.__muninnCaptureX =
    function () {
      try {
        const capturePackage =
          captureCurrentPost();

        sendResult({
          type:
            "X_CAPTURE_RESULT",

          ok:
            true,

          capturePackage
        });

        return "started";
      } catch (error) {
        const message =
          error instanceof Error
            ? error.message
            : String(
                error
              );

        console.error(
          "[Muninn/X] Capture failed:",
          error
        );

        sendResult({
          type:
            "X_CAPTURE_RESULT",

          ok:
            false,

          error:
            message
        });

        return "failed";
      }
    };


  function captureCurrentPost() {
    const postId =
      extractPostId(
        location.href
      );

    if (!postId) {
      throw new Error(
        `This is not an X post page: ${location.href}`
      );
    }

    const result =
      observedTweets.get(
        postId
      );

    if (!result) {
      throw new Error(
        `No observed X post response for ${postId}. Reload the post and try again.`
      );
    }

    return createCapturePackage(
      result
    );
  }


  function createCapturePackage(
    result
  ) {
    const postId =
      result.rest_id;

    const legacy =
      result.legacy;

    const author =
      unwrapUserResult(
        result.core
          ?.user_results
          ?.result
      );

    if (
      !postId ||
      !legacy ||
      !author
    ) {
      throw new Error(
        "Observed X post data is incomplete."
      );
    }

    const authorId =
      author.rest_id;

    const authorName =
      author.core
        ?.name;

    const screenName =
      author.core
        ?.screen_name;

    if (
      !authorId ||
      !authorName ||
      !screenName
    ) {
      throw new Error(
        "X author data is incomplete."
      );
    }

    const media =
      buildMediaList(
        legacy
      );

    return {
      schemaVersion:
        1,

      source: {
        type:
          "x",

        id:
          postId,

        canonicalUrl:
          `https://x.com/${screenName}/status/${postId}`
      },

      capturedAt:
        new Date()
          .toISOString(),

      publishedAt:
        normalizePublishedAt(
          legacy.created_at
        ),

      content: {
        author: {
          id:
            String(
              authorId
            ),

          name:
            authorName,

          handle:
            screenName
        },

        title:
          null,

        caption:
          extractPostText(
            legacy
          ),

        tags:
          extractHashtags(
            legacy
          )
      },

      media
    };
  }


  function unwrapUserResult(
    value
  ) {
    let current =
      value;

    for (
      let index = 0;
      index < 4;
      index += 1
    ) {
      if (
        current &&
        typeof current ===
          "object" &&
        current.rest_id &&
        current.core
      ) {
        return current;
      }

      if (
        current &&
        typeof current ===
          "object" &&
        current.result &&
        typeof current.result ===
          "object"
      ) {
        current =
          current.result;

        continue;
      }

      break;
    }

    return current;
  }


  function normalizePublishedAt(
    value
  ) {
    if (
      typeof value !==
        "string" ||
      value.length === 0
    ) {
      return null;
    }

    const timestamp =
      Date.parse(
        value
      );

    if (
      Number.isNaN(
        timestamp
      )
    ) {
      return null;
    }

    return new Date(
      timestamp
    ).toISOString();
  }


  function extractPostText(
    legacy
  ) {
    const fullText =
      legacy.full_text;

    if (
      typeof fullText !==
        "string"
    ) {
      return "";
    }

    const range =
      legacy.display_text_range;

    if (
      Array.isArray(
        range
      ) &&
      range.length === 2 &&
      Number.isInteger(
        range[0]
      ) &&
      Number.isInteger(
        range[1]
      )
    ) {
      return fullText.slice(
        range[0],
        range[1]
      );
    }

    return fullText;
  }


  function extractHashtags(
    legacy
  ) {
    const hashtags =
      legacy.entities
        ?.hashtags;

    if (
      !Array.isArray(
        hashtags
      )
    ) {
      return [];
    }

    return hashtags
      .map(
        (entry) =>
          entry?.text
      )
      .filter(
        (tag) =>
          typeof tag ===
            "string" &&
          tag.length > 0
      );
  }


  function buildMediaList(
    legacy
  ) {
    const rawMedia =
      legacy.extended_entities
        ?.media;

    if (
      !Array.isArray(
        rawMedia
      ) ||
      rawMedia.length === 0
    ) {
      throw new Error(
        "This X post does not contain image media."
      );
    }

    const photoMedia =
      rawMedia.filter(
        (item) =>
          item?.type ===
          "photo"
      );

    if (
      photoMedia.length === 0
    ) {
      throw new Error(
        "This X post does not contain supported photo media."
      );
    }

    /*
     * #44 currently captures photo media only.
     *
     * If the post mixes photos with unsupported media,
     * preserve the supported photos rather than failing
     * the entire capture.
     */

    return photoMedia.map(
      (
        item,
        index
      ) => {
        const sourceUrl =
          buildOriginalImageUrl(
            item.media_url_https
          );

        if (!sourceUrl) {
          throw new Error(
            `X image URL was not found at index ${index}.`
          );
        }

        return createMediaEntry(
          index,
          sourceUrl
        );
      }
    );
  }


  function buildOriginalImageUrl(
    mediaUrl
  ) {
    if (
      typeof mediaUrl !==
        "string" ||
      mediaUrl.length === 0
    ) {
      return null;
    }

    const url =
      new URL(
        mediaUrl
      );

    const match =
      url.pathname.match(
        /\.([a-zA-Z0-9]+)$/
      );

    const extension =
      match?.[1]
        ?.toLowerCase();

    if (!extension) {
      throw new Error(
        `Could not determine X image format: ${mediaUrl}`
      );
    }

    url.pathname =
      url.pathname.replace(
        /\.[a-zA-Z0-9]+$/,
        ""
      );

    url.search =
      "";

    url.searchParams.set(
      "format",
      extension ===
        "jpeg"
        ? "jpg"
        : extension
    );

    url.searchParams.set(
      "name",
      "orig"
    );

    return url.toString();
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


  function getFileExtension(
    url
  ) {
    try {
      const parsedUrl =
        new URL(
          url
        );

      const format =
        parsedUrl
          .searchParams
          .get(
            "format"
          );

      if (format) {
        return format
          .toLowerCase();
      }

      const match =
        parsedUrl
          .pathname
          .match(
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

      case "webp":
        return "image/webp";

      default:
        return null;
    }
  }


  function extractPostId(
    url
  ) {
    const match =
      url.match(
        /\/status\/(\d+)/
      );

    return (
      match?.[1] ??
      null
    );
  }


  function sendResult(
    result
  ) {
    if (
      typeof MuninnBridge ===
        "undefined" ||
      typeof MuninnBridge
        .postMessage !==
        "function"
    ) {
      console.error(
        "[Muninn/X] MuninnBridge is unavailable."
      );

      return;
    }

    MuninnBridge.postMessage(
      JSON.stringify(
        result
      )
    );
  }
})();