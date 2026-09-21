# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Retrofit/Gson response models are accessed reflectively in minified release builds.
-keep class com.business.gym_app.data.api.** { *; }
-keep interface com.business.gym_app.ApiService { *; }
-keep class com.business.gym_app.data.model.Coach { *; }
-keep class com.business.gym_app.data.local.entity.** { *; }
-keep class com.business.gym_app.data.local.dao.** { *; }

# These models are serialized/deserialized manually by Gson.
-keep class com.business.gym_app.ui.viewmodel.Exercise { *; }
-keep class com.business.gym_app.ui.viewmodel.DailyWorkout { *; }