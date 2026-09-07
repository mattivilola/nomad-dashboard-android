# Retrofit reads endpoint annotations when it creates service implementations.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature

# Kotlin serialization can resolve generated serializers through reified APIs
# used by cached dashboard payloads. Keep serializers while allowing the model
# names themselves to be optimized and obfuscated.
-keep,allowoptimization,allowobfuscation class **$$serializer { *; }
