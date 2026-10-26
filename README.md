# RK Camera AI — Android camera project

Android Studio / GitHub Actions project for Android 8.0+ (API 26+). This is a real CameraX camera app scaffold, not a demo video.

## Current features
- Live camera preview, front/back switch
- Real photo capture and MP4 video recording to MediaStore/Gallery
- Flash toggle where supported
- Requests Android Camera2 video stabilization on the preview request
- GitHub Actions workflow to build a debug APK

## Important stabilization limitation
The current implementation requests hardware EIS through Camera2Interop for the preview pipeline, but the device Camera HAL decides whether it is supported. It does **not** yet implement software gyro-based post-stabilization or guarantee stabilized output on unsupported phones. A software fallback requires a separate motion-estimation/gyro synchronization and video frame-warping pipeline, plus real-device testing. No app can recreate physical OIS or guarantee iPhone-equivalent results on every device.

## Build APK on GitHub
1. Create a new GitHub repository.
2. Upload all files and folders in this project, preserving `.github/workflows/build-apk.yml`.
3. Open **Actions** → **Build RK Camera AI APK** → **Run workflow** (or push to `main`/`master`).
4. Download the `RK-Camera-AI-APK` artifact from the completed run.

## Build locally
Requires JDK 17 and Gradle 8.7; run `gradle assembleDebug` from the project root.

## Next engineering steps for iPhone-like results
- Add a capability probe for supported Camera2 stabilization modes and stream combinations.
- Implement gyro-assisted EIS with crop margin and rolling-shutter compensation where feasible.
- Add multi-frame still HDR, exposure bracketing, alignment and ghost rejection.
- Tune color/tone mapping per sensor and validate with chart/real-scene tests.
- Test on every target device, especially Android 8/low-memory phones.
