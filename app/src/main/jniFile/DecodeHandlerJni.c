#include <stdio.h>
#include <jni.h>




JNIEXPORT jbyteArray JNICALL Java_com_google_zxing_client_android_DecodeHandlerJni_dataHandler
    (JNIEnv *env, jobject thiz, jbyteArray array, jint length, jint width, jint height){



    jbyte* jBuffer = (*env) -> GetByteArrayElements(env, array, 0);
    unsigned char* pbuffer = (unsigned char*)jBuffer;




    jbyteArray resultArray = (*env) -> NewByteArray(env, length);
    jbyte *bytes = (*env) -> GetByteArrayElements(env,resultArray, 0);

    for(int y=0; y<height; y++){
        for(int x=0; x<width; x++){
            bytes[x * height + height - y - 1] = pbuffer[x + y * width];
        }
    }



    (*env) -> SetByteArrayRegion(env, resultArray, 0, length, bytes);

    (*env) -> ReleaseByteArrayElements(env, array, jBuffer, JNI_ABORT);             //释放
    (*env) -> ReleaseByteArrayElements(env, resultArray, bytes, JNI_ABORT);         //释放

    return resultArray;
}