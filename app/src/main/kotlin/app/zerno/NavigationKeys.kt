package app.zerno

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object CameraKey : NavKey

@Serializable data class EditorKey(val name: String) : NavKey

@Serializable data object GalleryKey : NavKey

@Serializable data object SettingsKey : NavKey
