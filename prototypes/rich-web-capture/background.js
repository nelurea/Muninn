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

      console.log(
        "[Muninn] Capture response:",
        response
      );
    } catch (error) {
      console.error(
        "[Muninn] Failed to request capture:",
        error
      );
    }
  }
);