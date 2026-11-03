# 🎬 VideoX AI — Professional 100% Offline AI Video Enhancer & 4K Upscaler

[![Build & Release APK](https://github.com/mobarak6622/VideoX-AI/actions/workflows/android-release.yml/badge.svg)](https://github.com/mobarak6622/VideoX-AI/actions/workflows/android-release.yml)
[![Download Latest APK](https://img.shields.io/badge/Download-VideoX_AI_APK_(v1.0.0)-8B5CF6?style=for-the-badge&logo=android)](https://github.com/mobarak6622/VideoX-AI/releases/latest/download/VideoX-AI-release.apk)
[![GitHub Releases](https://img.shields.io/badge/GitHub-All_Releases-10B981?style=for-the-badge&logo=github)](https://github.com/mobarak6622/VideoX-AI/releases)
[![Source ZIP](https://img.shields.io/badge/Source_Code-Download_ZIP-3B82F6?style=for-the-badge&logo=github)](https://github.com/mobarak6622/VideoX-AI/archive/refs/heads/main.zip)

**VideoX AI** (`com.videoxai.enhancer`) হলো একটি সম্পূর্ণ অফলাইন, নেটিভ অ্যান্ড্রয়েড এআই ভিডিও এনহ্যান্সার এবং ৪কে (4K UHD) আপস্কেলার অ্যাপ্লিকেশন। এটি কোনো ক্লাউড সার্ভার বা ইন্টারনেট ছাড়াই আপনার ফোনের প্রসেসর ও জিপিইউ ব্যবহার করে ভিডিও প্রসেস করে।

---

## 📥 সরাসরি অ্যাপ ডাউনলোড লিংক (GitHub Direct Download Links — `mobarak6622/VideoX-AI`)

আপনার রিপোজিটরি (**`github.com/mobarak6622/VideoX-AI`**)-এর সরাসরি ডাউনলোড লিংক নিচে দেওয়া হলো:

| ফাইল / প্যাকেজ (Package) | বিবরণ (Description) | সরাসরি ডাউনলোড লিংক (Direct Link) |
| :--- | :--- | :--- |
| **📱 VideoX AI Release APK** | মূল অ্যান্ড্রয়েড অ্যাপ (`v1.0.0`, সব Android 10+ ফোনের জন্য) | [**⬇️ Download VideoX-AI-release.apk**](https://github.com/mobarak6622/VideoX-AI/releases/latest/download/VideoX-AI-release.apk) |
| **🛠️ VideoX AI Debug APK** | ডেভেলপার ও টেস্টিং APK (`v1.0.0-debug`) | [**⬇️ Download VideoX-AI-debug.apk**](https://github.com/mobarak6622/VideoX-AI/releases/latest/download/VideoX-AI-debug.apk) |
| **📦 GitHub Releases Page** | রিলিজ পেজ (সব ভার্সন ও APK তালিকা) | [**🔗 Open Releases Page**](https://github.com/mobarak6622/VideoX-AI/releases) |
| **⚙️ GitHub Actions Build** | অটোমেটিক APK বিল্ড স্ট্যাটাস ও আর্টিফ্যাক্ট ডাউনলোড | [**⚡ View GitHub Actions Build**](https://github.com/mobarak6622/VideoX-AI/actions) |
| **💻 Full Source Code (ZIP)** | সম্পূর্ণ Android Studio সোর্স কোড (ZIP) | [**⬇️ Download Source ZIP**](https://github.com/mobarak6622/VideoX-AI/archive/refs/heads/main.zip) |

> **📌 গুরুত্বপূর্ণ নোট (কেন আগে `No releases published` বা `❌` দেখাচ্ছিল?):**
> AI Studio-র নিজস্ব কন্টেইনারে অভ্যন্তরীণ Gradle ভার্সন (`AGP 9.1.1`) ব্যবহৃত হয় যা পাবলিক GitHub সার্ভারে থাকে না। এখন `.github/workflows/android-release.yml` ফাইলটি আপডেট করে দেওয়া হয়েছে যাতে GitHub-এ Push করার সাথে সাথেই এটি পাবলিক Google Maven (`AGP 8.7.3`) ও স্বয়ংক্রিয় Keystore তৈরি করে **২-৩ মিনিটের মধ্যে `VideoX-AI-release.apk` রিলিজ পেজে পাবলিশ করে দেয়!**
>
> **এখন করণীয়:**
> 1. AI Studio থেকে আরেকবার **GitHub-এ Push / Sync** করুন।
> 2. আপনার রিপোজিটরির [**Actions ট্যাবে**](https://github.com/mobarak6622/VideoX-AI/actions) গেলে দেখবেন `Build & Release VideoX AI APK` সবুজ (`✅`) হয়ে সম্পন্ন হয়েছে।
> 3. এরপর উপরের [**⬇️ Download VideoX-AI-release.apk**](https://github.com/mobarak6622/VideoX-AI/releases/latest/download/VideoX-AI-release.apk) লিংকে ক্লিক করলেই সরাসরি অ্যাপটি আপনার ফোনে ডাউনলোড হয়ে যাবে!

---

## 🧠 ভেরিফাইড ওপেন-সোর্স এআই মডেল ডাউনলোড লিংক (Optional External `.onnx` Models)

অ্যাপের ভেতরে ৩টি অফলাইন `.onnx` মডেল (`esrgan_lite_sr.onnx`, `rife_lite_flow.onnx`, `ai_denoise_cnn.onnx`) আগে থেকেই যুক্ত করা আছে। এছাড়াও আপনি নিচের পাবলিক GitHub লিংকগুলো থেকে অতিরিক্ত মডেল ডাউনলোড করে অ্যাপের **Tools → Local ONNX AI Model Manager → Import .onnx** অপশনের মাধ্যমে অফলাইনে ব্যবহার করতে পারবেন:

| মডেল (AI Model) | কাজ (Function) | অফিশিয়াল GitHub লিংক |
| :--- | :--- | :--- |
| **Real-ESRGAN** | 4K AI Super Resolution | [https://github.com/xinntao/Real-ESRGAN/releases](https://github.com/xinntao/Real-ESRGAN/releases) |
| **Practical-RIFE** | 60/120 FPS Optical Flow Interpolation | [https://github.com/hzwer/Practical-RIFE](https://github.com/hzwer/Practical-RIFE) |
| **ONNX Model Zoo** | Super Resolution & Vision ONNX Models | [https://github.com/onnx/models](https://github.com/onnx/models) |

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
