# 🎬 VideoX AI — Professional 100% Offline AI Video Enhancer & 4K Upscaler

[![Direct APK Download](https://img.shields.io/badge/⬇️_Download_APK-VideoX_AI_v1.0.0_(21_MB)-8B5CF6?style=for-the-badge&logo=android)](https://github.com/mobarak6622/VideoX-AI/raw/main/releases/VideoX-AI-v1.0.0.apk)
[![View APK in Repo](https://img.shields.io/badge/📂_Open_APK_Folder-releases%2FVideoX--AI--v1.0.0.apk-10B981?style=for-the-badge&logo=github)](./releases/VideoX-AI-v1.0.0.apk)
[![Source ZIP](https://img.shields.io/badge/📦_Source_Code-Download_ZIP-3B82F6?style=for-the-badge&logo=github)](https://github.com/mobarak6622/VideoX-AI/archive/refs/heads/main.zip)

**VideoX AI** (`com.videoxai.enhancer`) হলো একটি সম্পূর্ণ অফলাইন, নেটিভ অ্যান্ড্রয়েড এআই ভিডিও এনহ্যান্সার এবং ৪কে (4K UHD) আপস্কেলার অ্যাপ্লিকেশন। এটি কোনো ক্লাউড সার্ভার বা ইন্টারনেট ছাড়াই আপনার ফোনের প্রসেসর ও জিপিইউ ব্যবহার করে ভিডিও প্রসেস করে।

---

## 📥 সরাসরি অ্যাপ ডাউনলোড লিংক (Direct APK Download — No 404!)

এখন আপনার রিপোজিটরির ভেতরেই **`releases/VideoX-AI-v1.0.0.apk` (২১ মেগাবাইট, সাইনড রিলিজ APK)** সরাসরি যুক্ত করে দেওয়া হয়েছে! তাই আলাদা কোনো Release তৈরি হওয়া বা GitHub Action-এর জন্য অপেক্ষা করতে হবে না:

| ফাইল (File) | সাইজ (Size) | সরাসরি ডাউনলোড লিংক (Direct Download Link) |
| :--- | :--- | :--- |
| **📱 VideoX AI Android App (`.apk`)** | **21 MB** (`arm64-v8a` Signed APK) | [**⬇️ Click Here to Download APK (`VideoX-AI-v1.0.0.apk`)**](https://github.com/mobarak6622/VideoX-AI/raw/main/releases/VideoX-AI-v1.0.0.apk) |
| **📂 Repository APK File Page** | **21 MB** (GitHub File Viewer) | [**🔗 Open `releases/VideoX-AI-v1.0.0.apk`**](./releases/VideoX-AI-v1.0.0.apk) |
| **💻 Full Source Code (`.zip`)** | Full Android Studio Project | [**⬇️ Download Source Code ZIP**](https://github.com/mobarak6622/VideoX-AI/archive/refs/heads/main.zip) |

> **🚀 এখন মাত্র ১টি কাজ করুন:**
> AI Studio থেকে প্রজেক্টটি **আরেকবার GitHub (`mobarak6622/VideoX-AI`)-এ Push / Update** করুন।
> Push করার সাথে সাথেই আপনার GitHub রিপোজিটরিতে **`releases/VideoX-AI-v1.0.0.apk`** ফোল্ডারটি চলে আসবে এবং উপরের [**⬇️ Click Here to Download APK**](https://github.com/mobarak6622/VideoX-AI/raw/main/releases/VideoX-AI-v1.0.0.apk) লিংকে ক্লিক করলেই সরাসরি অ্যাপটি আপনার ফোনে ডাউনলোড হয়ে যাবে (আর কখনোই 404 দেখাবে না)!

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
