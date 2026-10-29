# Vera Downloader v1.0.0

First public release of Vera Downloader, a native Android downloader for TikTok, YouTube,
Instagram, and Facebook.

## Highlights

- Download video, audio, or images from a pasted or shared link.
- Preview cover, title, author, duration, and available formats.
- Multi-image carousel selection with thumbnails and confirmation.
- TikTok photo posts: generated MP4 slideshow, M4A audio, and images.
- YouTube DASH video/audio with local muxing.
- Instagram public videos/Reels with MP4, M4A, and image options.
- Facebook share links for images, videos, and extracted M4A audio.
- 30 languages with RTL support.
- Light, dark, and system themes.
- Download history with idempotency, retry, cancellation, and per-image thumbnails.
- Floating TikTok shortcut with a bottom **✕ REMOVE** bar.
- WorkManager downloads with progress notifications and resumable range transfers.
- No ads, analytics, accounts, or subscriptions.

## Install

Download the APK below and open it on Android 8.0 or newer. Allow install from the browser
or file manager if Android asks. The APK is signed; Play Protect may warn because it is not
distributed through Google Play.

- `vera-downloader-v1.0.0.apk`
- Size: 12,285,101 bytes
- SHA-256: `9a6c1890f4ccb9b0a9ebbdc63029114570279a763325514b08673a9a8da48050`

ADB:

```bash
adb install -r vera-downloader-v1.0.0.apk
```

## Notes

- Only public content can be downloaded reliably.
- YouTube may still return 403 for age/region-restricted or login-gated videos.
- Instagram and Facebook can require login for private content.
- The app does not bypass DRM or private-content restrictions.
- Use only content you own or have permission to save.

See the [README](https://github.com/JohnSalazar/vera-downloader#readme) for full
documentation, architecture, privacy notes, and troubleshooting.
