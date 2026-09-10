/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

#include <jni.h>

#include <zinnia.h>

namespace {

/**
 * Strokes are passed as one flat int array to keep the JNI surface small:
 *   [strokeCount, pointCount0, x, y, x, y, ..., pointCount1, x, y, ...]
 */
jobjectArray classify(JNIEnv *env, zinnia::Recognizer *recognizer,
                      jint width, jint height, jintArray strokes, jint nbest) {
    const jsize length = env->GetArrayLength(strokes);
    if (length < 1 || width <= 0 || height <= 0 || nbest <= 0) return nullptr;
    jint *data = env->GetIntArrayElements(strokes, nullptr);
    if (data == nullptr) return nullptr;

    zinnia::Character *character = zinnia::Character::create();
    character->clear();
    character->set_width(width);
    character->set_height(height);

    jsize cursor = 0;
    const jint strokeCount = data[cursor++];
    bool malformed = strokeCount < 0;
    for (jint s = 0; s < strokeCount; ++s) {
        if (cursor >= length) { malformed = true; break; }
        const jint pointCount = data[cursor++];
        if (pointCount < 0 || pointCount > (length - cursor) / 2) { malformed = true; break; }
        for (jint p = 0; p < pointCount; ++p) {
            const jint x = data[cursor++];
            const jint y = data[cursor++];
            character->add(s, x, y);
        }
    }
    env->ReleaseIntArrayElements(strokes, data, JNI_ABORT);
    if (malformed || character->strokes_size() == 0) {
        delete character;
        return nullptr;
    }

    zinnia::Result *result = recognizer->classify(*character, nbest);
    delete character;
    if (result == nullptr) return nullptr;

    const auto size = static_cast<jsize>(result->size());
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(size, stringClass, nullptr);
    for (jsize i = 0; i < size; ++i) {
        jstring value = env->NewStringUTF(result->value(i));
        env->SetObjectArrayElement(array, i, value);
        env->DeleteLocalRef(value);
    }
    delete result;
    return array;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_org_fcitx_fcitx5_android_data_handwriting_HandwritingRecognizer_nativeOpen(
        JNIEnv *env, jclass, jstring model) {
    const char *path = env->GetStringUTFChars(model, nullptr);
    zinnia::Recognizer *recognizer = zinnia::Recognizer::create();
    const bool opened = recognizer->open(path);
    env->ReleaseStringUTFChars(model, path);
    if (!opened) {
        delete recognizer;
        return 0;
    }
    return reinterpret_cast<jlong>(recognizer);
}

extern "C" JNIEXPORT void JNICALL
Java_org_fcitx_fcitx5_android_data_handwriting_HandwritingRecognizer_nativeClose(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    delete reinterpret_cast<zinnia::Recognizer *>(handle);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_fcitx_fcitx5_android_data_handwriting_HandwritingRecognizer_nativeClassify(
        JNIEnv *env, jclass, jlong handle, jint width, jint height,
        jintArray strokes, jint nbest) {
    if (handle == 0) return nullptr;
    auto *recognizer = reinterpret_cast<zinnia::Recognizer *>(handle);
    return classify(env, recognizer, width, height, strokes, nbest);
}
