package app.zerno.raw

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Инструмент отладки: проявляет настоящие DNG всеми профилями в PNG.
 * Запуск: ZERNO_DNG_DIR=/путь/к/dng ./gradlew testDebugUnitTest --tests '*DngDevelopTool*'
 * Без переменной окружения пропускается (в CI не выполняется).
 */
class DngDevelopTool {
  @Test
  fun developAll() {
    val dir = System.getenv("ZERNO_DNG_DIR")?.let(::File)
    assumeTrue("ZERNO_DNG_DIR не задан", dir != null && dir.isDirectory)
    val out = File(dir, "out").apply { mkdirs() }
    dir!!.listFiles { f -> f.extension.equals("dng", true) }!!.sorted().forEach { f ->
      val shot = runCatching { DngReader.read(f) }.getOrElse { println("SKIP ${f.name}: ${it.message}"); return@forEach }
      val shading = shot.shading
      println("DNG ${f.name}: ${shot.raw.width}x${shot.raw.height} ${shot.raw.cfa} black=${shot.raw.blackLevel.joinToString()} " +
        "white=${shot.raw.whiteLevel} neutral=${shot.meta.neutral?.joinToString { "%.3f".format(it) }} " +
        "cal=${shot.meta.calibrations.map { Illuminant.name(it.illuminant) }} shading=${shading?.let { "${it.cols}x${it.rows}" }}")
      shot.meta.calibrations.forEach { c ->
        println("  CM ${Illuminant.name(c.illuminant)}: ${c.colorMatrix}")
        c.forwardMatrix?.let { fm ->
          println("  FM: $fm placeholder=${ColorPipelineResolver.looksLikePlaceholderForwardMatrix(fm)}")
        }
      }
      for (strategy in listOf(ColorStrategy.AUTO, ColorStrategy.NO_MATRIX)) {
        val p = ColorPipelineResolver.resolve(shot.meta, strategy)
        for (profile in if (strategy == ColorStrategy.AUTO) Profile.entries else listOf(Profile.NATURAL)) {
          val ev = AutoExposure.compute(shot.raw, p, shading, profile)
          val t = System.nanoTime()
          val img = RawDeveloper.develop(shot.raw, p, shading, DevelopOptions(profile, ev, 4, shot.rotation))
          val ms = (System.nanoTime() - t) / 1_000_000
          val bi = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
          bi.setRGB(0, 0, img.width, img.height, img.argb, 0, img.width)
          val name = "${f.nameWithoutExtension}_${strategy.name.lowercase()}_${profile.name.lowercase()}.png"
          ImageIO.write(bi, "png", File(out, name))
          println("  -> $name ev=${"%+.2f".format(ev)} ${pipelineSummary(p)} ${ms}ms")
        }
      }
    }
  }

  private fun pipelineSummary(p: ColorPipeline) =
    "${p.used} wb=${p.wbGains.joinToString { "%.2f".format(it) }} notes=${p.notes}"
}
