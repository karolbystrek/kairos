import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { NotificationIcon } from "@/src/pwa/notification-control";

describe("notification control icon", () => {
  it("shows a normal bell when notifications can be enabled", () => {
    const markup = renderToStaticMarkup(<NotificationIcon enabled={false} />);

    expect(markup).toContain('class="lucide lucide-bell"');
    expect(markup).not.toContain('class="lucide lucide-bell-off"');
  });

  it("shows a crossed bell when notifications are enabled", () => {
    const markup = renderToStaticMarkup(<NotificationIcon enabled />);

    expect(markup).toContain('class="lucide lucide-bell-off"');
    expect(markup).not.toContain('class="lucide lucide-bell"');
  });
});
