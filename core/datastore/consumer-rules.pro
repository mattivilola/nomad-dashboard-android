# Protobuf Lite resolves generated message fields from names embedded in its
# message-info strings. Preserve those fields during release optimization.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
