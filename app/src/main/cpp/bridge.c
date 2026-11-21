#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "quickjs.h"
static JSRuntime *rt;
static JSContext *cx;
JNIEXPORT void JNICALL Java_kr_mangwon_holdem_GameActivity_boot(JNIEnv *e,jclass c,jbyteArray code){
 if(cx) JS_FreeContext(cx); if(rt) JS_FreeRuntime(rt);
 rt=JS_NewRuntime(); cx=JS_NewContext(rt);
 jsize len=(*e)->GetArrayLength(e,code); jbyte *buf=(*e)->GetByteArrayElements(e,code,0);
 char *src=malloc(len+1);memcpy(src,buf,len);src[len]=0;JSValue v=JS_Eval(cx,src,len,"club.js",JS_EVAL_TYPE_GLOBAL);free(src);
 if(JS_IsException(v)){JSValue ex=JS_GetException(cx);const char *s=JS_ToCString(cx,ex);__android_log_print(ANDROID_LOG_ERROR,"Mangwon","%s",s);JS_FreeCString(cx,s);JS_FreeValue(cx,ex);}JS_FreeValue(cx,v);(*e)->ReleaseByteArrayElements(e,code,buf,JNI_ABORT);}
JNIEXPORT jstring JNICALL Java_kr_mangwon_holdem_GameActivity_call(JNIEnv *e,jclass c,jstring command){
 const char *str=(*e)->GetStringUTFChars(e,command,0);JSValue global=JS_GetGlobalObject(cx),fn=JS_GetPropertyStr(cx,global,"mobile"),arg=JS_NewString(cx,str),v=JS_Call(cx,fn,global,1,&arg);(*e)->ReleaseStringUTFChars(e,command,str);
 if(JS_IsException(v)){JSValue ex=JS_GetException(cx);const char *s=JS_ToCString(cx,ex);__android_log_print(ANDROID_LOG_ERROR,"Mangwon","%s",s);JS_FreeCString(cx,s);JS_FreeValue(cx,ex);}
 const char *out=JS_ToCString(cx,v);jstring result=(*e)->NewStringUTF(e,out?out:"{}");JS_FreeCString(cx,out);JS_FreeValue(cx,v);JS_FreeValue(cx,arg);JS_FreeValue(cx,fn);JS_FreeValue(cx,global);return result;}
