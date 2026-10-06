#include <jni.h>
#include <string>
#include <android/log.h>
#include "translator/byte_array_util.h"
#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"
#include "translator/utils.h"
#include "third_party/cld2/public/compact_lang_det.h"
#include <string>
using namespace marian::bergamot;

#include <unordered_map>
#include <mutex>

// Java strings cross JNI as real UTF-8 and back. GetStringUTFChars hands over *modified*
// UTF-8, which writes a character outside the Basic Multilingual Plane -- an emoji, most
// often, in a text message -- as two three-byte halves that no tokenizer reads as one letter,
// and NewStringUTF expects the same modified form back. Going through UTF-16 both ways keeps
// such characters whole, and a stray half becomes U+FFFD rather than undefined bytes.
static std::string toUtf8(JNIEnv *env, jstring s) {
    const jsize len = env->GetStringLength(s);
    const jchar *u = env->GetStringChars(s, nullptr);
    std::string out;
    out.reserve(len * 3);
    for (jsize i = 0; i < len; i++) {
        uint32_t c = u[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < len && u[i + 1] >= 0xDC00 && u[i + 1] <= 0xDFFF) {
            c = 0x10000 + ((c - 0xD800) << 10) + (u[i + 1] - 0xDC00);
            i++;
        } else if (c >= 0xD800 && c <= 0xDFFF) {
            c = 0xFFFD;
        }
        if (c < 0x80) {
            out += (char) c;
        } else if (c < 0x800) {
            out += (char) (0xC0 | (c >> 6));
            out += (char) (0x80 | (c & 0x3F));
        } else if (c < 0x10000) {
            out += (char) (0xE0 | (c >> 12));
            out += (char) (0x80 | ((c >> 6) & 0x3F));
            out += (char) (0x80 | (c & 0x3F));
        } else {
            out += (char) (0xF0 | (c >> 18));
            out += (char) (0x80 | ((c >> 12) & 0x3F));
            out += (char) (0x80 | ((c >> 6) & 0x3F));
            out += (char) (0x80 | (c & 0x3F));
        }
    }
    env->ReleaseStringChars(s, u);
    return out;
}

static jstring fromUtf8(JNIEnv *env, const std::string &s) {
    std::u16string out;
    out.reserve(s.size());
    const auto *b = reinterpret_cast<const unsigned char *>(s.data());
    const size_t n = s.size();
    size_t i = 0;
    while (i < n) {
        uint32_t c = b[i];
        size_t extra = c < 0x80 ? 0 : (c >> 5) == 0x6 ? 1 : (c >> 4) == 0xE ? 2 : (c >> 3) == 0x1E ? 3 : 9;
        bool ok = extra <= 3 && i + extra < n;
        if (ok && extra > 0) {
            c &= (0x3Fu >> extra);
            for (size_t k = 1; k <= extra; k++) {
                if ((b[i + k] & 0xC0) != 0x80) { ok = false; break; }
                c = (c << 6) | (b[i + k] & 0x3F);
            }
        }
        if (!ok) {
            out += (char16_t) 0xFFFD;
            i++;
            continue;
        }
        i += extra + 1;
        if (c >= 0x10000) {
            c -= 0x10000;
            out += (char16_t) (0xD800 + (c >> 10));
            out += (char16_t) (0xDC00 + (c & 0x3FF));
        } else {
            out += (char16_t) c;
        }
    }
    return env->NewString(reinterpret_cast<const jchar *>(out.data()), (jsize) out.size());
}
static std::unordered_map<std::string, std::shared_ptr<TranslationModel>> model_cache;
static std::unique_ptr<BlockingService> global_service = nullptr;
static std::mutex service_mutex;
static std::mutex translation_mutex;

void initializeService() {
    std::lock_guard<std::mutex> lock(service_mutex);

    if (global_service == nullptr) {
        BlockingService::Config blockingConfig;
        blockingConfig.cacheSize = 256;
        blockingConfig.logger.level = "off";
        global_service = std::make_unique<BlockingService>(blockingConfig);
    }
}

void loadModelIntoCache(const std::string& cfg, const std::string& key) {
    std::lock_guard<std::mutex> lock(service_mutex);

    auto validate = true;
    auto pathsDir = "";

    if (model_cache.find(key) == model_cache.end()) {
        auto options = parseOptionsFromString(cfg, validate, pathsDir);
        model_cache[key] = std::make_shared<TranslationModel>(options);
    }
}

std::vector<std::string> translateMultiple(std::vector<std::string> &&inputs, const char *key) {
    initializeService();

    std::string key_str(key);

    // Assume model is already loaded in cache
    std::shared_ptr<TranslationModel> model = model_cache[key_str];

    std::vector<ResponseOptions> responseOptions;
    responseOptions.reserve(inputs.size());
    for (size_t i = 0; i < inputs.size(); ++i) {
        ResponseOptions opts;
        opts.HTML = false;
        opts.qualityScores = false;
        opts.alignment = false;
        opts.sentenceMappings = false;
        responseOptions.emplace_back(opts);
    }

    std::lock_guard<std::mutex> translation_lock(translation_mutex);
    std::vector<Response> responses = global_service->translateMultiple(model, std::move(inputs), responseOptions);

    std::vector<std::string> results;
    results.reserve(responses.size());
    for (const auto &response: responses) {
        results.push_back(response.target.text);
    }

    return results;
}

std::vector<std::string> pivotMultiple(const char *firstKey, const char *secondKey, std::vector<std::string> &&inputs) {
    initializeService();

    std::string first_key_str(firstKey);
    std::string second_key_str(secondKey);

    // Assume models are already loaded in cache
    std::shared_ptr<TranslationModel> firstModel = model_cache[first_key_str];
    std::shared_ptr<TranslationModel> secondModel = model_cache[second_key_str];

    std::vector<ResponseOptions> responseOptions;
    responseOptions.reserve(inputs.size());
    for (size_t i = 0; i < inputs.size(); ++i) {
        ResponseOptions opts;
        opts.HTML = false;
        opts.qualityScores = false;
        opts.alignment = false;
        opts.sentenceMappings = false;
        responseOptions.emplace_back(opts);
    }

    std::lock_guard<std::mutex> translation_lock(translation_mutex);
    std::vector<Response> responses = global_service->pivotMultiple(firstModel, secondModel, std::move(inputs), responseOptions);

    std::vector<std::string> results;
    results.reserve(responses.size());
    for (const auto &response: responses) {
        results.push_back(response.target.text);
    }

    return results;
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_dev_davidv_bergamot_NativeLib_initializeService(
        JNIEnv* env,
        jobject /* this */) {
    try {
        initializeService();
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_dev_davidv_bergamot_NativeLib_loadModelIntoCache(
        JNIEnv* env,
        jobject /* this */,
        jstring cfg,
        jstring key) {

    const char* c_cfg = env->GetStringUTFChars(cfg, nullptr);
    const char* c_key = env->GetStringUTFChars(key, nullptr);

    try {
        std::string cfg_str(c_cfg);
        std::string key_str(c_key);
        loadModelIntoCache(cfg_str, key_str);
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(cfg, c_cfg);
    env->ReleaseStringUTFChars(key, c_key);
}

// Cleanup function to be called when the library is unloaded
extern "C" __attribute__((visibility("default"))) JNIEXPORT jobjectArray JNICALL
Java_dev_davidv_bergamot_NativeLib_translateMultiple(
        JNIEnv *env,
        jobject /* this */,
        jobjectArray inputs,
        jstring key) {

    const char *c_key = env->GetStringUTFChars(key, nullptr);

    jsize inputCount = env->GetArrayLength(inputs);
    std::vector<std::string> cpp_inputs;
    cpp_inputs.reserve(inputCount);

    for (jsize i = 0; i < inputCount; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(inputs, i);
        cpp_inputs.emplace_back(toUtf8(env, jstr));
        env->DeleteLocalRef(jstr);
    }

    jobjectArray result = nullptr;
    try {
        std::vector<std::string> translations = translateMultiple(std::move(cpp_inputs), c_key);

        jclass stringClass = env->FindClass("java/lang/String");
        result = env->NewObjectArray((jsize) translations.size(), stringClass, nullptr);

        for (size_t i = 0; i < translations.size(); ++i) {
            jstring jstr = fromUtf8(env, translations[i]);
            env->SetObjectArrayElement(result, (jsize) i, jstr);
            env->DeleteLocalRef(jstr);
        }
    } catch (const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(key, c_key);

    return result;
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT jobjectArray JNICALL
Java_dev_davidv_bergamot_NativeLib_pivotMultiple(
        JNIEnv *env,
        jobject /* this */,
        jstring firstKey,
        jstring secondKey,
        jobjectArray inputs) {

    const char *c_firstKey = env->GetStringUTFChars(firstKey, nullptr);
    const char *c_secondKey = env->GetStringUTFChars(secondKey, nullptr);

    jsize inputCount = env->GetArrayLength(inputs);
    std::vector<std::string> cpp_inputs;
    cpp_inputs.reserve(inputCount);

    for (jsize i = 0; i < inputCount; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(inputs, i);
        cpp_inputs.emplace_back(toUtf8(env, jstr));
        env->DeleteLocalRef(jstr);
    }

    jobjectArray result = nullptr;
    try {
        std::vector<std::string> translations = pivotMultiple(c_firstKey, c_secondKey, std::move(cpp_inputs));

        jclass stringClass = env->FindClass("java/lang/String");
        result = env->NewObjectArray((jsize) translations.size(), stringClass, nullptr);

        for (size_t i = 0; i < translations.size(); ++i) {
            jstring jstr = fromUtf8(env, translations[i]);
            env->SetObjectArrayElement(result, (jsize) i, jstr);
            env->DeleteLocalRef(jstr);
        }
    } catch (const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(firstKey, c_firstKey);
    env->ReleaseStringUTFChars(secondKey, c_secondKey);

    return result;
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_dev_davidv_bergamot_NativeLib_cleanup(JNIEnv* env, jobject /* this */) {
    std::lock_guard<std::mutex> lock(service_mutex);
    global_service.reset();
    model_cache.clear();
}


struct DetectionResult {
    std::string language;
    bool isReliable;
    int confidence;
};

DetectionResult detectLanguage(const char *text, const char *language_hint = nullptr) {
    bool is_reliable;
    int text_bytes = (int) strlen(text);
    bool is_plain_text = true;

    CLD2::Language hint_lang = CLD2::UNKNOWN_LANGUAGE;
    if (language_hint != nullptr && strlen(language_hint) > 0) {
        hint_lang = CLD2::GetLanguageFromName(language_hint);
    }

    CLD2::CLDHints hints = {nullptr, nullptr, 0, hint_lang};
    CLD2::Language language3[3];
    int percent3[3];
    double normalized_score3[3];
    int chunk_bytes;

    CLD2::ExtDetectLanguageSummary(
            text,
            text_bytes,
            is_plain_text,
            &hints,
            0,
            language3,
            percent3,
            normalized_score3,
            nullptr,
            &chunk_bytes,
            &is_reliable
    );

    /*
    __android_log_print(ANDROID_LOG_DEBUG, "Bergamot", "Language detection results:");
    for (int i = 0; i < 3; i++) {
        __android_log_print(ANDROID_LOG_DEBUG, "Bergamot", "  %d: %s - %d%% (score: %.3f)",
                            i + 1,
                            CLD2::LanguageCode(language3[i]),
                            percent3[i],
                            normalized_score3[i]);
    }
    */

    return DetectionResult{
            CLD2::LanguageCode(language3[0]),
            is_reliable,
            percent3[0]
    };
}


extern "C" __attribute__((visibility("default"))) JNIEXPORT jobject JNICALL
Java_dev_davidv_bergamot_LangDetect_detectLanguage(
        JNIEnv *env,
        jobject /* this */,
        jstring text,
        jstring hint) {

    const std::string text_utf8 = toUtf8(env, text);
    const char *c_text = text_utf8.c_str();
    const char *c_hint = nullptr;
    if (hint != nullptr) {
        c_hint = env->GetStringUTFChars(hint, nullptr);
    }

    // Find the Result class and its constructor
    jclass resultClass = env->FindClass("dev/davidv/bergamot/DetectionResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>",
                                             "(Ljava/lang/String;ZI)V");

    try {
        DetectionResult result = detectLanguage(c_text, c_hint);

        // Convert C++ string to jstring
        jstring j_language = env->NewStringUTF(result.language.c_str());

        // Create new Result object
        jobject j_result = env->NewObject(resultClass, constructor,
                                          j_language,
                                          result.isReliable,
                                          result.confidence);

        if (c_hint != nullptr) {
            env->ReleaseStringUTFChars(hint, c_hint);
        }
        return j_result;

    } catch(const std::exception &e) {
        if (c_hint != nullptr) {
            env->ReleaseStringUTFChars(hint, c_hint);
        }
        // Handle error
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
        return nullptr;
    }
}
