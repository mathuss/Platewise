# The Anthropic SDK ships its own consumer rules (META-INF/proguard/anthropic-java-core.pro)
# for Jackson/Kotlin reflection. These cover what its optional dependencies reference but
# Android doesn't have.
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn io.swagger.v3.oas.annotations.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
