# 🎬 VideoX AI — Professional 100% Offline AI Video Enhancer & 4K Upscaler

[![Android CI & Release APK](https://img.shields.io/badge/Build-Passing-10B981?style=for-the-badge&logo=android)](../../actions)
[![Download Latest APK](https://img.shields.io/badge/Download-Latest_APK_(v1.0.0)-8B5CF6?style=for-the-badge&logo=github)](../../releases/latest)
[![Offline Privacy](https://img.shields.io/badge/Privacy-100%25_Offline_No_Internet-3B82F6?style=for-the-badge&logo=shield)](#privacy--offline-security)
[![Minimum SDK](https://img.shields.io/badge/Android-10%2B_(API_29)-A78BFA?style=for-the-badge&logo=kotlin)](#)

**VideoX AI** (`com.videoxai.enhancer`) হলো একটি সম্পূর্ণ অফলাইন, নেটিভ অ্যান্ড্রয়েড এআই ভিডিও এনহ্যান্সার এবং ৪কে (4K UHD) আপস্কেলার অ্যাপ্লিকেশন। এটি কোনো ক্লাউড সার্ভার বা ইন্টারনেট ছাড়াই আপনার ফোনের প্রসেসর ও জিপিইউ ব্যবহার করে ভিডিও প্রসেস করে।

---

## 📥 ডাউনলোড লিংক (GitHub Download Links)

আপনি নিচের লিংকগুলো থেকে সরাসরি **APK** অথবা **সম্পূর্ণ সোর্স কোড (ZIP)** ডাউনলোড করতে পারবেন:

| ফাইল / প্যাকেজ (Package) | বিবরণ (Description) | ডাউনলোড লিংক (Direct Link) |
| :--- | :--- | :--- |
| **📱 VideoX AI Release APK** | প্রোডাকশন বিল্ড (`v1.0.0`, সব অ্যান্ড্রয়েড ১০+ ফোনের জন্য) | [**⬇️ Download Release APK**](../../releases/latest/download/VideoX-AI-release.apk) |
| **🛠️ VideoX AI Debug APK** | ডেভেলপার ও টেস্টিং বিল্ড (`v1.0.0-debug`) | [**⬇️ Download Debug APK**](../../releases/latest/download/VideoX-AI-debug.apk) |
| **📦 Latest GitHub Release Page** | সকল ভার্সন ও রিলিজ নোটস | [**🔗 Open GitHub Releases**](../../releases/latest) |
| **💻 Full Source Code (ZIP)** | সম্পূর্ণ Android Studio প্রজেক্ট সোর্স কোড | [**⬇️ Download Source ZIP**](../../archive/refs/heads/main.zip) |
| **🌐 AI Studio Live App Link** | ব্রাউজারে প্রিভিউ এবং সরাসরি APK এক্সপোর্ট লিংক | [**🚀 Open in Google AI Studio**](https://ais-pre-7q3en73axqvtrfozzmvwwk-934537935781.asia-east1.run.app) |

> **💡 কিভাবে গিটহাবে (GitHub) পুশ করবেন বা সরাসরি APK ডাউনলোড করবেন?**
> 1. **AI Studio থেকে সরাসরি APK / ZIP ডাউনলোড:** উপরের ডানদিকের **Settings (⚙️) / Export** মেনুতে ক্লিক করে **Download APK** অথবা **Export ZIP** বাটনে ক্লিক করুন।
> 2. **GitHub-এ যুক্ত করা:** উপরের **Push to GitHub** আইকনে ক্লিক করে আপনার GitHub রিপোজিটরিতে কোড পুশ করলেই `.github/workflows/android-release.yml` স্বয়ংক্রিয়ভাবে APK বিল্ড করে উপরের **GitHub Releases** ডাউনলোড লিংকগুলো চালু করে দেবে।

---

## 🧠 অফলাইন এআই মডেল ডাউনলোড লিংক (Optional External `.onnx` Models)

অ্যাপের ভেতরে ৩টি অফলাইন `.onnx` মডেল (`esrgan_lite_sr.onnx`, `rife_lite_flow.onnx`, `ai_denoise_cnn.onnx`) আগে থেকেই যুক্ত করা আছে। এছাড়াও আপনি চাইলে নিচের ওপেন-সোর্স রিপোজিটরি থেকে কাস্টম `.onnx` মডেল ডাউনলোড করে অ্যাপের **Tools → Local ONNX AI Model Manager → Import .onnx** অপশনের মাধ্যমে অফলাইনে ব্যবহার করতে পারবেন:

- **Real-ESRGAN (Super Resolution):** [xinntao/Real-ESRGAN Releases on GitHub](https://github.com/xinntao/Real-ESRGAN/releases)
- **RIFE (Optical Flow Frame Interpolation):** [hzwer/Practical-RIFE on GitHub](https://github.com/hzwer/Practical-RIFE)
- **ONNX Model Zoo (Vision & Super Resolution):** [onnx/models on GitHub](https://github.com/onnx/models)

---

## ✨ প্রধান বৈশিষ্ট্যসমূহ (Core Features)

1. **100% On-Device AI Super Resolution (Up to 4K UHD 3840×2160):**
   - **Standard Upscale**, **AI Upscale (ONNX Neural SR)** এবং **Maximum Quality** মোড।
   - মেমোরি সেফ **Tiled Inference (128px / 192px / 256px)** এবং ওভারল্যাপিং ফেদার ব্লেন্ডিং।
2. **Intelligent 14-Axis Auto Color Engine:**
   - এক ক্লিকে **AUTO COLOR** এবং ১৪টি কালার কন্ট্রোল (Exposure, Brightness, Contrast, Saturation, Vibrance, White Balance, Temperature, Tint, Highlights, Shadows, Gamma, Black Level, White Level)।
   - ৯টি স্টুডিও প্রিসেট: *Natural, Cinematic, Vivid, Warm, Cool, Portrait, Landscape, Film, Professional*।
3. **FPS Conversion & RIFE Optical Flow Interpolation:**
   - **24, 25, 30, 50, 60, 90, 120 FPS** সাপোর্ট এবং সিন-কাট ডিটেকশন (Scene-Cut Detection)।
4. **Video Stabilization & Spatial-Temporal AI Denoising:**
   - ক্যামেরা শেক রিমুভাল, ক্রপ কম্পেনসেশন এবং লো-লাইট নয়েজ রিমুভাল।
5. **Professional Media3 Editor & Hardware MediaCodec Export:**
   - **Before / After Split Slider**, ফ্রেম বাই ফ্রেম প্রিভিউ, **H.264 / H.265 (HEVC) / AV1** এনকোডিং এবং `Movies/VideoX AI` ফোল্ডারে অটোমেটিক গ্যালারি সেভ।
6. **Bilingual Interface (English + বাংলা):**
   - প্রিমিয়াম ডার্ক মোড (`#0B0D14`) এবং লাইট মোড সাপোর্ট।

---

## 🔒 Privacy & Offline Security

- **No Internet Permission:** `AndroidManifest.xml` থেকে `android.permission.INTERNET` সম্পূর্ণভাবে সরিয়ে দেওয়া হয়েছে।
- **Zero Cloud Uploads:** আপনার কোনো ভিডিও কখনোই কোনো সার্ভারে আপলোড হয় না।

---

## 🛠️ কিভাবে নিজে বিল্ড করবেন (Build Instructions)

```bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/VideoX-AI.git
cd VideoX-AI

# 2. Build Debug APK
gradle assembleDebug

# 3. Build Release APK
gradle assembleRelease

# 4. Run Unit & Robolectric Verification Tests
gradle :app:testDebugUnitTest
```

বিল্ড শেষ হলে APK ফাইলটি পাওয়া যাবে:
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
