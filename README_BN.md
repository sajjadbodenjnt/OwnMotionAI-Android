# OwnMotion AI — Android

নিজস্ব Android ভিডিও-তৈরির প্রকল্প। লক্ষ্য: ফোনে লোকাল প্রসেসিং, গোপনীয়তা এবং ধাপে ধাপে generative video research।

## বর্তমান সংস্করণ 0.1.0 কী করে
- গ্যালারি/ফাইল থেকে ছবি নির্বাচন
- বাংলা/ইংরেজি prompt বা caption লেখা
- 720×720, 15 FPS, 5 সেকেন্ডের MP4 তৈরি
- ছবিতে ধীর pan/zoom (Ken Burns effect) এবং caption বসানো
- ভিডিও অ্যাপের private Movies/OwnMotion ফোল্ডারে রাখা

**গুরুত্বপূর্ণ সীমাবদ্ধতা:** এটি এখনো text-to-video বা true image-to-video generative AI নয়। এটি ছবির ফ্রেম রেন্ডার করে ভিডিও বানায়। মানুষের চোখের পলক, হাঁটা, দৌড়ানো, লাফানো বা নতুন দৃশ্য তৈরি করে না। এই কাজের জন্য আলাদা প্রশিক্ষিত video diffusion/transformer model, model weights, quantization এবং memory/compute optimization লাগবে। ফোনে CPU-only inference খুব ধীর হতে পারে। কোনো cloud/Gemini Omni API ব্যবহার করা হয়নি।

## Build (Termux)
Android Gradle Plugin 8.6.1-এর জন্য JDK 17 এবং Android SDK Platform 35 প্রয়োজন। Termux-এ আগে তোমার SDK/JDK সেটআপ যাচাই করো।

```bash
pkg update
pkg install git openjdk-17 gradle
git clone https://github.com/sajjadbodenjnt/OwnMotionAI-Android.git
cd OwnMotionAI-Android
export JAVA_HOME=$PREFIX/opt/openjdk
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
gradle --version
```

SDK Platform 35 এবং Build Tools ইনস্টল না থাকলে Android SDK Manager দিয়ে যোগ করতে হবে। তারপর:

```bash
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
gradle assembleDebug
```

APK (build সফল হলে):
`app/build/outputs/apk/debug/app-debug.apk`

## CPU / hardware note
ভিডিওর frame drawing অ্যাপের CPU-তে হয়। Android-এর MediaCodec AVC encoder ডিভাইসের hardware/software codec বেছে নিতে পারে; তাই এটি strict software-only H.264 encoding guarantee করে না। AI inference-এর কোনো GPU delegate নেই, কারণ এই starter release-এ ML model সংযুক্তই করা হয়নি।

## Roadmap
1. Reliable export pipeline, progress/cancel, gallery save/share
2. Model adapter interface এবং CPU inference benchmark
3. ছোট quantized motion model-এর গবেষণা ও Android integration
4. Temporal consistency, identity preservation, pose/motion conditioning
5. Model license, safety filters, device compatibility এবং quality evaluation

## Privacy
এই সংস্করণে কোনো account, analytics, network API বা cloud upload নেই। ছবি ও ভিডিও ডিভাইসেই থাকে।
