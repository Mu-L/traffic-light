#include <jni.h>
#include <string.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include "iperf.h"
#include "iperf_api.h"

static JNIEnv *g_env;
static jobject g_callback;
static jmethodID g_method_output;
static void (*g_reporter_cb)(struct iperf_test *);
static char *g_buf;
static size_t g_bufsize, g_last_pos;

static struct iperf_test *g_test;
static pthread_t g_test_thread;
static jmp_buf g_sigend_jmp;
static volatile sig_atomic_t g_jmp_valid = 0;
static volatile sig_atomic_t g_was_cancelled = 0;

static void sigend_handler(int sig) {
    if (g_jmp_valid) {
        longjmp(g_sigend_jmp, 1);
    }
}

static void custom_reporter_callback(struct iperf_test *test) {
    if (g_reporter_cb) {
        g_reporter_cb(test);
    }
    fflush(test->outfile);
    if (g_buf && g_bufsize > g_last_pos) {
        jstring chunk = (*g_env)->NewStringUTF(g_env, g_buf + g_last_pos);
        (*g_env)->CallVoidMethod(g_env, g_callback, g_method_output, chunk);
        (*g_env)->DeleteLocalRef(g_env, chunk);
        g_last_pos = g_bufsize;
    }
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    iperf_catch_sigend(sigend_handler);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_leekleak_iperfintegration_IPerf3Provider_runTestInternal(
    JNIEnv *env,
    jclass clazz,
    jobjectArray arguments,
    jobject callback
) {
    jclass objclass = (*env)->GetObjectClass(env, callback);
    jmethodID method_output = (*env)->GetMethodID(env, objclass, "onOutput", "(Ljava/lang/String;)V");
    jmethodID method_error = (*env)->GetMethodID(env, objclass, "onError", "(Ljava/lang/String;)V");
    jmethodID method_complete = (*env)->GetMethodID(env, objclass, "onComplete", "()V");

    if (method_output == 0 || method_error == 0 || method_complete == 0) {
        return;
    }

    g_env = env;
    g_callback = (*env)->NewGlobalRef(env, callback);
    g_method_output = method_output;
    g_last_pos = 0;
    g_bufsize = 0;
    g_buf = NULL;

    int is_server = 0;
    const int argc = (*env)->GetArrayLength(env, arguments);
    char *argv[argc+1]; // Needs to be offset by 1 for some reason, otherwise crash :/
    for (int i = 0; i < argc; i++) {
        jstring string = (jstring) (*env)->GetObjectArrayElement(env, arguments, i);
        const char *arg_str = (*env)->GetStringUTFChars(env, string, 0);

        if (strcmp(arg_str, "-s") == 0) {
            is_server = 1;
        }
        argv[i+1] = strdup(arg_str);
        (*env)->ReleaseStringUTFChars(env, string, arg_str);
    }

    struct iperf_test *test = iperf_new_test();
    g_test = test;

    if (test == NULL) {
        jstring err = (*env)->NewStringUTF(env, "Failed to create test");
        (*env)->CallVoidMethod(env, callback, method_error, err);
        (*env)->DeleteLocalRef(env, err);
        goto cleanup;
    }

    iperf_defaults(test);
    optind = 0;
    if (iperf_parse_arguments(test, argc+1, argv) > 0) {
        jstring err = (*env)->NewStringUTF(env, iperf_strerror(i_errno));
        (*env)->CallVoidMethod(env, callback, method_error, err);
        (*env)->DeleteLocalRef(env, err);
        iperf_free_test(test);
        goto cleanup;
    }

    FILE *memf = open_memstream(&g_buf, &g_bufsize);
    test->outfile = memf;
    g_reporter_cb = test->reporter_callback;
    test->reporter_callback = custom_reporter_callback;

    g_test_thread = pthread_self();
    g_was_cancelled = 0;
    g_jmp_valid = 1;

    int result;
    if (setjmp(g_sigend_jmp)) {
        iperf_got_sigend(test, SIGTERM);
        g_was_cancelled = 1;
        result = -1;
    } else {
        result = is_server ? iperf_run_server(test) : iperf_run_client(test);;
    }

    if (result < 0 && !g_was_cancelled) {
        jstring err = (*env)->NewStringUTF(env, iperf_strerror(i_errno));
        (*env)->CallVoidMethod(env, callback, method_error, err);
        (*env)->DeleteLocalRef(env, err);
    }

    fflush(memf);
    fclose(memf);
    iperf_free_test(test);
    if (g_buf) free(g_buf);

cleanup:
    (*env)->CallVoidMethod(env, callback, method_complete);
    (*env)->DeleteGlobalRef(env, g_callback);
    g_callback = NULL;
    g_method_output = NULL;
}

JNIEXPORT void JNICALL
Java_com_leekleak_iperfintegration_IPerf3Provider_stopTestInternal(JNIEnv *env, jclass clazz) {
    if (g_test) {
        g_test->done = 1;
        pthread_kill(g_test_thread, SIGUSR1);
    }
}