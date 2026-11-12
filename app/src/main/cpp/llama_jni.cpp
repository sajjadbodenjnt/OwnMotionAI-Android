#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include "llama.h"

#define LOG_TAG "MiraLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static std::mutex g_mutex;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mira_ai_LlamaEngine_nativeLoadModel(JNIEnv *env, jobject, jstring jpath, jint contextSize, jint threads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return JNI_FALSE;

    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }

    llama_backend_init();
    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0; // CPU inference for broad Android compatibility
    g_model = llama_model_load_from_file(path, modelParams);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) {
        LOGE("Could not load GGUF model");
        return JNI_FALSE;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(std::max(512, static_cast<int>(contextSize)));
    ctxParams.n_batch = 512;
    ctxParams.n_threads = std::max(2, static_cast<int>(threads));
    ctxParams.n_threads_batch = std::max(2, static_cast<int>(threads));
    g_ctx = llama_init_from_model(g_model, ctxParams);
    if (!g_ctx) {
        LOGE("Could not create llama context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mira_ai_LlamaEngine_nativeGenerate(JNIEnv *env, jobject, jstring jprompt, jint maxTokens, jfloat temperature) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx) return env->NewStringUTF("মডেল লোড করা নেই।");
    const char *promptChars = env->GetStringUTFChars(jprompt, nullptr);
    if (!promptChars) return env->NewStringUTF("Prompt পড়া যায়নি।");
    std::string prompt = std::string("User: ") + promptChars + "\nAssistant:";
    env->ReleaseStringUTFChars(jprompt, promptChars);

    llama_memory_clear(llama_get_memory(g_ctx), true);
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(prompt.size() + 16);
    int nTokens = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                 tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (nTokens < 0) {
        tokens.resize(static_cast<size_t>(-nTokens));
        nTokens = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                 tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (nTokens <= 0) return env->NewStringUTF("Prompt tokenize করা যায়নি।");
    tokens.resize(static_cast<size_t>(nTokens));

    llama_batch batch = llama_batch_get_one(tokens.data(), nTokens);
    if (llama_decode(g_ctx, batch) != 0) return env->NewStringUTF("Prompt decode ব্যর্থ হয়েছে।");

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(std::max(0.1f, temperature)));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    std::vector<char> piece(8192);
    for (int i = 0; i < std::max(1, static_cast<int>(maxTokens)); ++i) {
        llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        int n = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        if (n > 0) output.append(piece.data(), static_cast<size_t>(n));
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mira_ai_LlamaEngine_nativeRelease(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}
