# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep navigation SafeArgs generated classes
-keepnames class * extends androidx.navigation.NavArgs

# Keep LiveData and ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep data classes
-keepclassmembers class com.magfi.mapper.core.** {
    *;
}
