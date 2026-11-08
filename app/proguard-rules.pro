# Preserve ViewBinding references
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
}

# Keep Custom Grid View
-keep class com.example.workplacecamera.GridOverlayView { *; }

# Keep CameraX internals
-keep class androidx.camera.core.** { *; }
-dontwarn androidx.camera.core.**
