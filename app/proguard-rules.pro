# --- JNI: keep classes with native methods and their names ---
# awxkee avif-coder/jxl-coder call native .so via JNI; renaming the Java
# class would break the Java_<pkg>_<Class>_<method> symbol lookup.
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- ViewModels: instantiated via reflection by the default ViewModelProvider factory ---
-keep class * extends androidx.lifecycle.ViewModel { *; }

# --- Kotlin Coroutines: keep names for ServiceLoader + readable stack traces ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
