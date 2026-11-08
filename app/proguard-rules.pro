# Preserve ViewBinding references
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
}

# Keep Custom Grid View & Zoom View
-keep class com.example.workplacecamera.GridOverlayView { *; }
-keep class com.example.workplacecamera.ZoomableImageView { *; }

# Keep FileProvider for Sharing
-keep class androidx.core.content.FileProvider { *; }

# Keep CameraX internals
-keep class androidx.camera.core.** { *; }
-dontwarn androidx.camera.core.**
