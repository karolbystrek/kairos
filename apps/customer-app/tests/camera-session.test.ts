import { describe, expect, it, vi } from "vitest";

import { disposeCameraSession } from "@/src/scanner/camera-session";

describe("camera cleanup", () => {
  it.each(["none", "stop", "destroy"])(
    "releases every camera track with a %s cleanup failure",
    (failure) => {
      const tracks = [{ stop: vi.fn() }, { stop: vi.fn() }];
      const cleanupError = new Error("Scanner cleanup failed");
      const stopScanner = vi.fn(() => {
        if (failure === "stop") throw cleanupError;
      });
      const destroyScanner = vi.fn(() => {
        if (failure === "destroy") throw cleanupError;
      });
      const video = {
        srcObject: {
          getTracks: () => tracks,
        } as unknown as MediaStream,
      };

      const dispose = () =>
        disposeCameraSession(
          { destroy: destroyScanner, stop: stopScanner },
          video,
        );
      if (failure === "none") expect(dispose).not.toThrow();
      else expect(dispose).toThrow(cleanupError);

      expect(stopScanner).toHaveBeenCalledOnce();
      expect(destroyScanner).toHaveBeenCalledOnce();
      for (const track of tracks) expect(track.stop).toHaveBeenCalledOnce();
      expect(video.srcObject).toBeNull();
    },
  );
});
