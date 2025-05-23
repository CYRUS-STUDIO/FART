> 版权归作者所有，如有转发，请注明文章出处：<https://cyrus-studio.github.io/blog/>

# FART 源码



FART 是 ART 环境下基于主动调用的自动化脱壳方案。



关于 FART 详细介绍参考：

- [FART 自动化脱壳框架简介与脱壳点的选择](https://cyrus-studio.github.io/blog/posts/fart-%E8%87%AA%E5%8A%A8%E5%8C%96%E8%84%B1%E5%A3%B3%E6%A1%86%E6%9E%B6%E7%AE%80%E4%BB%8B%E4%B8%8E%E8%84%B1%E5%A3%B3%E7%82%B9%E7%9A%84%E9%80%89%E6%8B%A9/)

- [FART 主动调用组件设计和源码分析](https://cyrus-studio.github.io/blog/posts/fart-%E4%B8%BB%E5%8A%A8%E8%B0%83%E7%94%A8%E7%BB%84%E4%BB%B6%E8%AE%BE%E8%AE%A1%E5%92%8C%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90/)



开源地址：[https://github.com/hanbinglengyue/FART](https://github.com/hanbinglengyue/FART)



目前 FART 是基于 Android 6.0 实现，源码文件结构如下：



![word/media/image1.png](https://gitee.com/cyrus-studio/images/raw/master/bd1a32e9461378d9db1085c8d34f6c2b.png)


把 FART 源码移植到 LineageOS 17.1（Android 10）。



关于 LineageOS 源码的下载与编译参考：[LineageOS源码下载和编译（Xiaomi Mi 6X，wayne）](https://cyrus-studio.github.io/blog/posts/lineageos%E6%BA%90%E7%A0%81%E4%B8%8B%E8%BD%BD%E5%92%8C%E7%BC%96%E8%AF%91xiaomi-mi-6xwayne/)



# 移植 FART 到 Android 10



对比 android 6.0 中的源码和 FART 的源码找到修改的地方并移植到 Android 10 源码中



![word/media/image2.png](https://gitee.com/cyrus-studio/images/raw/master/3aff57087ce104bd2c4616c0aab39e81.png)


## interpreter.cc



路径：art/runtime/interpreter/interpreter.cc



Android 6.0 源码：[https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:art/runtime/interpreter/interpreter.cc](https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:art/runtime/interpreter/interpreter.cc)



namespace art 中增加 dumpDexFileByExecute 函数声明

```
//add
extern "C" void dumpDexFileByExecute(ArtMethod* artmethod);
```


在 Execute 函数头部增加 dumpDexFileByExecute 调用

```
static inline JValue Execute(
    Thread* self,
    const CodeItemDataAccessor& accessor,
    ShadowFrame& shadow_frame,
    JValue result_register,
    bool stay_in_interpreter = false,
    bool from_deoptimize = false) REQUIRES_SHARED(Locks::mutator_lock_) {

    //add
    LOG(INFO) << "[Execute]" << shadow_frame.GetMethod()->PrettyMethod();          
    if(strstr(PrettyMethod(shadow_frame.GetMethod()).c_str(),"<clinit>")!=nullptr) {
      dumpDexFileByExecute(shadow_frame.GetMethod());
    }        
    
    ...                                                           
}        
```


## dalvik_system_DexFile.cc



路径：art/runtime/native/dalvik_system_DexFile.cc



Android 6.0 源码：[https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:art/runtime/native/dalvik_system_DexFile.cc](https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:art/runtime/native/dalvik_system_DexFile.cc)



导入头文件：

```
//add
#include "scoped_fast_native_object_access-inl.h"
```


namespace art 中增加 myfartInvoke 函数声明

```
//add
extern "C" void myfartInvoke(ArtMethod* artmethod);
//add
extern "C" ArtMethod* jobject2ArtMethod(JNIEnv* env, jobject javaMethod);
```


添加 DexFile_dumpMethodCode 函数实现（注意：要在 gMethods 之前）

```
//add
static void DexFile_dumpMethodCode(JNIEnv* env, jclass,jobject method) {
    if(method!=nullptr) {
        ArtMethod* proxy_method = jobject2ArtMethod(env, method);
        myfartInvoke(proxy_method);
    }
    return;
}
```


在 gMethods 中增加 dumpMethodCode 的函数签名

```
static JNINativeMethod gMethods[] = {
  NATIVE_METHOD(DexFile, dumpMethodCode, "(Ljava/lang/Object;)V"),
};
```


## java_lang_reflect_Method.cc



在 namespace art 中增加 jobject2ArtMethod 方法实现

```
//add
extern "C" ArtMethod* jobject2ArtMethod(JNIEnv* env, jobject javaMethod) {
    ScopedFastNativeObjectAccess soa(env);
    ArtMethod* method = ArtMethod::FromReflectedMethod(soa, javaMethod);
    return method;
}
```


## art_method.cc



路径：art/runtime/art_method.cc



Android 6.0 源码：[https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:art/runtime/art_method.cc](https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:art/runtime/art_method.cc)



添加头文件

```
//add
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include "runtime.h"
#include <android/log.h>
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>
```


增加宏定义

```
//add
#define gettidv1() syscall(__NR_gettid)
#define LOG_TAG "ActivityThread"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
```


namespace art 中增加以下函数

```
//add
uint8_t* codeitem_end(const uint8_t **pData){
    uint32_t num_of_list = DecodeUnsignedLeb128(pData);
    for (;num_of_list>0;num_of_list--) {
        int32_t num_of_handlers=DecodeSignedLeb128(pData);
        int num=num_of_handlers;
        if (num_of_handlers<=0) {
            num=-num_of_handlers;
        }
        for (; num > 0; num--) {
            DecodeUnsignedLeb128(pData);
            DecodeUnsignedLeb128(pData);
        }
        if (num_of_handlers<=0) {
            DecodeUnsignedLeb128(pData);
        }
    }
    return (uint8_t*)(*pData);
}

//add
extern "C" char *base64_encode(char *str,long str_len,long* outlen){
    long len;
    char *res;
    int i,j;
    const char *base64_table="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    if(str_len % 3 == 0)
        len=str_len/3*4;
    else
        len=(str_len/3+1)*4;

    res=(char*)malloc(sizeof(char)*(len+1));
    res[len]='\0';
    *outlen=len;

    for(i=0,j=0;i<len-2;j+=3,i+=4){
        res[i]=base64_table[str[j]>>2];
        res[i+1]=base64_table[(str[j]&0x3)<<4 | (str[j+1]>>4)];
        res[i+2]=base64_table[(str[j+1]&0xf)<<2 | (str[j+2]>>6)];
        res[i+3]=base64_table[str[j+2]&0x3f];
    }

    switch(str_len % 3){
        case 1:
            res[i-2]='=';
            res[i-1]='=';
            break;
        case 2:
            res[i-1]='=';
            break;
    }
    return res;
}

//add
extern "C" void dumpDexFileByExecute(ArtMethod* artmethod) REQUIRES_SHARED(Locks::mutator_lock_) {
        char szCmdline[64] = {0};
        char szProcName[256] = {0};
        int procid = getpid();
        snprintf(szCmdline, sizeof(szCmdline), "/proc/%d/cmdline", procid);

        int fcmdline = open(szCmdline, O_RDONLY);
        if (fcmdline > 0) {
            ssize_t result = read(fcmdline, szProcName, sizeof(szProcName) - 1);
            if (result < 0) {
                LOG(ERROR) << "dumpDexFileByExecute: Failed to read cmdline";
            }
            close(fcmdline);
        }

        if (szProcName[0] == '\0') return;

        const DexFile* dex_file = artmethod->GetDexFile();
        const uint8_t* begin_ = dex_file->Begin();
        size_t size_ = dex_file->Size();
        int size_int = static_cast<int>(size_);

        std::string base_dir = "/sdcard/fart/";
        std::string app_dir = base_dir + szProcName;
        std::string dex_path = app_dir + "/" + std::to_string(size_int) + "_dexfile_execute.dex";
        std::string classlist_path = app_dir + "/" + std::to_string(size_int) + "_classlist_execute.txt";

        mkdir(base_dir.c_str(), 0777);
        mkdir(app_dir.c_str(), 0777);

        int dexfilefp = open(dex_path.c_str(), O_RDONLY);
        if (dexfilefp > 0) {
            close(dexfilefp);
        } else {
            int fp = open(dex_path.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
            if (fp > 0) {
                ssize_t w1 = write(fp, begin_, size_);
                if (w1 < 0) {
                    LOG(ERROR) << "dumpDexFileByExecute: Failed to write dex file";
                }
                fsync(fp);
                close(fp);

                int classlistfile = open(classlist_path.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
                if (classlistfile > 0) {
                    for (size_t ii = 0; ii < dex_file->NumClassDefs(); ++ii) {
                        const dex::ClassDef& class_def = dex_file->GetClassDef(ii);
                        const char* descriptor = dex_file->GetClassDescriptor(class_def);

                        ssize_t w2 = write(classlistfile, descriptor, strlen(descriptor));
                        if (w2 < 0) {
                            LOG(ERROR) << "dumpDexFileByExecute: Failed to write class descriptor";
                        }

                        ssize_t w3 = write(classlistfile, "\n", 1);
                        if (w3 < 0) {
                            LOG(ERROR) << "dumpDexFileByExecute: Failed to write newline";
                        }
                    }
                    fsync(classlistfile);
                    close(classlistfile);
                }
            }
        }
}

//add
extern "C" void dumpArtMethod(ArtMethod* artmethod) REQUIRES_SHARED(Locks::mutator_lock_) {
        char szProcName[256] = {0};
        int procid = getpid();

        // 获取进程名
        char szCmdline[64] = {0};
        snprintf(szCmdline, sizeof(szCmdline), "/proc/%d/cmdline", procid);
        int fcmdline = open(szCmdline, O_RDONLY);
        if (fcmdline > 0) {
            ssize_t result = read(fcmdline, szProcName, sizeof(szProcName) - 1);
            if (result < 0) {
                LOG(ERROR) << "ArtMethod::dumpArtMethod: read cmdline failed.";
            }
            close(fcmdline);
        }

        if (szProcName[0] == '\0') return;

        const DexFile* dex_file = artmethod->GetDexFile();
        const uint8_t* begin_ = dex_file->Begin();
        size_t size_ = dex_file->Size();
        int size_int = static_cast<int>(size_);

        // 路径拼接
        std::string baseDir = "/sdcard/fart/";
        std::string processDir = baseDir + szProcName;
        mkdir(baseDir.c_str(), 0777);
        mkdir(processDir.c_str(), 0777);

        // 保存 dex 文件
        std::string dexPath = processDir + "/" + std::to_string(size_int) + "_dexfile.dex";
        int dexfilefp = open(dexPath.c_str(), O_RDONLY);
        if (dexfilefp > 0) {
            close(dexfilefp);
        } else {
            int fp = open(dexPath.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
            if (fp > 0) {
                ssize_t w = write(fp, begin_, size_);
                if (w < 0) {
                    LOG(ERROR) << "ArtMethod::dumpArtMethod: write dexfile failed -> " << dexPath;
                }
                fsync(fp);
                close(fp);

                // 保存 class 列表
                std::string classListPath = processDir + "/" + std::to_string(size_int) + "_classlist.txt";
                int classlistfile = open(classListPath.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
                if (classlistfile > 0) {
                    for (size_t i = 0; i < dex_file->NumClassDefs(); ++i) {
                        const dex::ClassDef& class_def = dex_file->GetClassDef(i);
                        const char* descriptor = dex_file->GetClassDescriptor(class_def);

                        ssize_t w1 = write(classlistfile, descriptor, strlen(descriptor));
                        if (w1 < 0) {
                            LOG(ERROR) << "ArtMethod::dumpArtMethod: write class descriptor failed";
                        }

                        ssize_t w2 = write(classlistfile, "\n", 1);
                        if (w2 < 0) {
                            LOG(ERROR) << "ArtMethod::dumpArtMethod: write newline failed";
                        }
                    }
                    fsync(classlistfile);
                    close(classlistfile);
                }
            }
        }

        // 保存指令码
        const dex::CodeItem* code_item = artmethod->GetCodeItem();
        if (LIKELY(code_item != nullptr)) {
            uint8_t* item = (uint8_t*)code_item;
            int code_item_len = 0;
            CodeItemDataAccessor accessor(*dex_file, code_item);
            if (accessor.TriesSize() > 0) {
                const uint8_t* handler_data = accessor.GetCatchHandlerData();
                uint8_t* tail = codeitem_end(&handler_data);
                code_item_len = static_cast<int>(tail - item);
            } else {
                code_item_len = 16 + accessor.InsnsSizeInCodeUnits() * 2;
            }

            uint32_t method_idx = artmethod->GetDexMethodIndex();
            int offset = static_cast<int>(item - begin_);
            pid_t tid = gettidv1();
            std::string insPath = processDir + "/" + std::to_string(size_int) + "_ins_" + std::to_string(tid) + ".bin";

            int fp2 = open(insPath.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
            if (fp2 > 0) {
                lseek(fp2, 0, SEEK_END);
                std::string header = "{name:" + artmethod->PrettyMethod() +
                                     ",method_idx:" + std::to_string(method_idx) +
                                     ",offset:" + std::to_string(offset) +
                                     ",code_item_len:" + std::to_string(code_item_len) +
                                     ",ins:";

                ssize_t w3 = write(fp2, header.c_str(), header.length());
                if (w3 < 0) {
                    LOG(ERROR) << "ArtMethod::dumpArtMethod: write header failed";
                }

                long outlen = 0;
                char* base64result = base64_encode((char*)item, (long)code_item_len, &outlen);
                if (base64result != nullptr) {
                    ssize_t w4 = write(fp2, base64result, outlen);
                    if (w4 < 0) {
                        LOG(ERROR) << "ArtMethod::dumpArtMethod: write base64 ins failed";
                    }
                    free(base64result);
                }

                ssize_t w5 = write(fp2, "};", 2);
                if (w5 < 0) {
                    LOG(ERROR) << "ArtMethod::dumpArtMethod: write tail failed";
                }

                fsync(fp2);
                close(fp2);
            }
        }
}

//add
extern "C" void myfartInvoke(ArtMethod* artmethod)  REQUIRES_SHARED(Locks::mutator_lock_) {
        JValue *result=nullptr;
        Thread *self=nullptr;
        uint32_t temp=6;
        uint32_t* args=&temp;
        uint32_t args_size=6;
        artmethod->Invoke(self, args, args_size, result, "fart");
}
```


ArtMethod::Invoke 中添加判断如果是 fart 的主动调用就 dump

```
void ArtMethod::Invoke(Thread* self, uint32_t* args, uint32_t args_size, JValue* result,
                       const char* shorty) {
    //add
    if (self == nullptr) {
        dumpArtMethod(this);
        return;
    }
  
  ...
}  
```


## ActivityThread.java



路径：frameworks/base/core/java/android/app/ActivityThread.java



Android 6.0 源码：[https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:frameworks/base/core/java/android/app/ActivityThread.java](https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:frameworks/base/core/java/android/app/ActivityThread.java)



import 相关 java 类引用

```
//add
import android.app.Application;
import android.util.ArrayMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
```


```
//add
public static HashMap<String, String> dumpClassm_hashmap = new HashMap<>();
```


添加以下 java 方法到 ActivityThread 类

```
//add
public static Field getClassField(ClassLoader classloader, String class_name,
                                  String filedName) {

    try {
        Class obj_class = classloader.loadClass(class_name);//Class.forName(class_name);
        Field field = obj_class.getDeclaredField(filedName);
        field.setAccessible(true);
        return field;
    } catch (SecurityException e) {
        e.printStackTrace();
    } catch (NoSuchFieldException e) {
        e.printStackTrace();
    } catch (IllegalArgumentException e) {
        e.printStackTrace();
    } catch (ClassNotFoundException e) {
        e.printStackTrace();
    }
    return null;

}

//add
public static Object getClassFieldObject(ClassLoader classloader, String class_name, Object obj,
                                         String filedName) {

    try {
        Class obj_class = classloader.loadClass(class_name);//Class.forName(class_name);
        Field field = obj_class.getDeclaredField(filedName);
        field.setAccessible(true);
        Object result = null;
        result = field.get(obj);
        return result;
    } catch (SecurityException e) {
        e.printStackTrace();
    } catch (NoSuchFieldException e) {
        e.printStackTrace();
    } catch (IllegalArgumentException e) {
        e.printStackTrace();
    } catch (ClassNotFoundException e) {
        e.printStackTrace();
    } catch (IllegalAccessException e) {
        e.printStackTrace();
    }
    return null;

}

//add
public static Object invokeStaticMethod(String class_name,
                                        String method_name, Class[] pareTyple, Object[] pareVaules) {

    try {
        Class obj_class = Class.forName(class_name);
        Method method = obj_class.getMethod(method_name, pareTyple);
        return method.invoke(null, pareVaules);
    } catch (SecurityException e) {
        e.printStackTrace();
    } catch (IllegalArgumentException e) {
        e.printStackTrace();
    } catch (IllegalAccessException e) {
        e.printStackTrace();
    } catch (NoSuchMethodException e) {
        e.printStackTrace();
    } catch (InvocationTargetException e) {
        e.printStackTrace();
    } catch (ClassNotFoundException e) {
        e.printStackTrace();
    }
    return null;

}

//add
public static Object getFieldOjbect(String class_name, Object obj,
                                    String filedName) {
    try {
        Class obj_class = Class.forName(class_name);
        Field field = obj_class.getDeclaredField(filedName);
        field.setAccessible(true);
        return field.get(obj);
    } catch (SecurityException e) {
        e.printStackTrace();
    } catch (NoSuchFieldException e) {
        e.printStackTrace();
    } catch (IllegalArgumentException e) {
        e.printStackTrace();
    } catch (IllegalAccessException e) {
        e.printStackTrace();
    } catch (ClassNotFoundException e) {
        e.printStackTrace();
    } catch (NullPointerException e) {
        e.printStackTrace();
    }
    return null;

}

//add
public static ClassLoader getClassloader() {
    ClassLoader resultClassloader = null;
    Object currentActivityThread = invokeStaticMethod(
            "android.app.ActivityThread", "currentActivityThread",
            new Class[]{}, new Object[]{});
    Object mBoundApplication = getFieldOjbect(
            "android.app.ActivityThread", currentActivityThread,
            "mBoundApplication");
    Application mInitialApplication = (Application) getFieldOjbect("android.app.ActivityThread",
            currentActivityThread, "mInitialApplication");
    Object loadedApkInfo = getFieldOjbect(
            "android.app.ActivityThread$AppBindData",
            mBoundApplication, "info");
    Application mApplication = (Application) getFieldOjbect("android.app.LoadedApk", loadedApkInfo, "mApplication");
    resultClassloader = mApplication.getClassLoader();
    return resultClassloader;
}

//add
public static void loadClassAndInvoke(ClassLoader appClassloader, String eachclassname, Method dumpMethodCode_method) {
    Class resultclass = null;
    Log.i("ActivityThread", "go into loadClassAndInvoke->" + "classname:" + eachclassname);
    try {
        resultclass = appClassloader.loadClass(eachclassname);
    } catch (Exception e) {
        e.printStackTrace();
        return;
    } catch (Error e) {
        e.printStackTrace();
        return;
    }
    if (resultclass != null) {
        try {
            Constructor<?> cons[] = resultclass.getDeclaredConstructors();
            for (Constructor<?> constructor : cons) {
                if (dumpMethodCode_method != null) {
                    try {
                        dumpMethodCode_method.invoke(null, constructor);
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    } catch (Error e) {
                        e.printStackTrace();
                        continue;
                    }
                } else {
                    Log.e("ActivityThread", "dumpMethodCode_method is null ");
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } catch (Error e) {
            e.printStackTrace();
        }
        try {
            Method[] methods = resultclass.getDeclaredMethods();
            if (methods != null) {
                for (Method m : methods) {
                    if (dumpMethodCode_method != null) {
                        try {
                            dumpMethodCode_method.invoke(null, m);
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        } catch (Error e) {
                            e.printStackTrace();
                            continue;
                        }
                    } else {
                        Log.e("ActivityThread", "dumpMethodCode_method is null ");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } catch (Error e) {
            e.printStackTrace();
        }
    }
}

//add
public static void fart() {
    ClassLoader appClassloader = getClassloader();
    ClassLoader tmpClassloader=appClassloader;
    ClassLoader parentClassloader=appClassloader.getParent();
    if(appClassloader.toString().indexOf("java.lang.BootClassLoader")==-1)
    {
        fartwithClassloader(appClassloader);
    }
    while(parentClassloader!=null){
        if(parentClassloader.toString().indexOf("java.lang.BootClassLoader")==-1)
        {
            fartwithClassloader(parentClassloader);
        }
        tmpClassloader=parentClassloader;
        parentClassloader=parentClassloader.getParent();
    }
}

//add
public static void fartwithClassloader(ClassLoader appClassloader) {
    List<Object> dexFilesArray = new ArrayList<Object>();
    Field pathList_Field = (Field) getClassField(appClassloader, "dalvik.system.BaseDexClassLoader", "pathList");
    Object pathList_object = getFieldOjbect("dalvik.system.BaseDexClassLoader", appClassloader, "pathList");
    Object[] ElementsArray = (Object[]) getFieldOjbect("dalvik.system.DexPathList", pathList_object, "dexElements");
    Field dexFile_fileField = null;
    try {
        dexFile_fileField = (Field) getClassField(appClassloader, "dalvik.system.DexPathList$Element", "dexFile");
    } catch (Exception e) {
        e.printStackTrace();
    } catch (Error e) {
        e.printStackTrace();
    }
    Class DexFileClazz = null;
    try {
        DexFileClazz = appClassloader.loadClass("dalvik.system.DexFile");
    } catch (Exception e) {
        e.printStackTrace();
    } catch (Error e) {
        e.printStackTrace();
    }
    Method getClassNameList_method = null;
    Method defineClass_method = null;
    Method dumpDexFile_method = null;
    Method dumpMethodCode_method = null;

    for (Method field : DexFileClazz.getDeclaredMethods()) {
        if (field.getName().equals("getClassNameList")) {
            getClassNameList_method = field;
            getClassNameList_method.setAccessible(true);
        }
        if (field.getName().equals("defineClassNative")) {
            defineClass_method = field;
            defineClass_method.setAccessible(true);
        }
        if (field.getName().equals("dumpDexFile")) {
            dumpDexFile_method = field;
            dumpDexFile_method.setAccessible(true);
        }
        if (field.getName().equals("dumpMethodCode")) {
            dumpMethodCode_method = field;
            dumpMethodCode_method.setAccessible(true);
        }
    }
    Field mCookiefield = getClassField(appClassloader, "dalvik.system.DexFile", "mCookie");
    Log.v("ActivityThread->methods", "dalvik.system.DexPathList.ElementsArray.length:" + ElementsArray.length);//5个
    for (int j = 0; j < ElementsArray.length; j++) {
        Object element = ElementsArray[j];
        Object dexfile = null;
        try {
            dexfile = (Object) dexFile_fileField.get(element);
        } catch (Exception e) {
            e.printStackTrace();
        } catch (Error e) {
            e.printStackTrace();
        }
        if (dexfile == null) {
            Log.e("ActivityThread", "dexfile is null");
            continue;
        }
        if (dexfile != null) {
            dexFilesArray.add(dexfile);
            Object mcookie = getClassFieldObject(appClassloader, "dalvik.system.DexFile", dexfile, "mCookie");
            if (mcookie == null) {
                Object mInternalCookie = getClassFieldObject(appClassloader, "dalvik.system.DexFile", dexfile, "mInternalCookie");
                if(mInternalCookie!=null)
                {
                    mcookie=mInternalCookie;
                }else{
                    Log.v("ActivityThread->err", "get mInternalCookie is null");
                    continue;
                }

            }
            String[] classnames = null;
            try {
                classnames = (String[]) getClassNameList_method.invoke(dexfile, mcookie);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            } catch (Error e) {
                e.printStackTrace();
                continue;
            }
            if (classnames != null) {
                for (String eachclassname : classnames) {
                    loadClassAndInvoke(appClassloader, eachclassname, dumpMethodCode_method);
                }
            }

        }
    }
    return;
}

//add
public static void fartthread() {
    new Thread(new Runnable() {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            try {
                Log.e("ActivityThread", "start sleep......");
                Thread.sleep(1 * 60 * 1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            Log.e("ActivityThread", "sleep over and start fart");
            fart();
            Log.e("ActivityThread", "fart run over");

        }
    }).start();
}
```


在 performLaunchActivity 方法中添加日志和  fartthread 方法调用

```
private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
    //add
    Log.e("ActivityThread","go into performLaunchActivity");

    //add
    fartthread();
    
    return activity;
}
```


handleBindApplication 中添加日志

```
private void handleBindApplication(AppBindData data) {
    //add
    Log.e("ActivityThread","go into handleBindApplication");
}
```


## DexFile.java



路径：libcore/dalvik/src/main/java/dalvik/system/DexFile.java



Android 6.0 源码：[https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:libcore/dalvik/src/main/java/dalvik/system/DexFile.java](https://cs.android.com/android/platform/superproject/+/android-6.0.0_r1:libcore/dalvik/src/main/java/dalvik/system/DexFile.java)



在 DexFile 类中增加 native 方法 dumpMethodCode

```
//add
private static native void dumpMethodCode(Object m);
```


# 编译系统



重新编译 android 系统

```
# 初始化编译环境
source build/envsetup.sh

# 设置编译目标
breakfast wayne

# 回到 Android 源码树的根目录
croot

# 开始编译
brunch wayne
```
参考：[LineageOS源码下载和编译（Xiaomi Mi 6X，wayne）](https://cyrus-studio.github.io/blog/posts/lineageos%E6%BA%90%E7%A0%81%E4%B8%8B%E8%BD%BD%E5%92%8C%E7%BC%96%E8%AF%91xiaomi-mi-6xwayne/)



生成 OTA 包

```
./sign_ota_wayne.sh
```
参考：[使用 release key 对 LineageOS 进行编译和签名](https://cyrus-studio.github.io/blog/posts/%E4%BD%BF%E7%94%A8-release-key-%E5%AF%B9-lineageos-%E8%BF%9B%E8%A1%8C%E7%BC%96%E8%AF%91%E5%92%8C%E7%AD%BE%E5%90%8D/)



# 解决编译报错



报错日志如下：

```
cyrus@cyrus:/mnt/case_sensitive/lineage-17.1$ brunch wayne
Trying dependencies-only mode on a non-existing device tree?

============================================
PLATFORM_VERSION_CODENAME=REL
PLATFORM_VERSION=10
LINEAGE_VERSION=17.1-20250521-UNOFFICIAL-wayne
TARGET_PRODUCT=lineage_wayne
TARGET_BUILD_VARIANT=userdebug
TARGET_BUILD_TYPE=release
TARGET_ARCH=arm64
TARGET_ARCH_VARIANT=armv8-a
TARGET_CPU_VARIANT=cortex-a73
TARGET_2ND_ARCH=arm
TARGET_2ND_ARCH_VARIANT=armv8-a
TARGET_2ND_CPU_VARIANT=kryo
HOST_ARCH=x86_64
HOST_2ND_ARCH=x86
HOST_OS=linux
HOST_OS_EXTRA=Linux-5.15.153.1-microsoft-standard-WSL2-x86_64-Ubuntu-22.04.3-LTS
HOST_CROSS_OS=windows
HOST_CROSS_ARCH=x86
HOST_CROSS_2ND_ARCH=x86_64
HOST_BUILD_TYPE=release
BUILD_ID=QQ3A.200805.001
OUT_DIR=out
PRODUCT_SOONG_NAMESPACES=vendor/xiaomi/sdm660-common device/xiaomi/sdm660-common device/xiaomi/wayne vendor/xiaomi/wayne-common hardware/qcom-caf/msm8998
============================================
============================================
PLATFORM_VERSION_CODENAME=REL
PLATFORM_VERSION=10
LINEAGE_VERSION=17.1-20250521-UNOFFICIAL-wayne
TARGET_PRODUCT=lineage_wayne
TARGET_BUILD_VARIANT=userdebug
TARGET_BUILD_TYPE=release
TARGET_ARCH=arm64
TARGET_ARCH_VARIANT=armv8-a
TARGET_CPU_VARIANT=cortex-a73
TARGET_2ND_ARCH=arm
TARGET_2ND_ARCH_VARIANT=armv8-a
TARGET_2ND_CPU_VARIANT=kryo
HOST_ARCH=x86_64
HOST_2ND_ARCH=x86
HOST_OS=linux
HOST_OS_EXTRA=Linux-5.15.153.1-microsoft-standard-WSL2-x86_64-Ubuntu-22.04.3-LTS
HOST_CROSS_OS=windows
HOST_CROSS_ARCH=x86
HOST_CROSS_2ND_ARCH=x86_64
HOST_BUILD_TYPE=release
BUILD_ID=QQ3A.200805.001
OUT_DIR=out
PRODUCT_SOONG_NAMESPACES=vendor/xiaomi/sdm660-common device/xiaomi/sdm660-common device/xiaomi/wayne vendor/xiaomi/wayne-common hardware/qcom-caf/msm8998
============================================
device/xiaomi/sdm660-common/common_prop.mk was modified, regenerating...
device/xiaomi/sdm660-common/common_prop.mk was modified, regenerating...
[100% 1065/1065] writing build rules ...
build/make/core/base_rules.mk:510: warning: overriding commands for target `out/target/product/wayne/vendor/lib/libstdc++.so'
build/make/core/base_rules.mk:510: warning: ignoring old commands for target `out/target/product/wayne/vendor/lib/libstdc++.so'
build/make/core/base_rules.mk:510: warning: overriding commands for target `out/target/product/wayne/vendor/lib64/libstdc++.so'
build/make/core/base_rules.mk:510: warning: ignoring old commands for target `out/target/product/wayne/vendor/lib64/libstdc++.so'
out/target/product/wayne/obj/CONFIG/kati_packaging/dist.mk was modified, regenerating...
[ 19% 1093/5705] //art/runtime:libart clang++ art_method.cc
FAILED: out/soong/.intermediates/art/runtime/libart/android_arm64_armv8-a_cortex-a73_core_shared_com.android.runtime.release/obj/art/runtime/art_method.o
PWD=/proc/self/cwd /usr/bin/ccache prebuilts/clang/host/linux-x86/clang-r353983c1/bin/clang++ -c -Iart/runtime -Ibionic/libc/private -Iexternal/vixl/src -Iart/sigchainlib -Iexternal/zlib -Iart/runtime -D__ANDROID_APEX__=com.android.runtime.release  -Werror=implicit-function-declaration -DANDROID -fmessage-length=0 -W -Wall -Wno-unused -Winit-self -Wpointer-arith -no-canonical-prefixes -DNDEBUG -UDEBUG -fno-exceptions -Wno-multichar -O2 -g -fno-strict-aliasing -fdebug-prefix-map=/proc/self/cwd= -D__compiler_offsetof=__builtin_offsetof -faddrsig -Wimplicit-fallthrough -Werror=int-conversion -Wno-reserved-id-macro -Wno-format-pedantic -Wno-unused-command-line-argument -fcolor-diagnostics -Wno-zero-as-null-pointer-constant -Wno-sign-compare -Wno-defaulted-function-deleted -Wno-inconsistent-missing-override -ffunction-sections -fdata-sections -fno-short-enums -funwind-tables -fstack-protector-strong -Wa,--noexecstack -D_FORTIFY_SOURCE=2 -Wstrict-aliasing=2 -Werror=return-type -Werror=non-virtual-dtor -Werror=address -Werror=sequence-point -Werror=date-time -Werror=format-security -nostdlibinc -march=armv8-a -mcpu=cortex-a53 -Iart/cmdline -Iart/tools/cpp-define-generator -Iexternal/icu/icu4c/source/common -Iexternal/icu/android_icu4c/include -Ilibnativehelper/header_only_include -Ilibnativehelper/platform_include -Iexternal/zlib -Iart/libelffile -Iart/libartpalette/include -Isystem/core/libnativebridge/include -Isystem/core/libnativeloader/include -Isystem/core/libbacktrace/include -Isystem/core/demangle/include -Isystem/core/liblog/include -D__LIBLOG_API__=10000 -Isystem/core/base/include -Iart/libartbase -Iexternal/lz4/lib -Iexternal/lzma/C -Iart/libdexfile -Iart/libprofile -D__LIBDL_ANDROID_API__=10000 -Ibionic/libc/async_safe/include -Iexternal/libcxx/include -Iexternal/libcxxabi/include -Ibionic/libc/include -Ibionic/libc/system_properties/include -Isystem/core/property_service/libpropertyinfoparser/include -Iout/soong/.intermediates/art/runtime/art_operator_srcs/gen/gensrcs -Iout/soong/.intermediates/art/tools/cpp-define-generator/cpp-define-generator-asm-support/android_arm64_armv8-a_cortex-a73_core/gen -Isystem/core/include -Isystem/media/audio/include -Ihardware/libhardware/include -Ihardware/libhardware_legacy/include -Ihardware/ril/include -Iframeworks/native/include -Iframeworks/native/opengl/include -Iframeworks/av/include -isystem bionic/libc/include -isystem bionic/libc/kernel/uapi -isystem bionic/libc/kernel/uapi/asm-arm64 -isystem bionic/libc/kernel/android/scsi -isystem bionic/libc/kernel/android/uapi -Ilibnativehelper/include_jni -fno-rtti -ggdb3 -Wall -Werror -Wextra -Wstrict-aliasing -fstrict-aliasing -Wunreachable-code -Wredundant-decls -Wshadow -Wunused -fvisibility=protected -Wthread-safety -Wthread-safety-negative -Wimplicit-fallthrough -Wfloat-equal -Wint-to-void-pointer-cast -Wused-but-marked-unused -Wdeprecated -Wunreachable-code-break -Wunreachable-code-return -Wno-invalid-offsetof -Winconsistent-missing-override -D_LIBCPP_ENABLE_THREAD_SAFETY_ANNOTATIONS -O3 -DART_DEFAULT_GC_TYPE_IS_CMS -DIMT_SIZE=43 -DART_USE_READ_BARRIER=1 -DART_READ_BARRIER_TYPE_IS_BAKER=1 -DART_USE_GENERATIONAL_CC=1 -DART_DEFAULT_COMPACT_DEX_LEVEL=fast -DART_STACK_OVERFLOW_GAP_arm=8192 -DART_STACK_OVERFLOW_GAP_arm64=8192 -DART_STACK_OVERFLOW_GAP_mips=16384 -DART_STACK_OVERFLOW_GAP_mips64=16384 -DART_STACK_OVERFLOW_GAP_x86=8192 -DART_STACK_OVERFLOW_GAP_x86_64=8192 -DUSE_D8_DESUGAR=1 -DBUILDING_LIBART=1 -Wmissing-noreturn -DART_TARGET -DART_ENABLE_CODEGEN_arm -DART_ENABLE_CODEGEN_arm64 -Wframe-larger-than=1736 -DART_FRAME_SIZE_LIMIT=1736 -DART_BASE_ADDRESS=0x70000000 -DART_TARGET_ANDROID -DART_BASE_ADDRESS_MIN_DELTA=-0x1000000 -DART_BASE_ADDRESS_MAX_DELTA=0x1000000 -DANDROID_LINK_SHARED_ICU4C -target aarch64-linux-android -Bprebuilts/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/aarch64-linux-android/bin -DANDROID_STRICT -fPIC -D_USING_LIBCXX -flto=thin -fsplit-lto-unit -std=gnu++17 -Wsign-promo -D_LIBCPP_ENABLE_THREAD_SAFETY_ANNOTATIONS -Wno-thread-safety-negative -Wno-gnu-include-next -fvisibility-inlines-hidden -fno-rtti  -Werror=int-to-pointer-cast -Werror=pointer-to-int-cast -Werror=address-of-temporary -Werror=return-type -Wno-tautological-constant-compare -Wno-tautological-type-limit-compare -Wno-tautological-unsigned-enum-zero-compare -Wno-tautological-unsigned-zero-compare -Wno-c++98-compat-extra-semi -Wno-return-std-move-in-c++11 -MD -MF out/soong/.intermediates/art/runtime/libart/android_arm64_armv8-a_cortex-a73_core_shared_com.android.runtime.release/obj/art/runtime/art_method.o.d -o out/soong/.intermediates/art/runtime/libart/android_arm64_armv8-a_cortex-a73_core_shared_com.android.runtime.release/obj/art/runtime/art_method.o art/runtime/art_method.cc
art/runtime/art_method.cc:964:26: error: use of undeclared identifier 'PrettyMethod'
                      << PrettyMethod(artmethod).
                         ^
art/runtime/art_method.cc:973:50: error: 'open' has superfluous mode bits; missing O_CREAT? [-Werror,-Wuser-defined-warnings]
        fcmdline = open(szCmdline, O_RDONLY, 0644);
                                                 ^
bionic/libc/include/bits/fortify/fcntl.h:69:9: note: from 'diagnose_if' attribute on 'open':
        __clang_warning_if(!__open_modes_useful(flags) && modes,
        ^~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
bionic/libc/include/sys/cdefs.h:134:54: note: expanded from macro '__clang_warning_if'
#define __clang_warning_if(cond, msg) __attribute__((diagnose_if(cond, msg, "warning")))
                                                     ^           ~~~~
art/runtime/art_method.cc:1001:61: error: 'open' has superfluous mode bits; missing O_CREAT? [-Werror,-Wuser-defined-warnings]
            int dexfilefp = open(dexfilepath, O_RDONLY, 0666);
                                                            ^
bionic/libc/include/bits/fortify/fcntl.h:69:9: note: from 'diagnose_if' attribute on 'open':
        __clang_warning_if(!__open_modes_useful(flags) && modes,
        ^~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
bionic/libc/include/sys/cdefs.h:134:54: note: expanded from macro '__clang_warning_if'
#define __clang_warning_if(cond, msg) __attribute__((diagnose_if(cond, msg, "warning")))
                                                     ^           ~~~~
art/runtime/art_method.cc:1029:26: error: use of undeclared identifier 'PrettyMethod'
                      << PrettyMethod(artmethod).
                         ^
art/runtime/art_method.cc:1038:50: error: 'open' has superfluous mode bits; missing O_CREAT? [-Werror,-Wuser-defined-warnings]
        fcmdline = open(szCmdline, O_RDONLY, 0644);
                                                 ^
bionic/libc/include/bits/fortify/fcntl.h:69:9: note: from 'diagnose_if' attribute on 'open':
        __clang_warning_if(!__open_modes_useful(flags) && modes,
        ^~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
bionic/libc/include/sys/cdefs.h:134:54: note: expanded from macro '__clang_warning_if'
#define __clang_warning_if(cond, msg) __attribute__((diagnose_if(cond, msg, "warning")))
                                                     ^           ~~~~
art/runtime/art_method.cc:1048:21: error: use of undeclared identifier 'PrettyMethod'
                    PrettyMethod(artmethod).c_str();
                    ^
art/runtime/art_method.cc:1068:61: error: 'open' has superfluous mode bits; missing O_CREAT? [-Werror,-Wuser-defined-warnings]
            int dexfilefp = open(dexfilepath, O_RDONLY, 0666);
                                                            ^
bionic/libc/include/bits/fortify/fcntl.h:69:9: note: from 'diagnose_if' attribute on 'open':
        __clang_warning_if(!__open_modes_useful(flags) && modes,
        ^~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
bionic/libc/include/sys/cdefs.h:134:54: note: expanded from macro '__clang_warning_if'
#define __clang_warning_if(cond, msg) __attribute__((diagnose_if(cond, msg, "warning")))
                                                     ^           ~~~~
art/runtime/art_method.cc:1081:19: error: no type named 'CodeItem' in 'art::DexFile'; did you mean 'dex::CodeItem'?
            const DexFile::CodeItem * code_item = artmethod->GetCodeItem();
                  ^~~~~~~~~~~~~~~~~
                  dex::CodeItem
art/libdexfile/dex/class_accessor.h:29:8: note: 'dex::CodeItem' declared here
struct CodeItem;
       ^
art/runtime/art_method.cc:1085:32: error: no member named 'tries_size_' in 'art::dex::CodeItem'
                if (code_item->tries_size_ > 0) {
                    ~~~~~~~~~  ^
art/runtime/art_method.cc:1086:114: error: no member named 'tries_size_' in 'art::dex::CodeItem'
                    const uint8_t *handler_data = (const uint8_t *) (DexFile::GetTryItems(*code_item, code_item->tries_size_));
                                                                                                      ~~~~~~~~~  ^
art/runtime/art_method.cc:1090:53: error: no member named 'insns_size_in_code_units_' in 'art::dex::CodeItem'
                    code_item_len = 16 + code_item->insns_size_in_code_units_ * 2;
                                         ~~~~~~~~~  ^
art/runtime/art_method.cc:1142:23: error: redefinition of 'FromReflectedMethod'
ArtMethod *ArtMethod::FromReflectedMethod(const ScopedObjectAccessAlreadyRunnable &soa, jobject jlr_method) {
                      ^
art/runtime/art_method.cc:128:23: note: previous definition is here
ArtMethod* ArtMethod::FromReflectedMethod(const ScopedObjectAccessAlreadyRunnable& soa,
                      ^
art/runtime/art_method.cc:1143:67: error: expected expression
    auto *abstract_method = soa.Decode < mirror::AbstractMethod * >(jlr_method);
                                                                  ^
art/runtime/art_method.cc:1143:50: error: no member named 'AbstractMethod' in namespace 'art::mirror'
    auto *abstract_method = soa.Decode < mirror::AbstractMethod * >(jlr_method);
                                         ~~~~~~~~^
art/runtime/art_method.cc:1143:68: error: expected unqualified-id
    auto *abstract_method = soa.Decode < mirror::AbstractMethod * >(jlr_method);
                                                                   ^
15 errors generated.
[ 19% 1097/5705] Building Kernel Config
make: Entering directory '/mnt/case_sensitive/lineage-17.1/kernel/xiaomi/sdm660'
make[1]: Entering directory '/mnt/case_sensitive/lineage-17.1/out/target/product/wayne/obj/KERNEL_OBJ'
  GEN     ./Makefile
  HOSTCC  scripts/basic/fixdep
  HOSTCC  scripts/basic/bin2c
  HOSTCC  scripts/kconfig/conf.o
clang-9: warning: argument unused during compilation: '-fuse-ld=lld' [-Wunused-command-line-argument]
  HOSTCC  scripts/kconfig/zconf.tab.o
clang-9: warning: argument unused during compilation: '-fuse-ld=lld' [-Wunused-command-line-argument]
  HOSTLD  scripts/kconfig/conf
#
# configuration written to .config
#
make[1]: Leaving directory '/mnt/case_sensitive/lineage-17.1/out/target/product/wayne/obj/KERNEL_OBJ'
make: Leaving directory '/mnt/case_sensitive/lineage-17.1/kernel/xiaomi/sdm660'
make: Entering directory '/mnt/case_sensitive/lineage-17.1/kernel/xiaomi/sdm660'
make[1]: Entering directory '/mnt/case_sensitive/lineage-17.1/out/target/product/wayne/obj/KERNEL_OBJ'
  GEN     ./Makefile
scripts/kconfig/conf  --savedefconfig=defconfig Kconfig
make[1]: Leaving directory '/mnt/case_sensitive/lineage-17.1/out/target/product/wayne/obj/KERNEL_OBJ'
make: Leaving directory '/mnt/case_sensitive/lineage-17.1/kernel/xiaomi/sdm660'
[ 19% 1106/5669] //libcore/mmodules/core_platform_api:core-platform-api-stubs Metalava [common]

/mnt/case_sensitive/lineage-17.1/external/bouncycastle/repackaged/bcprov/src/main/java/com/android/org/bouncycastle/asn1/x500/style/BCStyle.java:17: warning: Public class com.android.org.bouncycastle.asn1.x500.style.BCStyle stripped of unavailable superclass com.android.org.bouncycastle.asn1.x500.style.AbstractX500NameStyle [HiddenSuperclass]
23:34:38 ninja failed with: exit status 1

#### failed to build some targets (10:08 (mm:ss)) ####
```


## 1. 未声明的标识符：PrettyMethod



把 FART 中 PrettyMethod 的调用

```
LOG(INFO) << "ArtMethod::dumpDexFileByExecute,methodname:" << PrettyMethod(artmethod).c_str() << "malloc 2000 byte failed";

LOG(INFO) << "ArtMethod::dumpArtMethodinvoked,methodname:" << PrettyMethod(artmethod).c_str() << "malloc 2000 byte failed";

const char *methodname = PrettyMethod(artmethod).c_str();

PrettyMethod(shadow_frame.GetMethod()).c_str()
```
修改如下

```
LOG(INFO) << "ArtMethod::dumpDexFileByExecute,methodname:" << artmethod->PrettyMethod().c_str() << "malloc 2000 byte failed";

LOG(INFO) << "ArtMethod::dumpArtMethodinvoked,methodname:" << artmethod->PrettyMethod().c_str() << "malloc 2000 byte failed";

const char *methodname = artmethod->PrettyMethod().c_str();

shadow_frame.GetMethod()->PrettyMethod().c_str()
```


## 2. open 函数带有多余 mode 参数



```
error: 'open' has superfluous mode bits; missing O_CREAT? [-Werror,-Wuser-defined-warnings]
```
这个意思是你使用了 open 函数时传了第三个参数（mode），但没有设置 O_CREAT 标志，而 mode 参数只有在创建新文件时才有意义。



在较老版本（如 Android 6.0）的编译器中，这类用法：

```
int fd = open("xxx", O_RDONLY, 0644);  // 没有 O_CREAT，却传了 mode
```
虽然语义上多余，但不会报错或中断构建。



而在 Android 10（Q）以后，AOSP 使用了更严格的 Clang，并开启了如下 warning：

```
-Wuser-defined-warnings
```
然后又通过 -Werror 将这些警告强制为错误，所以编译器现在直接拒绝这类写法。



如果你只是读文件（O_RDONLY），就不要写第三个参数：

```
int fd = open("file.txt", O_RDONLY);
```


修改前：

```
fcmdline = open(szCmdline, O_RDONLY, 0644);
int dexfilefp = open(dexfilepath, O_RDONLY, 0666);
```
修改后：

```
fcmdline = open(szCmdline, O_RDONLY);
int dexfilefp = open(dexfilepath, O_RDONLY);
```


## 3. DexFile::CodeItem 类型名错误



这是因为新版代码中 CodeItem 定义在 dex 命名空间中，而不是 art::DexFile。



修改前：

```
const DexFile::CodeItem* code_item = artmethod->GetCodeItem();
```


修改后：

```
const dex::CodeItem* code_item = artmethod->GetCodeItem();
```


## 4. CodeItem 无 tries_size_ 和 insns_size_in_code_units_ 成员



```
art/runtime/art_method.cc:1076:32: error: no member named 'tries_size_' in 'art::dex::CodeItem'
                if (code_item->tries_size_ > 0) {
                    ~~~~~~~~~  ^
art/runtime/art_method.cc:1077:114: error: no member named 'tries_size_' in 'art::dex::CodeItem'
                    const uint8_t *handler_data = (const uint8_t *) (DexFile::GetTryItems(*code_item, code_item->tries_size_));
                                                                                                      ~~~~~~~~~  ^
art/runtime/art_method.cc:1081:53: error: no member named 'insns_size_in_code_units_' in 'art::dex::CodeItem'
                    code_item_len = 16 + code_item->insns_size_in_code_units_ * 2;
                                         ~~~~~~~~~  ^
```


从 Android 9（Pie）开始，ART 对 Dex 解析部分的内部结构进行了重构，CodeItem 成员变成私有的或受限访问，不能再直接通过 code_item->tries_size_ 或 code_item->insns_size_in_code_units_ 来访问了。



应该使用官方提供的辅助类 CodeItemDataAccessor 访问：

```
const DexFile *dex_file = artmethod->GetDexFile();

// 获取 CodeItem
const dex::CodeItem* code_item = ...;

// 创建 DataAccessor
CodeItemDataAccessor accessor(*dex_file, code_item);

// 正确访问 tries_size 和 insns_size_in_code_units
uint32_t tries_size = accessor.TriesSize();
uint32_t insns_size = accessor.InsnsSizeInCodeUnits();
```


## 5. DexFile::GetTryItems



报错日志如下：

```
art/runtime/art_method.cc:1071:91: error: no viable conversion from 'const dex::CodeItem' to 'const art::DexInstructionIterator'
                    const uint8_t *handler_data = (const uint8_t *) (DexFile::GetTryItems(*code_item, accessor.TriesSize()));
```


在 Android 9 之后，应该用 CodeItemDataAccessor::GetTries() 来访问 tries 数组，用 accessor.GetCatchHandlerData() 来访问 handler 。



把

```
 const uint8_t *handler_data = (const uint8_t *) (DexFile::GetTryItems(*code_item, accessor.TriesSize()));
```
改为

```
const uint8_t* handler_data = accessor.GetCatchHandlerData();
```


## 6. mirror::AbstractMethod 不存在



```
art/runtime/art_method.cc:1128:67: error: expected expression
    auto *abstract_method = soa.Decode < mirror::AbstractMethod * >(jlr_method);
                                                                  ^
art/runtime/art_method.cc:1128:50: error: no member named 'AbstractMethod' in namespace 'art::mirror'
    auto *abstract_method = soa.Decode < mirror::AbstractMethod * >(jlr_method);
                                         ~~~~~~~~^
art/runtime/art_method.cc:1128:68: error: expected unqualified-id
    auto *abstract_method = soa.Decode < mirror::AbstractMethod * >(jlr_method);
```
说明：

- Android 6.0 中有 mirror::AbstractMethod；

- Android 10 中已经移除或合并进 Executable / Method 等类型。



修复方式：使用 mirror::Executable（它是 ART 中 java.lang.reflect.Method 和 Constructor 的基类）代替。

```
ArtMethod *ArtMethod::FromReflectedMethod(const ScopedObjectAccessAlreadyRunnable &soa, jobject jlr_method) {
    ObjPtr<mirror::Executable> executable = soa.Decode<mirror::Executable>(jlr_method);
    DCHECK(executable != nullptr);
    return executable->GetArtMethod();
}
```


## 7. 'jni_internal.h' file not found



报错日志如下：

```
art/runtime/native/dalvik_system_DexFile.cc:57:10: fatal error: 'jni_internal.h' file not found
#include "jni_internal.h"
         ^~~~~~~~~~~~~~~~
```


把

```
#include "jni_internal.h"
```
替换为

```
#include "jni/jni_internal.h"
```


## 8. ignoring return value of function declared with 'warn_unused_result'



```
art/runtime/art_method.cc:965:13: error: ignoring return value of function declared with 'warn_unused_result' attribute [-Werror,-Wunused-result]
            read(fcmdline, szProcName, 256);
            ^~~~ ~~~~~~~~~~~~~~~~~~~~~~~~~
art/runtime/art_method.cc:998:21: error: ignoring return value of function declared with 'warn_unused_result' attribute [-Werror,-Wunused-result]
                    write(dexfilefp, (void *) begin_,
                    ^~~~~ ~~~~~~~~~~~~~~~~~~~~~~~~~~~
art/runtime/art_method.cc:1025:13: error: ignoring return value of function declared with 'warn_unused_result' attribute [-Werror,-Wunused-result]
            read(fcmdline, szProcName, 256);
            ^~~~ ~~~~~~~~~~~~~~~~~~~~~~~~~
art/runtime/art_method.cc:1060:21: error: ignoring return value of function declared with 'warn_unused_result' attribute [-Werror,-Wunused-result]
                    write(dexfilefp, (void *) begin_, size_);
                    ^~~~~ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
art/runtime/art_method.cc:1094:21: error: ignoring return value of function declared with 'warn_unused_result' attribute [-Werror,-Wunused-result]
                    write(fp2, (void *) dexfilepath, contentlength);
                    ^~~~~ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
art/runtime/art_method.cc:1097:21: error: ignoring return value of function declared with 'warn_unused_result' attribute [-Werror,-Wunused-result]
                    write(fp2, base64result, outlen);
                    ^~~~~ ~~~~~~~~~~~~~~~~~~~~~~~~~
art/runtime/art_method.cc:1098:21: error: ignoring return value of function declared with 'warn_unused_result' attribute [-Werror,-Wunused-result]
                    write(fp2, "};", 2);
                    ^~~~~ ~~~~~~~~~~~~
7 errors generated.
```
这些函数都被标记了 __attribute__((warn_unused_result))，表示返回值必须使用。Clang 编译器将它们视为错误（因为加了

-Werror），所以编译失败了。



处理返回值， 比如：

```
// 原来的写法（错误）
read(fcmdline, szProcName, 256);

// 处理返回值
ssize_t bytes_read = read(fcmdline, szProcName, 256);
if (bytes_read < 0) {
    LOG(INFO) << "read failed";
}


ssize_t written = write(dexfilefp, begin_, size_);
LOG(INFO) << "write to file: " << dexfilepath << ", written = " << written << ", expected = " << size_;

ssize_t written = write(dexfilefp, (void *) begin_, size_);
LOG(INFO) << "write to file: " << dexfilepath << ", written = " << written << ", expected = " << size_;
 
ssize_t written = write(fp2, (void *) dexfilepath, contentlength);
LOG(INFO) << "write to file: " << dexfilepath << ", written = " << written << ", expected = " << contentlength;

ssize_t written_fp2 = write(fp2, base64result, outlen);
LOG(INFO) << "written_fp2: "<< ", written = " << written_fp2 << ", expected = " << outlen;

ssize_t written_fp2_end = write(fp2, "};", 2);
LOG(INFO) << "written_fp2_end: " << ", written = " << written_fp2_end << ", expected = " << 2;
```




## 9. 找不到 ScopedFastNativeObjectAccess



```
ld.lld: error: undefined symbol: art::ScopedFastNativeObjectAccess::ScopedFastNativeObjectAccess(_JNIEnv*)
>>> referenced by dalvik_system_DexFile.cc:72 (art/runtime/native/dalvik_system_DexFile.cc:72)
>>>               lto.tmp:(art::DexFile_dumpMethodCode(_JNIEnv*, _jclass*, _jobject*))
```
链接器找不到 ScopedFastNativeObjectAccess 构造函数的实现。



把

```
#include "scoped_fast_native_object_access.h"
```
替换为

```
#include "scoped_fast_native_object_access-inl.h"
```


## 10. no member named 'GetDexMethodIndexUnchecked'



```
art/runtime/art_method.cc:359:52: error: no member named 'GetDexMethodIndexUnchecked' in 'art::ArtMethod'
                    uint32_t method_idx=artmethod->GetDexMethodIndexUnchecked();
                                        ~~~~~~~~~  ^
```


把

```
uint32_t method_idx = artmethod->GetDexMethodIndexUnchecked();
```
改为

```
uint32_t method_idx = artmethod->GetDexMethodIndex();
```


# 编译完成





![word/media/image3.png](https://gitee.com/cyrus-studio/images/raw/master/81752a0e3f85109e441df4db30f934db.png)


执行 ls -hl 可以看到目录下已经生成了 signed-ota_update.zip



![word/media/image4.png](https://gitee.com/cyrus-studio/images/raw/master/8d5752489c659c54662beeeecab86ceb.png)


# 刷机



由于我这里是在 WSL 中编译，先把 ota 文件 copy 到 windwos 目录下

```
 cp ./signed-ota_update.zip /mnt/e/lineageos/xiaomi6x_wayne_lineageos-17.1_signed-ota_update_fart.zip
```


设备进入 recovery 模式（或者同时按住【音量+】和【开机键】）

```
adb reboot recovery
```


【Apply update】【Apply from adb】开启 adb sideload



![word/media/image5.png](https://gitee.com/cyrus-studio/images/raw/master/01322abedf864f46d77fbe411f9ab975.png)


开始刷机

```
adb sideload E:\lineageos\xiaomi6x_wayne_lineageos-17.1_signed-ota_update_fart.zip
```


成功刷入后重启手机。



参考：[LineageOS刷机教程](https://cyrus-studio.github.io/blog/posts/lineageos%E5%88%B7%E6%9C%BA%E6%95%99%E7%A8%8B/)



# 自动化脱壳



如果 app 没有存储权限，先授予存储卡读写权限



![word/media/image6.png](https://gitee.com/cyrus-studio/images/raw/master/0e1db4835d1a0f8dbe7528c432642cb8.png)


打印 app 日志

```
adb logcat --pid=$(adb shell pidof com.cyrus.example)
```


打开 app，等待60秒后开始完整的主动调用，可以看到每个调用到的 dex 函数



![word/media/image7.png](https://gitee.com/cyrus-studio/images/raw/master/52edd19770b0d5b2fb3ba191ac909748.png)


通过下面的命令过滤出 fart 日志

- linux / mac：

```
adb logcat | grep fart
```
- windodws：

```
adb logcat | Select-String fart
```
出现 fart over就是脱壳完成了



抽取壳的应用脱壳完成后，在 /sdcard/fart/packageName 目录下可以找到如下文件：

```
wayne:/sdcard/fart/com.cyrus.example $ ls
10637856_classlist.txt         1321896_dexfile_execute.dex 1472352_9399.bin            198768_11648.bin
10637856_classlist_execute.txt 1321896_ins_7860.bin        1472352_9496.bin            198768_16643.bin
10637856_dexfile.dex           1321896_ins_7971.bin        1472352_9651.bin            198768_16667.bin
10637856_dexfile_execute.dex   1351008_10667.bin           1472352_9754.bin            198768_16826.bin
10637856_ins_5496.bin          1351008_10739.bin           1472352_dexfile.dex         198768_17570.bin
10637856_ins_5523.bin          1351008_10869.bin           1472352_dexfile_execute.dex 198768_17704.bin
10637856_ins_5762.bin          1351008_11525.bin           1472352_ins_7860.bin        198768_20150.bin
10637856_ins_5945.bin          1351008_11648.bin           1472352_ins_7971.bin        198768_9399.bin
10637856_ins_6217.bin          1351008_16643.bin           1481472_10667.bin           198768_9496.bin
11125808_classlist.txt         1351008_16667.bin           1481472_10739.bin           198768_9651.bin
11125808_classlist_execute.txt 1351008_16826.bin           1481472_10869.bin           198768_9754.bin
11125808_dexfile.dex           1351008_17570.bin           1481472_11525.bin           198768_dexfile.dex
11125808_dexfile_execute.dex   1351008_17704.bin           1481472_11648.bin           198768_dexfile_execute.dex
```


其中

- *_dexfile_execute.dex 是从 Execute 方法中 dump 下来的 dex 文件；

- *_dexfile.dex 是从 ArtMethod::Invoke 方法中 dump 下来的 dex 文件；

- *_classlist.txt 或 *_classlist_execute.txt 是 dex 的类列表；

- *.bin 是从 ArtMethod::Invoke 方法中 dump 下来的方法的 CodeItem；



把整个目录拉取到本地

```
adb pull /sdcard/fart/com.cyrus.example
```


# sprintf 缓冲区溢出



在测试某个电商 app 脱壳时偶现报错如下：

```
2025-05-23 21:15:46.790 13890-16106 libc                    pid-13890                            A  FORTIFY: vsprintf: prevented 1026-byte write into 1000-byte buffer
2025-05-23 21:15:46.872 13890-16084 libc                    pid-13890                            A  FORTIFY: vsprintf: prevented 1026-byte write into 1000-byte buffer
2025-05-23 21:15:52.339 13890-16278 libc                    pid-13890                            A  FORTIFY: vsprintf: prevented 1026-byte write into 1000-byte buffer
2025-05-23 21:15:52.340 13890-16278 libc                    pid-13890                            A  Fatal signal 6 (SIGABRT), code -1 (SI_QUEUE) in tid 16278 (Thread-14), pid 13890 (shizhuang.duapp)
2025-05-23 21:15:52.542 16906-16906 DEBUG                   pid-16906                            A  *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
2025-05-23 21:15:52.542 16906-16906 DEBUG                   pid-16906                            A  LineageOS Version: '17.1-20250523-UNOFFICIAL-wayne'
2025-05-23 21:15:52.542 16906-16906 DEBUG                   pid-16906                            A  Build fingerprint: 'xiaomi/wayne/wayne:8.1.0/OPM1.171019.011/V9.5.11.0.ODCCNFA:user/release-keys'
2025-05-23 21:15:52.542 16906-16906 DEBUG                   pid-16906                            A  Revision: '0'
2025-05-23 21:15:52.542 16906-16906 DEBUG                   pid-16906                            A  ABI: 'arm64'
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A  Timestamp: 2025-05-23 21:15:52+0800
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A  pid: 13890, tid: 16278, name: Thread-14  >>> com.shizhuang.duapp <<<
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A  uid: 10139
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A  signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A  Abort message: 'FORTIFY: vsprintf: prevented 1026-byte write into 1000-byte buffer'
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A      x0  0000000000000000  x1  0000000000003f96  x2  0000000000000006  x3  00000071e127a3c0
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A      x4  fefefefefeff7164  x5  fefefefefeff7164  x6  fefefefefeff7164  x7  7f7f7f7f7f7f7f7f
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A      x8  00000000000000f0  x9  00000075b873c4a0  x10 0000000000000000  x11 0000000000000001
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A      x12 0000000000000018  x13 0000001b26f96dc9  x14 0014343f445098a4  x15 00000000002d0120
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A      x16 00000075b88098c0  x17 00000075b87e7310  x18 00000071ddc8e000  x19 00000000000000ac
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A      x20 0000000000003642  x21 00000000000000b2  x22 0000000000003f96  x23 00000000ffffffff
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A      x24 000000000000007c  x25 000000000000c685  x26 00000000000003e8  x27 00000071e127c020
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A      x28 0000007537de0000  x29 00000071e127a470
2025-05-23 21:15:52.543 16906-16906 DEBUG                   pid-16906                            A      sp  00000071e127a3a0  lr  00000075b879a170  pc  00000075b879a1a0
2025-05-23 21:15:52.698 16906-16906 DEBUG                   pid-16906                            A  
                                                                                                    backtrace:
2025-05-23 21:15:52.698 16906-16906 DEBUG                   pid-16906                            A        #00 pc 00000000000821a0  /apex/com.android.runtime/lib64/bionic/libc.so (abort+176) (BuildId: a5aa1dd8572ed64645c321b17b43e24d)
2025-05-23 21:15:52.698 16906-16906 DEBUG                   pid-16906                            A        #01 pc 00000000000a92bc  /apex/com.android.runtime/lib64/bionic/libc.so (__fortify_fatal(char const*, ...)+116) (BuildId: a5aa1dd8572ed64645c321b17b43e24d)
2025-05-23 21:15:52.698 16906-16906 DEBUG                   pid-16906                            A        #02 pc 00000000000a9f70  /apex/com.android.runtime/lib64/bionic/libc.so (__vsprintf_chk+144) (BuildId: a5aa1dd8572ed64645c321b17b43e24d)
2025-05-23 21:15:52.698 16906-16906 DEBUG                   pid-16906                            A        #03 pc 00000000001448d4  /apex/com.android.runtime/lib64/libart.so (_ZL7sprintfPcU17pass_object_size1PKcz+124) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.698 16906-16906 DEBUG                   pid-16906                            A        #04 pc 0000000000144d74  /apex/com.android.runtime/lib64/libart.so (dumpArtMethod+1140) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.698 16906-16906 DEBUG                   pid-16906                            A        #05 pc 000000000004944c  /system/framework/arm64/boot-core-libart.oat (art_jni_trampoline+172) (BuildId: ec978110cc23b14d9f94b2304a344f21a20e848d)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #06 pc 00000000001375b8  /apex/com.android.runtime/lib64/libart.so (art_quick_invoke_static_stub+568) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #07 pc 0000000000145374  /apex/com.android.runtime/lib64/libart.so (art::ArtMethod::Invoke(art::Thread*, unsigned int*, unsigned int, art::JValue*, char const*)+292) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #08 pc 00000000004b2fe8  /apex/com.android.runtime/lib64/libart.so (art::(anonymous namespace)::InvokeWithArgArray(art::ScopedObjectAccessAlreadyRunnable const&, art::ArtMethod*, art::(anonymous namespace)::ArgArray*, art::JValue*, char const*)+104) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #09 pc 00000000004b4a30  /apex/com.android.runtime/lib64/libart.so (art::InvokeMethod(art::ScopedObjectAccessAlreadyRunnable const&, _jobject*, _jobject*, _jobject*, unsigned long)+1472) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #10 pc 00000000004406c0  /apex/com.android.runtime/lib64/libart.so (art::Method_invoke(_JNIEnv*, _jobject*, _jobject*, _jobjectArray*)+48) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #11 pc 00000000000bfc34  /system/framework/arm64/boot.oat (art_jni_trampoline+180) (BuildId: 9180a0d3ab0acb7f4675c6605dd909d40007fd68)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #12 pc 00000000021b619c  /memfd:/jit-cache (deleted) (android.app.ActivityThread.loadClassAndInvoke+1148)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #13 pc 00000000021b6ddc  /memfd:/jit-cache (deleted) (android.app.ActivityThread.fartwithClassloader+2508)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #14 pc 00000000001375b8  /apex/com.android.runtime/lib64/libart.so (art_quick_invoke_static_stub+568) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #15 pc 0000000000145374  /apex/com.android.runtime/lib64/libart.so (art::ArtMethod::Invoke(art::Thread*, unsigned int*, unsigned int, art::JValue*, char const*)+292) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #16 pc 00000000002e4388  /apex/com.android.runtime/lib64/libart.so (art::interpreter::ArtInterpreterToCompiledCodeBridge(art::Thread*, art::ArtMethod*, art::ShadowFrame*, unsigned short, art::JValue*)+384) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #17 pc 00000000002df414  /apex/com.android.runtime/lib64/libart.so (bool art::interpreter::DoCall<false, false>(art::ArtMethod*, art::Thread*, art::ShadowFrame&, art::Instruction const*, unsigned short, art::JValue*)+900) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #18 pc 00000000005a5660  /apex/com.android.runtime/lib64/libart.so (MterpInvokeStatic+368) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.699 16906-16906 DEBUG                   pid-16906                            A        #19 pc 0000000000131994  /apex/com.android.runtime/lib64/libart.so (mterp_op_invoke_static+20) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #20 pc 0000000000181b24  /system/framework/framework.jar (android.app.ActivityThread.fart+44)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #21 pc 00000000005a5960  /apex/com.android.runtime/lib64/libart.so (MterpInvokeStatic+1136) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #22 pc 0000000000131994  /apex/com.android.runtime/lib64/libart.so (mterp_op_invoke_static+20) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #23 pc 000000000017998e  /system/framework/framework.jar (android.app.ActivityThread$2.run+46)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #24 pc 00000000002b4c2c  /apex/com.android.runtime/lib64/libart.so (_ZN3art11interpreterL7ExecuteEPNS_6ThreadERKNS_20CodeItemDataAccessorERNS_11ShadowFrameENS_6JValueEbb.llvm.5230655971336280632+340) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #25 pc 0000000000593fa0  /apex/com.android.runtime/lib64/libart.so (artQuickToInterpreterBridge+1024) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #26 pc 0000000000140468  /apex/com.android.runtime/lib64/libart.so (art_quick_to_interpreter_bridge+88) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #27 pc 00000000001a3088  /system/framework/arm64/boot.oat (java.lang.Thread.run+72) (BuildId: 9180a0d3ab0acb7f4675c6605dd909d40007fd68)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #28 pc 0000000000137334  /apex/com.android.runtime/lib64/libart.so (art_quick_invoke_stub+548) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #29 pc 0000000000145348  /apex/com.android.runtime/lib64/libart.so (art::ArtMethod::Invoke(art::Thread*, unsigned int*, unsigned int, art::JValue*, char const*)+248) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #30 pc 00000000004b2fe8  /apex/com.android.runtime/lib64/libart.so (art::(anonymous namespace)::InvokeWithArgArray(art::ScopedObjectAccessAlreadyRunnable const&, art::ArtMethod*, art::(anonymous namespace)::ArgArray*, art::JValue*, char const*)+104) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #31 pc 00000000004b4098  /apex/com.android.runtime/lib64/libart.so (art::InvokeVirtualOrInterfaceWithJValues(art::ScopedObjectAccessAlreadyRunnable const&, _jobject*, _jmethodID*, jvalue const*)+416) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #32 pc 00000000004f45e0  /apex/com.android.runtime/lib64/libart.so (art::Thread::CreateCallback(void*)+1176) (BuildId: a54f40638f14fad0786ba67b78899d39)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #33 pc 00000000000e35fc  /apex/com.android.runtime/lib64/bionic/libc.so (__pthread_start(void*)+36) (BuildId: a5aa1dd8572ed64645c321b17b43e24d)
2025-05-23 21:15:52.700 16906-16906 DEBUG                   pid-16906                            A        #34 pc 0000000000083d98  /apex/com.android.runtime/lib64/bionic/libc.so (__start_thread+64) (BuildId: a5aa1dd8572ed64645c321b17b43e24d)
```


触发崩溃的原因是：

```
FORTIFY: vsprintf: prevented 1026-byte write into 1000-byte buffer
```
触发了 Android 的 FORTIFY 安全机制，这个机制会检测标准库函数（如 sprintf / vsprintf）是否发生了缓冲区溢出。



在尝试写入超过目标缓冲区大小的内容（尝试写入 1026 字节，但缓冲区仅有 1000 字节），结果触发了 libc 中的 FORTIFY 检测机制，引发了 SIGABRT（abort）信号终止进程。



崩溃发生在 libart.so 中的 dumpArtMethod() 函数内部。



解决方案：使用 std::string 替代手动 malloc + sprintf 就好了。



调整 dumpArtMethod 代码如下：

```
//add
extern "C" void dumpArtMethod(ArtMethod* artmethod) REQUIRES_SHARED(Locks::mutator_lock_) {
    char szProcName[256] = {0};
    int procid = getpid();

    // 获取进程名
    char szCmdline[64] = {0};
    snprintf(szCmdline, sizeof(szCmdline), "/proc/%d/cmdline", procid);
    int fcmdline = open(szCmdline, O_RDONLY);
    if (fcmdline > 0) {
        ssize_t result = read(fcmdline, szProcName, sizeof(szProcName) - 1);
        if (result < 0) {
            LOG(ERROR) << "ArtMethod::dumpArtMethod: read cmdline failed.";
        }
        close(fcmdline);
    }

    if (szProcName[0] == '\0') return;

    const DexFile* dex_file = artmethod->GetDexFile();
    const uint8_t* begin_ = dex_file->Begin();
    size_t size_ = dex_file->Size();
    int size_int = static_cast<int>(size_);

    // 路径拼接
    std::string baseDir = "/sdcard/fart/";
    std::string processDir = baseDir + szProcName;
    mkdir(baseDir.c_str(), 0777);
    mkdir(processDir.c_str(), 0777);

    // 保存 dex 文件
    std::string dexPath = processDir + "/" + std::to_string(size_int) + "_dexfile.dex";
    int dexfilefp = open(dexPath.c_str(), O_RDONLY);
    if (dexfilefp > 0) {
        close(dexfilefp);
    } else {
        int fp = open(dexPath.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
        if (fp > 0) {
            ssize_t w = write(fp, begin_, size_);
            if (w < 0) {
                LOG(ERROR) << "ArtMethod::dumpArtMethod: write dexfile failed -> " << dexPath;
            }
            fsync(fp);
            close(fp);

            // 保存 class 列表
            std::string classListPath = processDir + "/" + std::to_string(size_int) + "_classlist.txt";
            int classlistfile = open(classListPath.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
            if (classlistfile > 0) {
                for (size_t i = 0; i < dex_file->NumClassDefs(); ++i) {
                    const dex::ClassDef& class_def = dex_file->GetClassDef(i);
                    const char* descriptor = dex_file->GetClassDescriptor(class_def);

                    ssize_t w1 = write(classlistfile, descriptor, strlen(descriptor));
                    if (w1 < 0) {
                        LOG(ERROR) << "ArtMethod::dumpArtMethod: write class descriptor failed";
                    }

                    ssize_t w2 = write(classlistfile, "\n", 1);
                    if (w2 < 0) {
                        LOG(ERROR) << "ArtMethod::dumpArtMethod: write newline failed";
                    }
                }
                fsync(classlistfile);
                close(classlistfile);
            }
        }
    }

    // 保存指令码
    const dex::CodeItem* code_item = artmethod->GetCodeItem();
    if (LIKELY(code_item != nullptr)) {
        uint8_t* item = (uint8_t*)code_item;
        int code_item_len = 0;
        CodeItemDataAccessor accessor(*dex_file, code_item);
        if (accessor.TriesSize() > 0) {
            const uint8_t* handler_data = accessor.GetCatchHandlerData();
            uint8_t* tail = codeitem_end(&handler_data);
            code_item_len = static_cast<int>(tail - item);
        } else {
            code_item_len = 16 + accessor.InsnsSizeInCodeUnits() * 2;
        }

        uint32_t method_idx = artmethod->GetDexMethodIndex();
        int offset = static_cast<int>(item - begin_);
        pid_t tid = gettidv1();
        std::string insPath = processDir + "/" + std::to_string(size_int) + "_ins_" + std::to_string(tid) + ".bin";

        int fp2 = open(insPath.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
        if (fp2 > 0) {
            lseek(fp2, 0, SEEK_END);
            std::string header = "{name:" + artmethod->PrettyMethod() +
                                 ",method_idx:" + std::to_string(method_idx) +
                                 ",offset:" + std::to_string(offset) +
                                 ",code_item_len:" + std::to_string(code_item_len) +
                                 ",ins:";

            ssize_t w3 = write(fp2, header.c_str(), header.length());
            if (w3 < 0) {
                LOG(ERROR) << "ArtMethod::dumpArtMethod: write header failed";
            }

            long outlen = 0;
            char* base64result = base64_encode((char*)item, (long)code_item_len, &outlen);
            if (base64result != nullptr) {
                ssize_t w4 = write(fp2, base64result, outlen);
                if (w4 < 0) {
                    LOG(ERROR) << "ArtMethod::dumpArtMethod: write base64 ins failed";
                }
                free(base64result);
            }

            ssize_t w5 = write(fp2, "};", 2);
            if (w5 < 0) {
                LOG(ERROR) << "ArtMethod::dumpArtMethod: write tail failed";
            }

            fsync(fp2);
            close(fp2);
        }
    }
}
```


dumpDexFileByExecute 函数同样调整如下：

```
//add
extern "C" void dumpDexFileByExecute(ArtMethod* artmethod) REQUIRES_SHARED(Locks::mutator_lock_) {
    char szCmdline[64] = {0};
    char szProcName[256] = {0};
    int procid = getpid();
    snprintf(szCmdline, sizeof(szCmdline), "/proc/%d/cmdline", procid);

    int fcmdline = open(szCmdline, O_RDONLY);
    if (fcmdline > 0) {
        ssize_t result = read(fcmdline, szProcName, sizeof(szProcName) - 1);
        if (result < 0) {
            LOG(ERROR) << "dumpDexFileByExecute: Failed to read cmdline";
        }
        close(fcmdline);
    }

    if (szProcName[0] == '\0') return;

    const DexFile* dex_file = artmethod->GetDexFile();
    const uint8_t* begin_ = dex_file->Begin();
    size_t size_ = dex_file->Size();
    int size_int = static_cast<int>(size_);

    std::string base_dir = "/sdcard/fart/";
    std::string app_dir = base_dir + szProcName;
    std::string dex_path = app_dir + "/" + std::to_string(size_int) + "_dexfile_execute.dex";
    std::string classlist_path = app_dir + "/" + std::to_string(size_int) + "_classlist_execute.txt";

    mkdir(base_dir.c_str(), 0777);
    mkdir(app_dir.c_str(), 0777);

    int dexfilefp = open(dex_path.c_str(), O_RDONLY);
    if (dexfilefp > 0) {
        close(dexfilefp);
    } else {
        int fp = open(dex_path.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
        if (fp > 0) {
            ssize_t w1 = write(fp, begin_, size_);
            if (w1 < 0) {
                LOG(ERROR) << "dumpDexFileByExecute: Failed to write dex file";
            }
            fsync(fp);
            close(fp);

            int classlistfile = open(classlist_path.c_str(), O_CREAT | O_APPEND | O_RDWR, 0666);
            if (classlistfile > 0) {
                for (size_t ii = 0; ii < dex_file->NumClassDefs(); ++ii) {
                    const dex::ClassDef& class_def = dex_file->GetClassDef(ii);
                    const char* descriptor = dex_file->GetClassDescriptor(class_def);
                    
                    ssize_t w2 = write(classlistfile, descriptor, strlen(descriptor));
                    if (w2 < 0) {
                        LOG(ERROR) << "dumpDexFileByExecute: Failed to write class descriptor";
                    }

                    ssize_t w3 = write(classlistfile, "\n", 1);
                    if (w3 < 0) {
                        LOG(ERROR) << "dumpDexFileByExecute: Failed to write newline";
                    }
                }
                fsync(classlistfile);
                close(classlistfile);
            }
        }
    }
}
```


# 完整源码



开源地址：[https://github.com/CYRUS-STUDIO/FART](https://github.com/CYRUS-STUDIO/FART)



相关文章：

- [将FART和Youpk结合来做一次针对函数抽取壳的全面提升](https://bbs.kanxue.com/thread-260052.htm)

- [https://github.com/CrackerCat/FartExt](https://github.com/CrackerCat/FartExt)

- [FartExt之优化更深主动调用的FART10](https://bbs.kanxue.com/thread-268760.htm)
