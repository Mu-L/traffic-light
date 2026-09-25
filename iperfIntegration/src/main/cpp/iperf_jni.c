#include <jni.h>
#include <string.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include "iperf.h"
#include "iperf_api.h"
#include "iperf_util.h"

static JNIEnv *g_env;
static jobject g_callback;
static jmethodID g_method_output;
static void (*g_stats_cb)(struct iperf_test *);

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

static void custom_stats_callback(struct iperf_test *test) {
    g_stats_cb(test);

    struct iperf_stream *sp;

    cJSON *json_interval = cJSON_CreateArray();
    SLIST_FOREACH(sp, &test->streams, streams) {
        struct iperf_stream_result *rp = sp->result;
        struct iperf_interval_results *irp = TAILQ_LAST(&rp->interval_results, irlisthead);
        if (irp == NULL) continue;

        cJSON *entry;
        if (g_test->protocol->id == Pudp) {
            entry = iperf_json_printf(
                    "protocol: %s  socket: %d  "
                    "bytes_transferred: %d  interval_start_time: %d  interval_end_time: %d  "
                    "interval_duration: %f  interval_packet_count: %d  interval_outoforder_packets: %d  "
                    "interval_cnt_error: %d  packet_count: %d  jitter: %f  outoforder_packets: %d  "
                    "cnt_error: %d  omitted: %b",
                    "udp", (int64_t) sp->socket,
                    (int64_t) irp->bytes_transferred,
                    (int64_t) irp->interval_start_time.secs,
                    (int64_t) irp->interval_end_time.secs,
                    (double) irp->interval_duration,
                    (int64_t) irp->interval_packet_count,
                    (int64_t) irp->interval_outoforder_packets,
                    (int64_t) irp->interval_cnt_error,
                    (int64_t) irp->packet_count,
                    (double) irp->jitter,
                    (int64_t) irp->outoforder_packets,
                    (int64_t) irp->cnt_error,
                    (int) irp->omitted);
        } else {
            entry = iperf_json_printf(
                    "protocol: %s  socket: %d  "
                    "bytes_transferred: %d  interval_start_time: %d  interval_end_time: %d  "
                    "interval_duration: %f  omitted: %b",
                    "tcp", (int64_t) sp->socket,
                    (int64_t) irp->bytes_transferred,
                    (int64_t) irp->interval_start_time.secs,
                    (int64_t) irp->interval_end_time.secs,
                    (double) irp->interval_duration,
                    (int) irp->omitted);
        }
        cJSON_AddItemToArray(json_interval, entry);
    }
    char *str = cJSON_Print(json_interval);
    if (str != NULL) {
        jstring chunk = (*g_env)->NewStringUTF(g_env, str);
        (*g_env)->CallVoidMethod(g_env, g_callback, g_method_output, chunk);
        (*g_env)->DeleteLocalRef(g_env, chunk);
        free(str);
    }
    cJSON_Delete(json_interval);
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

    g_stats_cb = test->stats_callback;
    test->stats_callback = custom_stats_callback;

    g_test_thread = pthread_self();
    g_was_cancelled = 0;
    g_jmp_valid = 1;

    int result;
    if (setjmp(g_sigend_jmp)) {
        iperf_got_sigend(test, SIGTERM);
        g_was_cancelled = 1;
        result = -1;
    } else {
        result = is_server ? iperf_run_server(test) : iperf_run_client(test);
    }

    if (result < 0 && !g_was_cancelled) {
        jstring err = (*env)->NewStringUTF(env, iperf_strerror(i_errno));
        (*env)->CallVoidMethod(env, callback, method_error, err);
        (*env)->DeleteLocalRef(env, err);
    }

    iperf_free_test(test);

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