const MESSAGE_SOURCE =
  "MUNINN_X_PAGE_HOOK";

const observedTweets =
  new Map();


window.addEventListener(
  "message",
  (event) => {
    if (
      event.source !== window ||
      event.origin !== location.origin
    ) {
      return;
    }

    const message =
      event.data;

    if (
      !message ||
      message.source !== MESSAGE_SOURCE ||
      message.type !== "X_RESPONSE" ||
      message.operation !== "TweetResultByRestId"
    ) {
      return;
    }

    const result =
      message.payload
        ?.data
        ?.tweetResult
        ?.result;

    const postId =
      result?.rest_id;

    if (
      typeof postId !== "string" ||
      !/^\d+$/.test(postId)
    ) {
      return;
    }

    observedTweets.set(
      postId,
      result
    );
  }
);


chrome.runtime.onMessage.addListener(
  (
    message,
    sender,
    sendResponse
  ) => {
    if (
      message.type !== "MUNINN_CAPTURE"
    ) {
      return;
    }

    try {
      const capturePackage =
        captureCurrentPost();

      sendResponse({
        ok: true,
        capturePackage
      });
    } catch (error) {
      console.error(
        "[Muninn/X] Capture failed:",
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
  }
);


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
      `No observed X post response for ${postId}.`
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
    result.core
      ?.user_results
      ?.result;

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
    author.core?.name;

  const screenName =
    author.core?.screen_name;

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
    schemaVersion: 1,

    source: {
      type: "x",
      id: postId,
      canonicalUrl:
        `https://x.com/${screenName}/status/${postId}`
    },

    capturedAt:
      new Date().toISOString(),

    content: {
      author: {
        id:
          String(authorId),

        name:
          authorName
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


function extractPostText(
  legacy
) {
  const fullText =
    legacy.full_text;

  if (
    typeof fullText !== "string"
  ) {
    return "";
  }

  const range =
    legacy.display_text_range;

  if (
    Array.isArray(range) &&
    range.length === 2 &&
    Number.isInteger(range[0]) &&
    Number.isInteger(range[1])
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
    legacy.entities?.hashtags;

  if (!Array.isArray(hashtags)) {
    return [];
  }

  return hashtags
    .map(
      (entry) =>
        entry?.text
    )
    .filter(
      (tag) =>
        typeof tag === "string" &&
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
    !Array.isArray(rawMedia) ||
    rawMedia.length === 0
  ) {
    throw new Error(
      "This X post does not contain media."
    );
  }

  return rawMedia.map(
    (
      item,
      index
    ) => {
      if (
        item?.type !== "photo"
      ) {
        throw new Error(
          `Unsupported X media type at index ${index}: ${item?.type}`
        );
      }

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
  const url =
    new URL(mediaUrl);

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

  url.search = "";

  url.searchParams.set(
    "format",
    extension === "jpeg"
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
      new URL(url);

    const format =
      parsedUrl.searchParams.get(
        "format"
      );

    if (format) {
      return format.toLowerCase();
    }

    const match =
      parsedUrl.pathname.match(
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
  switch (extension) {
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