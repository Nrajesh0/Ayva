# Footer animation compression · 2.0.32

Only the nine footer animation files and their format reader/export checks change. Android minimum/target versions, device architectures, other assets, fonts, posters, settings, UI layout, scene selection and animation paths are unchanged.

The original 26,347,647 bytes of GIFs are replaced by 18,456,904 bytes of lossless animated WebP: 7,890,743 bytes saved (29.95%). All nine retain their dimensions, 192 frames, per-frame 80/90 ms duration pattern, 16-second loop, infinite repeat, colours and transparency.

`tools/compress_vizag_animations.py` compares every decoded frame to its source GIF. Alpha and visible RGB must match exactly, and every duration must match. It emits the compression report and original-frame SHA-256 fixtures used only by tests. The regular exporter now uses temporary GIF intermediates and packages only WebP.

Android 9+ keeps using ImageDecoder/AnimatedImageDrawable. Android 7–8 retains playback through a small, dependency-free fallback that composites the lossless VP8L subframes with BitmapFactory, preserving WebP blend/disposal flags. Native-rendering tests compare all 1,728 fallback frames against original GIF frame hashes. Existing native playback/pause, themes, rotation, and scrolling tests also pass.

The frame-region, bird-direction, vehicle-direction and initial paraglider checks run against the converted assets. No GIF copies or frame-digest fixtures are packaged into the app.
