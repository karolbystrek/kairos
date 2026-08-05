export type CameraScanner = {
  destroy: () => void;
  stop: () => void;
};

type VideoWithStream = Pick<HTMLVideoElement, "srcObject">;

export function disposeCameraSession(
  scanner: CameraScanner | null,
  video: VideoWithStream | null,
): void {
  try {
    scanner?.stop();
  } finally {
    try {
      scanner?.destroy();
    } finally {
      const stream = video?.srcObject;

      if (stream && "getTracks" in stream) {
        for (const track of stream.getTracks()) {
          track.stop();
        }
      }
      if (video) {
        video.srcObject = null;
      }
    }
  }
}
