import { describe, expect, it, vi } from "vitest";

import { disposeCameraSession } from "@/src/scanner/camera-session";

describe("camera cleanup", () => {
  it("stops the scanner, decoder, and every remaining media track", () => {
    const stopTrack = vi.fn();
    const stopScanner = vi.fn();
    const destroyScanner = vi.fn();
    const video = {
      srcObject: {
        getTracks: () => [{ stop: stopTrack }],
      } as unknown as MediaStream,
    };

    disposeCameraSession(
      { destroy: destroyScanner, stop: stopScanner },
      video,
    );

    expect(stopScanner).toHaveBeenCalledOnce();
    expect(destroyScanner).toHaveBeenCalledOnce();
    expect(stopTrack).toHaveBeenCalledOnce();
    expect(video.srcObject).toBeNull();
  });
});
