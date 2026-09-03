
#include <gphoto2/gphoto2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

static volatile int quit_requested = 0;
static volatile int capture_requested = 0;

static void error_func(GPContext *c, const char *text, void *data) {
    (void)c; (void)data; fprintf(stderr, "[gphoto-error] %s\n", text ? text : "");
}
static void status_func(GPContext *c, const char *text, void *data) {
    (void)c; (void)data;
}

static void *command_thread(void *unused) {
    (void)unused;
    char line[128];
    while (fgets(line, sizeof(line), stdin)) {
        if (!strncmp(line, "CAPTURE", 7)) capture_requested = 1;
        else if (!strncmp(line, "QUIT", 4)) { quit_requested = 1; break; }
    }
    return NULL;
}

static int bind_camera(Camera *camera, GPContext *ctx, const char *requested,
                       char *model_out, size_t model_cap) {
    CameraList *detected = NULL;
    CameraAbilitiesList *abilities = NULL;
    GPPortInfoList *ports = NULL;
    int ret = gp_list_new(&detected);
    if (ret < GP_OK) return ret;
    ret = gp_camera_autodetect(detected, ctx);
    if (ret < GP_OK) goto done;

    int count = gp_list_count(detected);
    const char *model = NULL, *port = NULL;
    for (int i=0;i<count;i++) {
        const char *m=NULL,*p=NULL;
        gp_list_get_name(detected,i,&m); gp_list_get_value(detected,i,&p);
        if (m && p && (!requested || !*requested || !strcasecmp(m, requested))) {
            model=m; port=p; break;
        }
    }
    if (!model && count>0) { gp_list_get_name(detected,0,&model); gp_list_get_value(detected,0,&port); }
    if (!model || !port) { ret=GP_ERROR_BAD_PARAMETERS; goto done; }
    snprintf(model_out,model_cap,"%s",model);

    ret=gp_abilities_list_new(&abilities); if(ret<GP_OK) goto done;
    ret=gp_abilities_list_load(abilities,ctx); if(ret<GP_OK) goto done;
    int ai=gp_abilities_list_lookup_model(abilities,model); if(ai<0){ret=ai;goto done;}
    CameraAbilities ca;
    ret=gp_abilities_list_get_abilities(abilities,ai,&ca); if(ret<GP_OK) goto done;
    ret=gp_camera_set_abilities(camera,ca); if(ret<GP_OK) goto done;

    ret=gp_port_info_list_new(&ports); if(ret<GP_OK) goto done;
    ret=gp_port_info_list_load(ports); if(ret<GP_OK) goto done;
    int pi=gp_port_info_list_lookup_path(ports,port); if(pi<0){ret=pi;goto done;}
    GPPortInfo info;
    ret=gp_port_info_list_get_info(ports,pi,&info); if(ret<GP_OK) goto done;
    ret=gp_camera_set_port_info(camera,info);

done:
    if(ports) gp_port_info_list_free(ports);
    if(abilities) gp_abilities_list_free(abilities);
    if(detected) gp_list_free(detected);
    return ret;
}

static int emit_file(const char *type, CameraFile *file) {
    const char *data=NULL; unsigned long size=0;
    int ret=gp_file_get_data_and_size(file,&data,&size);
    if(ret<GP_OK) return ret;
    printf("%s %lu\n",type,size);
    if(size) fwrite(data,1,size,stdout);
    fflush(stdout);
    return GP_OK;
}

static int capture_and_emit(Camera *camera, GPContext *ctx) {
    CameraFilePath path;
    memset(&path,0,sizeof(path));
    int ret=gp_camera_capture(camera,GP_CAPTURE_IMAGE,&path,ctx);
    if(ret<GP_OK) return ret;

    CameraFile *file=NULL;
    ret=gp_file_new(&file);
    if(ret>=GP_OK) ret=gp_camera_file_get(camera,path.folder,path.name,GP_FILE_TYPE_NORMAL,file,ctx);
    if(ret>=GP_OK) ret=emit_file("CAPTURE",file);
    if(file) gp_file_unref(file);

    int del=gp_camera_file_delete(camera,path.folder,path.name,ctx);
    if(del<GP_OK) fprintf(stderr,"[WARN] delete returned %d\n",del);
    return ret;
}

int main(int argc,char **argv) {
    setvbuf(stdout,NULL,_IONBF,0);
    const char *requested=(argc>1)?argv[1]:"";
    char model[256]={0};

    GPContext *ctx=gp_context_new();
    if(!ctx) return 2;
    gp_context_set_error_func(ctx,error_func,NULL);
    gp_context_set_status_func(ctx,status_func,NULL);

    Camera *camera=NULL;
    int ret=gp_camera_new(&camera);
    if(ret<GP_OK) return 3;

    ret=bind_camera(camera,ctx,requested,model,sizeof(model));
    if(ret<GP_OK) {
        printf("ERROR bind %d\n",ret); fflush(stdout);
        gp_camera_free(camera); gp_context_unref(ctx); return 4;
    }
    ret=gp_camera_init(camera,ctx);
    if(ret<GP_OK) {
        printf("ERROR init %d\n",ret); fflush(stdout);
        gp_camera_free(camera); gp_context_unref(ctx); return 5;
    }

    printf("READY %s\n",model); fflush(stdout);

    pthread_t tid;
    pthread_create(&tid,NULL,command_thread,NULL);

    while(!quit_requested) {
        if(capture_requested) {
            capture_requested=0;
            ret=capture_and_emit(camera,ctx);
            if(ret<GP_OK) {
                printf("ERROR capture %d\n",ret); fflush(stdout);
            }
            continue;
        }

        CameraFile *preview=NULL;
        ret=gp_file_new(&preview);
        if(ret>=GP_OK) ret=gp_camera_capture_preview(camera,preview,ctx);
        if(ret>=GP_OK) ret=emit_file("FRAME",preview);
        if(preview) gp_file_unref(preview);

        if(ret<GP_OK) {
            printf("ERROR preview %d\n",ret); fflush(stdout);
            /* Keep the session alive; a transient preview error should not tear down USB/PTP. */
        }
    }

    pthread_join(tid,NULL);
    gp_camera_exit(camera,ctx);
    gp_camera_free(camera);
    gp_context_unref(ctx);
    return 0;
}
