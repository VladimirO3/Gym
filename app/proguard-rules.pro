# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Retrofit/Gson response models are accessed reflectively in minified release builds.
-keep class com.business.gym_app.data.api.LoginResponse { *; }
-keep class com.business.gym_app.data.api.ProfileResponse { *; }