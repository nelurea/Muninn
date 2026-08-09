console.log(
  "[Muninn/X] content script loaded:",
  location.href
);

const MESSAGE_SOURCE =
  "MUNINN_X_PAGE_HOOK";


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

    if (
      !message ||
      message.source !==
        MESSAGE_SOURCE ||
      message.type !==
        "X_RESPONSE"
    ) {
      return;
    }

    if (
      message.operation !==
        "TweetResultByRestId"
    ) {
      return;
    }

    const result =
      message.payload
        ?.data
        ?.tweetResult
        ?.result;

    if (!result) {
      console.warn(
        "[Muninn/X] Tweet result is missing."
      );

      return;
    }

    console.log(
      "[Muninn/X] Tweet result keys:",
      Object.keys(result)
    );

    console.log(
      "[Muninn/X] Tweet legacy:\n" +
      JSON.stringify(
        result.legacy,
        null,
        2
      )
    );

    console.log(
      "[Muninn/X] Author result:\n" +
      JSON.stringify(
        result.core
          ?.user_results
          ?.result,
        null,
        2
      )
    );

    console.log(
      "[Muninn/X] Note tweet:\n" +
      JSON.stringify(
        result.note_tweet,
        null,
        2
      )
    );
  }
);