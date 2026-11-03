# VideoX AI — Professional 100% Offline AI Video Enhancer & 4K Upscaler

## 1. Architecture & Offline Privacy Guarantee
- **Application ID:** `com.videoxai.enhancer`
- **Minimum SDK:** Android 10 (API 29)
- **Zero-Internet Enforcement:** `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />` and `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />` are enforced in `AndroidManifest.xml`.
- **100% On-Device Pipeline:** All video decoding, AI neural network inference, color grading, denoising, stabilization, optical flow interpolation, and MP4 hardware encoding run locally on the Android device.

## 2. Bundled & Custom ONNX AI Models (`app/src/main/assets/models/`)
VideoX AI ships with 3 pre-bundled offline `.onnx` models (Opset 11, IR v7, NCHW Float32):
1. `esrgan_lite_sr.onnx` — Bundled ESRGAN-compatible Super Resolution & residual detail enhancement CNN.
2. `rife_lite_flow.onnx` — Bundled RIFE-compatible motion refinement & intermediate frame synthesis CNN.
3. `ai_denoise_cnn.onnx` — Bundled spatial-temporal AI denoising & macroblock deblocking CNN.

### Installing Custom External `.onnx` Models Offline
1. Copy any compatible NCHW Float32 `[1, 3, H, W] -> [1, 3, H_out, W_out]` `.onnx` model file (e.g., `realesrgan-x4plus.onnx`, `rife-v4.6.onnx`, `fastdvdnet.onnx`) to your Android device storage.
2. Open **VideoX AI → Tools → Local ONNX AI Model Manager**.
3. Tap **Import .onnx** on the desired task card and select the `.onnx` file via the Android Storage Access Framework (SAF).
4. VideoX AI validates the tensor graph using `OrtEnvironment.createSession(...)`, computes the SHA-256 checksum, and enables on-device benchmarking.

## 3. Build & Test Instructions
- **Compile & Assemble Debug APK:** `gradle assembleDebug`
- **Run Unit & Robolectric Verification Suite:** `gradle :app:testDebugUnitTest`
