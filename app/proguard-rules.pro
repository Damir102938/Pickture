# Retrofit, OkHttp, kotlinx.serialization и Room поставляют собственные правила R8.
# Здесь только то, что нужно приложению дополнительно.

# DTO из TMDB разбираются kotlinx.serialization — сохраняем их сериализаторы.
-keep,includedescriptorclasses class com.moviepicker.**$$serializer { *; }
-keepclassmembers class com.moviepicker.** {
    *** Companion;
}
-keepclasseswithmembers class com.moviepicker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit читает generic-типы возвращаемых значений интерфейса API.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep,allowobfuscation interface com.moviepicker.data.remote.TmdbApi
