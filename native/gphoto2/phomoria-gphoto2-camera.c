#include <gphoto2/gphoto2-camera.h>
#include <gphoto2/gphoto2-context.h>
#include <gphoto2/gphoto2-file.h>
#include <gphoto2/gphoto2-list.h>
#include <gphoto2/gphoto2-abilities-list.h>
#include <gphoto2/gphoto2-port-info-list.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <errno.h>

#ifdef _WIN32
#include <io.h>
#include <fcntl.h>
#endif

static volatile int capture_requested = 0;
static volatile int quit_requested = 0;

static void status_func(GPContext *c, const char *text, void *data) {
    (void)c;
    (void)text;
    (void)data;
}

static void error_func(GPContext *c, const char *text, void *data) {
    (void)c;
    (void)data;
    fprintf(stderr, "ERROR %s\n", text ? text : "");
    fflush(stderr);
}

static void *command_thread(void *arg) {
    (void)arg;
    char line[128];

    while (!quit_requested && fgets(line, sizeof(line), stdin)) {
        if (strncmp(line, "CAPTURE", 7) == 0) {
            capture_requested = 1;
        } else if (strncmp(line, "QUIT", 4) == 0) {
            quit_requested = 1;
            break;
        }
    }

    return NULL;
}

static int bind_camera(Camera *camera, GPContext *context, const char *requested_model) {
    CameraList *detected = NULL;
    CameraAbilitiesList *abilities_list = NULL;
    GPPortInfoList *port_list = NULL;
    CameraAbilities abilities;
    GPPortInfo port_info;
    const char *model = NULL;
    const char *port = NULL;
    int ret, count, model_index, port_index;

    ret = gp_list_new(&detected);
    if (ret < GP_OK) return ret;

    ret = gp_camera_autodetect(detected, context);
    if (ret < GP_OK) {
        gp_list_free(detected);
        return ret;
    }

    count = gp_list_count(detected);
    if (count <= 0) {
        gp_list_free(detected);
        return GP_ERROR;
    }

    int selected = 0;
    if (requested_model && requested_model[0]) {
        for (int i = 0; i < count; i++) {
            const char *m = NULL;
            if (gp_list_get_name(detected, i, &m) >= GP_OK &&
                m && strcmp(m, requested_model) == 0) {
                selected = i;
                break;
            }
        }
    }

    ret = gp_list_get_name(detected, selected, &model);
    if (ret < GP_OK) { gp_list_free(detected); return ret; }

    ret = gp_list_get_value(detected, selected, &port);
    if (ret < GP_OK) { gp_list_free(detected); return ret; }

    ret = gp_abilities_list_new(&abilities_list);
    if (ret < GP_OK) { gp_list_free(detected); return ret; }

    ret = gp_abilities_list_load(abilities_list, context);
    if (ret < GP_OK) {
        gp_abilities_list_free(abilities_list);
        gp_list_free(detected);
        return ret;
    }

    model_index = gp_abilities_list_lookup_model(abilities_list, model);
    if (model_index < 0) {
        gp_abilities_list_free(abilities_list);
        gp_list_free(detected);
        return model_index;
    }

    ret = gp_abilities_list_get_abilities(abilities_list, model_index, &abilities);
    if (ret < GP_OK) {
        gp_abilities_list_free(abilities_list);
        gp_list_free(detected);
        return ret;
    }

    ret = gp_camera_set_abilities(camera, abilities);
    if (ret < GP_OK) {
        gp_abilities_list_free(abilities_list);
        gp_list_free(detected);
        return ret;
    }

    ret = gp_port_info_list_new(&port_list);
    if (ret < GP_OK) {
        gp_abilities_list_free(abilities_list);
        gp_list_free(detected);
        return ret;
    }

    ret = gp_port_info_list_load(port_list);
    if (ret < GP_OK) {
        gp_port_info_list_free(port_list);
        gp_abilities_list_free(abilities_list);
        gp_list_free(detected);
        return ret;
    }

    port_index = gp_port_info_list_lookup_path(port_list, port);
    if (port_index < 0) {
        gp_port_info_list_free(port_list);
        gp_abilities_list_free(abilities_list);
        gp_list_free(detected);
        return port_index;
    }

    ret = gp_port_info_list_get_info(port_list, port_index, &port_info);
    if (ret < GP_OK) {
        gp_port_info_list_free(port_list);
        gp_abilities_list_free(abilities_list);
        gp_list_free(detected);
        return ret;
    }

    ret = gp_camera_set_port_info(camera, port_info);

    gp_port_info_list_free(port_list);
    gp_abilities_list_free(abilities_list);
    gp_list_free(detected);

    return ret;
}

static int emit_frame(CameraFile *file) {
    const char *data = NULL;
    unsigned long size = 0;
    int ret = gp_file_get_data_and_size(file, &data, &size);
    if (ret < GP_OK) return ret;

    printf("FRAME %lu\n", size);
    fflush(stdout);

    if (size > 0 && fwrite(data, 1, size, stdout) != size) {
        return GP_ERROR_IO;
    }
    fflush(stdout);
    return GP_OK;
}

static int emit_capture(CameraFile *file) {
    const char *data = NULL;
    unsigned long size = 0;
    int ret = gp_file_get_data_and_size(file, &data, &size);
    if (ret < GP_OK) return ret;

    printf("CAPTURE %lu\n", size);
    fflush(stdout);

    if (size > 0 && fwrite(data, 1, size, stdout) != size) {
        return GP_ERROR_IO;
    }
    fflush(stdout);
    return GP_OK;
}

int main(int argc, char **argv) {
#ifdef _WIN32
    if (_setmode(_fileno(stdout), _O_BINARY) == -1) {
        fprintf(stderr, "ERROR unable to set stdout binary mode: %s\n", strerror(errno));
        return 1;
    }
    if (_setmode(_fileno(stdin), _O_BINARY) == -1) {
        fprintf(stderr, "ERROR unable to set stdin binary mode: %s\n", strerror(errno));
        return 1;
    }
#endif

    const char *requested_model = argc > 1 ? argv[1] : "";

    GPContext *context = gp_context_new();
    if (!context) return 1;

    gp_context_set_status_func(context, status_func, NULL);
    gp_context_set_error_func(context, error_func, NULL);

    Camera *camera = NULL;
    int ret = gp_camera_new(&camera);
    if (ret < GP_OK) {
        fprintf(stderr, "ERROR gp_camera_new %d\n", ret);
        gp_context_unref(context);
        return 1;
    }

    ret = bind_camera(camera, context, requested_model);
    if (ret < GP_OK) {
        fprintf(stderr, "ERROR bind_camera %s (%d)\n", gp_result_as_string(ret), ret);
        gp_camera_free(camera);
        gp_context_unref(context);
        return 1;
    }

    ret = gp_camera_init(camera, context);
    if (ret < GP_OK) {
        fprintf(stderr, "ERROR gp_camera_init %s (%d)\n", gp_result_as_string(ret), ret);
        gp_camera_free(camera);
        gp_context_unref(context);
        return 1;
    }

    printf("READY %s\n", requested_model);
    fflush(stdout);

    pthread_t thread;
    if (pthread_create(&thread, NULL, command_thread, NULL) != 0) {
        fprintf(stderr, "ERROR cannot create command thread\n");
        gp_camera_exit(camera, context);
        gp_camera_free(camera);
        gp_context_unref(context);
        return 1;
    }

    while (!quit_requested) {
        if (capture_requested) {
            capture_requested = 0;

            CameraFilePath path;
            memset(&path, 0, sizeof(path));

            ret = gp_camera_capture(camera, GP_CAPTURE_IMAGE, &path, context);
            if (ret < GP_OK) {
                fprintf(stderr, "ERROR capture %s (%d)\n", gp_result_as_string(ret), ret);
                fflush(stderr);
                continue;
            }

            CameraFile *file = NULL;
            ret = gp_file_new(&file);
            if (ret < GP_OK) {
                fprintf(stderr, "ERROR gp_file_new %d\n", ret);
                continue;
            }

            ret = gp_camera_file_get(
                camera, path.folder, path.name,
                GP_FILE_TYPE_NORMAL, file, context
            );

            if (ret < GP_OK) {
                fprintf(stderr, "ERROR file_get %s (%d)\n", gp_result_as_string(ret), ret);
                gp_file_free(file);
                continue;
            }

            ret = emit_capture(file);
            gp_file_free(file);

            if (ret < GP_OK) {
                fprintf(stderr, "ERROR emit_capture %s (%d)\n", gp_result_as_string(ret), ret);
                fflush(stderr);
                continue;
            }

            gp_camera_file_delete(camera, path.folder, path.name, context);
            continue;
        }

        CameraFile *preview = NULL;
        ret = gp_file_new(&preview);
        if (ret < GP_OK) {
            fprintf(stderr, "ERROR gp_file_new preview %d\n", ret);
            break;
        }

        ret = gp_camera_capture_preview(camera, preview, context);
        if (ret >= GP_OK) {
            ret = emit_frame(preview);
        }

        gp_file_free(preview);

        if (ret < GP_OK) {
            /*
             * A preview failure can mean the USB camera disappeared.
             * Do not keep spinning inside libgphoto2: the Java side would
             * remain blocked waiting for the next protocol header while
             * stderr is flooded with repeated preview errors.
             *
             * Send the failure through the stdout protocol first, then
             * terminate this helper. Java will receive ERROR immediately,
             * classify it as a connection failure, dispose this stale
             * session, and start a fresh autodetect/reconnect cycle.
             */
            fprintf(stdout, "ERROR preview %s (%d)\n",
                    gp_result_as_string(ret), ret);
            fflush(stdout);

            fprintf(stderr, "ERROR preview %s (%d)\n",
                    gp_result_as_string(ret), ret);
            fflush(stderr);
            break;
        }
    }

    pthread_join(thread, NULL);
    gp_camera_exit(camera, context);
    gp_camera_free(camera);
    gp_context_unref(context);
    return 0;
}
