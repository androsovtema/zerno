package app.zerno

import android.content.Context
import androidx.core.content.edit
import app.zerno.raw.Profile

/** Настройки пользователя. SharedPreferences достаточно: значений мало, читаем редко. */
class Prefs(context: Context) {
  private val sp = context.getSharedPreferences("zerno", Context.MODE_PRIVATE)

  var cameraKey: String?
    get() = sp.getString("camera", null)
    set(v) = sp.edit { putString("camera", v) }

  var profile: Profile
    get() = Profile.byName(sp.getString("profile", null))
    set(v) = sp.edit { putString("profile", v.name) }

  /** Дублировать каждый кадр файлом DNG в галерее (для сравнения в Lightroom и т. п.). */
  var saveDng: Boolean
    get() = sp.getBoolean("save_dng", false)
    set(v) = sp.edit { putBoolean("save_dng", v) }

  var shadingCorrection: Boolean
    get() = sp.getBoolean("shading", true)
    set(v) = sp.edit { putBoolean("shading", v) }

  var tipShown: Boolean
    get() = sp.getBoolean("tip_shown", false)
    set(v) = sp.edit { putBoolean("tip_shown", v) }
}
