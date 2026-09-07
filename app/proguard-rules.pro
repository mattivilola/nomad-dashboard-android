# Retrofit reads endpoint annotations when it creates service implementations.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature

# Kotlin serialization can resolve generated serializers through reified APIs
# used by cached dashboard payloads. Keep serializers while allowing the model
# names themselves to be optimized and obfuscated.
-keep,allowoptimization,allowobfuscation class **$$serializer { *; }

# Guava references these optional Objective-C translation annotations. They
# have no Android runtime behavior; R8 reports their absent annotation types.
-dontwarn com.google.j2objc.annotations.ReflectionSupport
-dontwarn com.google.j2objc.annotations.RetainedWith
