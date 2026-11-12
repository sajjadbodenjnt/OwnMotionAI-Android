package com.mira.ai;

public final class LlamaEngine {
    static {
        System.loadLibrary("mira-llama");
    }

    private boolean loaded = false;

    public boolean loadModel(String path) {
        loaded = nativeLoadModel(path, 2048, 4);
        return loaded;
    }

    public boolean isLoaded() { return loaded; }

    public String generate(String prompt) {
        if (!loaded) return "প্রথমে একটি GGUF মডেল লোড করুন।";
        String answer = nativeGenerate(prompt, 256, 0.7f);
        return answer == null || answer.isEmpty() ? "মডেল কোনো উত্তর তৈরি করতে পারেনি।" : answer;
    }

    public void close() {
        nativeRelease();
        loaded = false;
    }

    private native boolean nativeLoadModel(String path, int contextSize, int threads);
    private native String nativeGenerate(String prompt, int maxTokens, float temperature);
    private native void nativeRelease();
}
