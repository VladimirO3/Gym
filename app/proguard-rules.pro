# Proguard rules for Gym App

# Suppress warnings
-dontwarn org.slf4j.impl.StaticLoggerBinder

# 1. Android Application Components & Services
-keep class com.business.gym_app.GymApplication { *; }
-keep class com.business.gym_app.MainActivity { *; }
-keep class com.business.gym_app.service.** { *; }
-keep class com.business.gym_app.receiver.** { *; }

# 2. WorkManager Workers (instantiated via reflection)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.business.gym_app.service.ChatCheckWorker { *; }

# 3. Room Database & DAOs & Entities (instantiated via reflection)
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.business.gym_app.data.local.** { *; }
-keep class com.business.gym_app.data.local.dao.** { *; }
-keep class com.business.gym_app.data.local.entity.** { *; }
-dontwarn androidx.room.paging.**

# 4. Gson / Retrofit / Serialization & Data Models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations

# Gson: TypeToken опирается на generic-сигнатуру анонимного подкласса.
# Без этих keep-правил R8 вырезает сигнатуру и падает
# "TypeToken must be created with a type argument" в release-сборке.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepattributes Signature

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
-keep class com.google.gson.** { *; }

-keep class com.business.gym_app.data.model.** { *; }
-keep class com.business.gym_app.data.api.** { *; }
-keep class com.business.gym_app.ui.viewmodel.** { *; }
-keep interface com.business.gym_app.ApiService { *; }

-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# 5. Ktor & OkHttp
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# 6. Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 7. Firebase & Play Services
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# 8. Biometric & Coil
-keep class androidx.biometric.** { *; }
-keep class coil.** { *; }
-dontwarn coil.**

# 9. Compose / Lifecycle (crash in release on fresh install:
# WrappedComposition.setContent -> LifecycleRegistry.addObserver ->
# AndroidComposeView.onAttachedToWindow).
# R8-агрессивная оптимизация + отсутствие keep-атрибутов ломает
# синтетические лямбды/классы Compose и обфусцирует стек так,
# что невозможно понять причину NPE (rewriteFrame removeInnerFrames).
# Сохраняем имена методов/строк и ключевые классы Compose.
-keepattributes SourceFile,LineNumberTable
-keep class androidx.compose.ui.platform.WrappedComposition { *; }
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }
-keep class androidx.compose.ui.platform.AbstractComposeView { *; }
-keep class androidx.compose.ui.platform.ComposeViewContext { *; }
-keep class androidx.compose.ui.platform.WindowRecomposer_androidKt { *; }
-keep class androidx.lifecycle.LifecycleRegistry { *; }
-keep class androidx.lifecycle.LifecycleRegistry$* { *; }
-keep class androidx.appcompat.app.AppCompatActivity { *; }
-keep class androidx.activity.ComponentActivity { *; }
