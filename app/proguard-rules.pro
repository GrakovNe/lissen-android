# Stack traces — keep source file names and line numbers
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson
-keepattributes Signature
-keepattributes AnnotationDefault,RuntimeVisibleAnnotations
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { <fields>; }
-keepclassmembers class **$TypeAdapterFactory { <fields>; }

# Moshi — keep @JsonClass-annotated classes and their generated adapters
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keep class **JsonAdapter { *; }

# Retrofit — keep suspend function coroutine continuation type
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Enums — keep constant names because Moshi EnumJsonAdapter uses Class.getField() by name
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final static ** *;
}

# Serializable — keep members used by Java serialization
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
