(() => {
  const ORIGINAL_FETCH =
    window.fetch;

  const OriginalXhr =
    window.XMLHttpRequest;

  /*
   * Individual-post capture cache.
   *
   * This remains for the existing #44 capture flow.
   */
  const observedTweets =
    new Map();

  /*
   * Observation-only registry used while implementing
   * X Discovery.
   *
   * This prevents the GraphQL diagnostic log from being
   * flooded by repeated requests during one page lifetime.
   */
  const observedGraphqlOperations =
    new Set();


  /*
   * X GraphQL operations verified on-device for Issue #46.
   *
   * DiscoveryMode mapping:
   *
   * HomeTimeline
   *   -> LATEST
   *   -> X "For You"
   *
   * Likes
   *   -> BOOKMARKS
   *   -> liked posts
   *
   * SearchTimeline
   *   -> SEARCH
   */
  const DISCOVERY_OPERATIONS =
    new Map([
      [
        "HomeTimeline",
        "LATEST"
      ],
      [
        "HomeLatestTimeline",
        "FOLLOWING"
      ],
      [
        "Likes",
        "BOOKMARKS"
      ],
      [
        "SearchTimeline",
        "SEARCH"
      ]
    ]);


  /*
   * Observe X GraphQL responses as early as possible.
   *
   * X is a SPA, so the data needed for capture or Discovery
   * may arrive before the user explicitly asks Muninn to
   * use it.
   */

  window.fetch =
    async function (...args) {
      const response =
        await ORIGINAL_FETCH.apply(
          this,
          args
        );

      try {
        observeGraphqlOperation(
          response.url,
          "fetch"
        );
      } catch (error) {
        console.warn(
          "[Muninn/X/GraphQL] Failed to observe fetch operation:",
          error
        );
      }

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
            observeGraphqlOperation(
              this.responseURL,
              "xhr"
            );
          } catch (error) {
            console.warn(
              "[Muninn/X/GraphQL] Failed to observe XHR operation:",
              error
            );
          }

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


  /*
   * ---------------------------------------------------------
   * GraphQL operation observation
   * ---------------------------------------------------------
   */

  function observeGraphqlOperation(
    rawUrl,
    transport
  ) {
    const operation =
      parseGraphqlOperation(
        rawUrl
      );

    if (!operation) {
      return;
    }

    if (
      observedGraphqlOperations.has(
        operation
      )
    ) {
      return;
    }

    observedGraphqlOperations.add(
      operation
    );

    console.info(
      `[Muninn/X/GraphQL] transport=${transport} operation=${operation}`
    );
  }


  function parseGraphqlOperation(
    rawUrl
  ) {
    const parsed =
      parseXUrl(
        rawUrl
      );

    if (!parsed) {
      return null;
    }

    const pathParts =
      parsed.pathname
        .split(
          "/"
        )
        .filter(
          Boolean
        );

    const graphqlIndex =
      pathParts.indexOf(
        "graphql"
      );

    if (
      graphqlIndex < 0
    ) {
      return null;
    }

    /*
     * Typical form:
     *
     * /i/api/graphql/{queryId}/{operationName}
     */

    const operationName =
      pathParts[
        graphqlIndex + 2
      ];

    if (
      typeof operationName !==
        "string" ||
      operationName.length ===
        0
    ) {
      return null;
    }

    try {
      return decodeURIComponent(
        operationName
      );
    } catch {
      return operationName;
    }
  }


  function parseXUrl(
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
      url.hostname !==
        "x.com" &&
      !url.hostname.endsWith(
        ".x.com"
      )
    ) {
      return null;
    }

    return url;
  }


  /*
   * ---------------------------------------------------------
   * Response observation
   * ---------------------------------------------------------
   */

  async function observeFetchResponse(
    response
  ) {
    const operation =
      parseGraphqlOperation(
        response.url
      );

    if (
      !isRelevantOperation(
        operation
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

    processObservedResponse(
      operation,
      response.url,
      payload
    );
  }


  function observeXhrResponse(
    xhr
  ) {
    const operation =
      parseGraphqlOperation(
        xhr.responseURL
      );

    if (
      !isRelevantOperation(
        operation
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

    processObservedResponse(
      operation,
      xhr.responseURL,
      payload
    );
  }


  function isRelevantOperation(
    operation
  ) {
    if (!operation) {
      return false;
    }

    return (
      operation ===
        "TweetResultByRestId" ||
      operation ===
        "TweetDetail" ||
      DISCOVERY_OPERATIONS.has(
        operation
      )
    );
  }


  function processObservedResponse(
    operation,
    rawUrl,
    payload
  ) {
    if (
      operation ===
        "TweetResultByRestId" ||
      operation ===
        "TweetDetail"
    ) {
      collectObservedTweets(
        payload
      );
    }

    if (
      DISCOVERY_OPERATIONS.has(
        operation
      )
    ) {
      sendDiscoveryBatch(
        operation,
        rawUrl,
        payload
      );
    }
  }


  /*
   * ---------------------------------------------------------
   * Existing single-post capture observation
   * ---------------------------------------------------------
   */

  function collectObservedTweets(
    root
  ) {
    const results =
      collectTweetResults(
        root
      );

    for (
      const result
      of results
    ) {
      observedTweets.set(
        result.rest_id,
        result
      );
    }
  }


  /*
   * Traverse a relevant GraphQL response and return unique
   * tweet-shaped objects.
   *
   * The same traversal is used for:
   *
   * - TweetDetail / TweetResultByRestId
   * - HomeTimeline
   * - Likes
   * - SearchTimeline
   */

  function collectTweetResults(
    root
  ) {
    const visited =
      new Set();

    const results =
      new Map();

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
        results.set(
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

    return Array.from(
      results.values()
    );
  }


  function unwrapTweetResult(
    value
  ) {
    let current =
      value;

    /*
     * X may wrap tweet data in structures such as:
     *
     * - result
     * - tweet
     * - TweetWithVisibilityResults
     */

    for (
      let index = 0;
      index < 6;
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
   * ---------------------------------------------------------
   * Discovery
   * ---------------------------------------------------------
   */

  /*
   * Issue #54 diagnostic.
   *
   * Inspect raw timeline wrapper objects for explicit
   * promotion / advertising fields before collectTweetResults()
   * discards that wrapper context.
   */
  /*
   * Return every Tweet id that belongs to an explicitly
   * promoted timeline entry.
   *
   * X currently marks these entries with:
   *
   *   entryId: "promoted-tweet-<id>"
   *
   * Filter the entire entry rather than only the primary
   * Tweet id so quoted / nested Tweet objects inside an ad
   * cannot leak into Discovery.
   */
  function collectPromotedTweetIds(
    root
  ) {
    const visited =
      new Set();

    const promotedTweetIds =
      new Set();

    function visit(
      value
    ) {
      if (
        value === null ||
        typeof value !==
          "object" ||
        visited.has(
          value
        )
      ) {
        return;
      }

      visited.add(
        value
      );

      const entryId =
        typeof value.entryId ===
          "string"
          ? value.entryId
          : typeof value.entry_id ===
              "string"
            ? value.entry_id
            : null;

      if (
        entryId !== null &&
        entryId.startsWith(
          "promoted-tweet-"
        )
      ) {
        const promotedResults =
          collectTweetResults(
            value
          );

        for (
          const result
          of promotedResults
        ) {
          promotedTweetIds.add(
            result.rest_id
          );
        }

        /*
         * Everything below this entry belongs to the
         * promoted timeline item and has already been
         * collected above.
         */
        return;
      }

      if (
        Array.isArray(
          value
        )
      ) {
        for (
          const child
          of value
        ) {
          visit(
            child
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

    return promotedTweetIds;
  }


  function sendDiscoveryBatch(
    operation,
    rawUrl,
    root
  ) {
    const mode =
      DISCOVERY_OPERATIONS.get(
        operation
      );

    if (!mode) {
      return;
    }

    const promotedTweetIds =
      collectPromotedTweetIds(
        root
      );

    const tweetResults =
      collectTweetResults(
        root
      )
        .filter(
          (result) =>
            !promotedTweetIds.has(
              result.rest_id
            )
        );

    if (
      tweetResults.length ===
        0
    ) {
      return;
    }

    /*
     * Discovery currently handles image works only.
     *
     * Ignore:
     *
     * - text-only posts
     * - video-only posts
     * - unsupported / incomplete tweet objects
     *
     * A malformed individual item must not make the entire
     * timeline batch fail.
     */
    const items =
      [];

    for (
      const result
      of tweetResults
    ) {
      try {
        const capturePackage =
          createCapturePackage(
            result
          );

        if (
          !capturePackage.media ||
          capturePackage.media.length ===
            0
        ) {
          continue;
        }

        items.push(
          capturePackage
        );
      } catch {
        /*
         * Expected for text-only, video-only, ads, deleted
         * posts, incomplete timeline entries, etc.
         *
         * Silently ignore them for Discovery.
         */
      }
    }

    if (
      items.length ===
        0
    ) {
      return;
    }

    const query =
      mode ===
        "SEARCH"
        ? extractSearchQuery(
            rawUrl
          )
        : null;

    sendResult({
      type:
        "X_DISCOVERY_BATCH",

      mode,

      query,

      items
    });

    console.info(
      `[Muninn/X/Discovery] operation=${operation} mode=${mode} items=${items.length}`
    );
  }


  /*
   * SearchTimeline normally carries its search expression
   * inside the JSON-encoded "variables" query parameter.
   *
   * We only extract it to associate observed results with
   * the corresponding Muninn Discovery query.
   */

  function extractSearchQuery(
    rawUrl
  ) {
    const url =
      parseXUrl(
        rawUrl
      );

    if (!url) {
      return null;
    }

    const variablesText =
      url.searchParams.get(
        "variables"
      );

    if (!variablesText) {
      return null;
    }

    let variables;

    try {
      variables =
        JSON.parse(
          variablesText
        );
    } catch {
      return null;
    }

    const candidates = [
      variables.rawQuery,
      variables.query,
      variables.searchQuery
    ];

    for (
      const candidate
      of candidates
    ) {
      if (
        typeof candidate ===
          "string" &&
        candidate.trim().length >
          0
      ) {
        return candidate.trim();
      }
    }

    return null;
  }


  /*
   * Optional diagnostic helpers.
   */

  window.__muninnListXGraphqlOperations =
    function () {
      return Array.from(
        observedGraphqlOperations
      );
    };


  window.__muninnGetObservedXPostIds =
    function () {
      return Array.from(
        observedTweets.keys()
      );
    };


  /*
   * ---------------------------------------------------------
   * Existing #44 single-post capture
   * ---------------------------------------------------------
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


  /*
   * ---------------------------------------------------------
   * Shared Tweet -> Capture Package mapper
   * ---------------------------------------------------------
   *
   * This is intentionally shared by:
   *
   * - existing X capture
   * - X Discovery
   *
   * so both paths use the same canonical representation.
   */

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
      index < 6;
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
      value.length ===
        0
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
      range.length ===
        2 &&
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


  /*
   * ---------------------------------------------------------
   * Media
   * ---------------------------------------------------------
   */

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
      rawMedia.length ===
        0
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
      photoMedia.length ===
        0
    ) {
      throw new Error(
        "This X post does not contain supported photo media."
      );
    }

    /*
     * Photo-only capture remains the current supported
     * media model.
     *
     * If a post mixes photos and unsupported media,
     * preserve the supported photos.
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
      mediaUrl.length ===
        0
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


  /*
   * ---------------------------------------------------------
   * Native bridge
   * ---------------------------------------------------------
   */

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
