package com.example.core.common

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    BENGALI("bn", "Bengali", "বাংলা")
}

data class BilingualStrings(
    val appName: String,
    val tagline: String,
    val offlineBadge: String,
    val navHome: String,
    val navProjects: String,
    val navTools: String,
    val navSettings: String,
    val importVideoTitle: String,
    val importFromPhotoPicker: String,
    val importFromStorage: String,
    val generateTestVideo: String,
    val recentProjectsTitle: String,
    val noProjectsYet: String,
    val quickToolsTitle: String,
    val toolAiUpscaler: String,
    val toolAutoColor: String,
    val toolFpsConverter: String,
    val toolStabilizer: String,
    val toolDenoiser: String,
    val toolSharpen: String,
    val toolModelManager: String,
    val toolDeviceAnalyzer: String,
    val videoAnalysisTitle: String,
    val openInEditor: String,
    val beforeLabel: String,
    val afterLabel: String,
    val startExport: String,
    val processingTitle: String,
    val exportSuccessTitle: String,
    val privacyGuaranteeTitle: String,
    val privacyGuaranteeBody: String
)

object AppStrings {
    private val english = BilingualStrings(
        appName = "VideoX AI",
        tagline = "Professional 100% Offline AI Video Enhancer & 4K Upscaler",
        offlineBadge = "100% On-Device • Zero Cloud • No Internet Permission",
        navHome = "Home",
        navProjects = "Projects",
        navTools = "Studio Tools",
        navSettings = "Settings",
        importVideoTitle = "Import & Enhance Video",
        importFromPhotoPicker = "Gallery / Photo Picker",
        importFromStorage = "Device Storage (SAF)",
        generateTestVideo = "Generate Real MP4 Test Sample",
        recentProjectsTitle = "Recent Projects & Exports",
        noProjectsYet = "No video projects yet. Import a video from your phone or generate a real MP4 test clip to begin.",
        quickToolsTitle = "On-Device AI & Enhancement Modules",
        toolAiUpscaler = "AI Super Resolution & 4K Upscaler",
        toolAutoColor = "Intelligent Auto Color & 14-Axis Grading",
        toolFpsConverter = "FPS Converter & Optical Flow RIFE",
        toolStabilizer = "Motion Trajectory Video Stabilizer",
        toolDenoiser = "Spatial-Temporal AI Video Denoiser",
        toolSharpen = "Smart Sharpening & Detail Recovery",
        toolModelManager = "Local ONNX AI Model Manager",
        toolDeviceAnalyzer = "Hardware & Thermal Capability Analyzer",
        videoAnalysisTitle = "Video Stream Telemetry & Metadata",
        openInEditor = "Open in Studio Editor",
        beforeLabel = "ORIGINAL",
        afterLabel = "ENHANCED",
        startExport = "Render & Export MP4",
        processingTitle = "On-Device Frame Pipeline Active",
        exportSuccessTitle = "Video Verified & Saved to Gallery",
        privacyGuaranteeTitle = "Strict Offline Privacy Architecture",
        privacyGuaranteeBody = "VideoX AI processes every video frame locally on your Android CPU/GPU/NPU. The INTERNET permission is explicitly stripped from the application manifest. Your videos never leave your device."
    )

    private val bengali = BilingualStrings(
        appName = "VideoX AI",
        tagline = "প্রফেশনাল ১০০% অফলাইন এআই ভিডিও এনহ্যান্সার ও ৪কে আপস্কেলার",
        offlineBadge = "১০০% অন-ডিভাইস • ক্লাউড মুক্ত • ইন্টারনেট পারমিশন নেই",
        navHome = "হোম",
        navProjects = "প্রজেক্টস",
        navTools = "স্টুডিও টুলস",
        navSettings = "সেটিংস",
        importVideoTitle = "ভিডিও ইমপোর্ট এবং এনহ্যান্স করুন",
        importFromPhotoPicker = "গ্যালারি / ফটো পিকার",
        importFromStorage = "ডিভাইস স্টোরেজ (SAF)",
        generateTestVideo = "রিয়েল MP4 টেস্ট ভিডিও তৈরি করুন",
        recentProjectsTitle = "সাম্প্রতিক প্রজেক্ট ও এক্সপোর্ট",
        noProjectsYet = "এখনও কোনো ভিডিও প্রজেক্ট নেই। আপনার ফোন থেকে ভিডিও ইমপোর্ট করুন অথবা টেস্ট MP4 ভিডিও তৈরি করুন।",
        quickToolsTitle = "অন-ডিভাইস এআই এবং ভিডিও প্রসেসিং টুলস",
        toolAiUpscaler = "এআই সুপার রেজোলিউশন ও ৪কে আপস্কেলার",
        toolAutoColor = "অটো কালার ও ১৪-প্যারামিটার কালার গ্রেডিং",
        toolFpsConverter = "FPS কনভার্টার এবং এআই ফ্রেম ইন্টারপোলেশন",
        toolStabilizer = "ভিডিও স্ট্যাবিলাইজেশন ইঞ্জিন",
        toolDenoiser = "এআই ভিডিও ডিনয়েজিং (নয়েজ রিমুভাল)",
        toolSharpen = "স্মার্ট শার্পেনিং ও ডিটেইল রিকভারি",
        toolModelManager = "লোকাল ONNX এআই মডেল ম্যানেজার",
        toolDeviceAnalyzer = "হার্ডওয়্যার ও থার্মাল ক্যাপাবিলিটি অ্যানালাইজার",
        videoAnalysisTitle = "ভিডিও মেটাডেটা ও কোডেক বিশ্লেষণ",
        openInEditor = "স্টুডিও এডিটরে খুলুন",
        beforeLabel = "আসল",
        afterLabel = "এনহ্যান্সড",
        startExport = "MP4 রেন্ডার ও এক্সপোর্ট করুন",
        processingTitle = "অন-ডিভাইস ফ্রেম প্রসেসিং চলছে",
        exportSuccessTitle = "ভিডিও যাচাই সম্পন্ন ও গ্যালারিতে সেভ হয়েছে",
        privacyGuaranteeTitle = "সম্পূর্ণ অফলাইন প্রাইভেসি সুরক্ষা",
        privacyGuaranteeBody = "VideoX AI আপনার ফোনের নিজস্ব প্রসেসর ও জিপিইউ ব্যবহার করে সব ভিডিও প্রসেস করে। অ্যাপে কোনো ইন্টারনেট পারমিশন নেই এবং কোনো ভিডিও কখনোই সার্ভারে আপলোড হয় না।"
    )

    fun get(language: AppLanguage): BilingualStrings = when (language) {
        AppLanguage.ENGLISH -> english
        AppLanguage.BENGALI -> bengali
    }
}
