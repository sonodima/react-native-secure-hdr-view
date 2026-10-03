package com.sonodima.securehdrview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.hardware.DataSpace
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageWriter
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import com.facebook.react.views.view.ReactViewGroup
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

class SecureHDRView(context: Context) : ReactViewGroup(context), SurfaceHolder.Callback {
  var secure = false
  var hdr = 0f

  // Surface under the window, seen through a hole the view punches over its children.
  private var surface: SurfaceView? = null
  val hasSurface get() = surface != null

  private val pendingDraw = Runnable { drawSurface() }
  private var snapshotting = false

  // Surfaces don't follow animations of the views above them, like screen transitions.
  private val transitionWatcher = ViewTreeObserver.OnPreDrawListener {
    surface?.alpha = if (inTransition()) 0f else 1f
    true
  }

  // Main thread: one frame in flight, while the next waits for it.
  private var busy = false
  private var dirty = false

  // Output thread.
  private var writer: ImageWriter? = null
  private var writerFailed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
  private var pixels = IntArray(0)

  fun applyProps() {
    updateSurface()
    redraw()
  }

  // The secure flag is only read when a surface is created, so a change means a new one.
  private fun updateSurface() {
    val needed = secure || hdr > 0
    surface?.let {
      if (needed && it.tag == secure) {
        return
      }
      removeView(it)
    }

    surface = if (!needed) null else SurfaceView(context).apply {
      tag = secure
      setSecure(secure)
      holder.setFormat(PixelFormat.TRANSLUCENT)
      holder.addCallback(this@SecureHDRView)
    }.also {
      addView(it)
      it.layout(0, 0, width, height)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    viewTreeObserver.addOnPreDrawListener(transitionWatcher)
  }

  override fun onDetachedFromWindow() {
    viewTreeObserver.removeOnPreDrawListener(transitionWatcher)
    super.onDetachedFromWindow()
  }

  private fun inTransition(): Boolean {
    var view = parent as? View
    while (view != null) {
      val animation = view.animation
      if (animation != null && animation.hasStarted() && !animation.hasEnded()) {
        return true
      }
      view = view.parent as? View
    }
    return false
  }

  // The copy is of the content, without the surface's hole.
  override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
    if (snapshotting && child === surface) {
      return false
    }
    return super.drawChild(canvas, child, drawingTime)
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    surface?.layout(0, 0, width, height)
  }

  override fun onDescendantInvalidated(child: View, target: View) {
    super.onDescendantInvalidated(child, target)
    if (target !== surface) {
      redraw()
    }
  }

  override fun surfaceCreated(holder: SurfaceHolder) {}

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    OUTPUT.execute { closeWriter() }
    redraw()
  }

  // The surface must not be touched once this returns, but the main thread mustn't hang on a
  // frame stuck waiting for a buffer either.
  override fun surfaceDestroyed(holder: SurfaceHolder) {
    try {
      OUTPUT.submit { closeWriter() }.get(500, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
      // The frame fails on the dead surface and is caught.
    }
  }

  // Changes come in bursts: draw once, on the next frame.
  private fun redraw() {
    removeCallbacks(pendingDraw)
    postOnAnimation(pendingDraw)
  }

  private fun drawSurface() {
    val holder = surface?.holder ?: return
    if (!holder.surface.isValid || width == 0 || height == 0) {
      return
    }
    if (busy) {
      dirty = true
      return
    }
    busy = true

    val frame = snapshot()
    val hdr = hdr
    OUTPUT.execute {
      try {
        present(holder, frame, hdr)
      } catch (e: RuntimeException) {
        // The surface went away mid-frame: the next one starts over.
      } finally {
        frame.recycle()
        post {
          busy = false
          if (dirty) {
            dirty = false
            redraw()
          }
        }
      }
    }
  }

  // The view as it draws itself. From Android 9 it's recorded and rendered by the GPU.
  private fun snapshot(): Bitmap {
    snapshotting = true
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val picture = Picture()
        draw(picture.beginRecording(width, height))
        picture.endRecording()
        return Bitmap.createBitmap(picture, width, height, Bitmap.Config.ARGB_8888)
      }
      return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        try {
          draw(Canvas(it))
        } catch (e: IllegalArgumentException) {
          // A hardware bitmap in software: leave it out.
        }
      }
    } finally {
      snapshotting = false
    }
  }

  // Output thread.
  private fun present(holder: SurfaceHolder, frame: Bitmap, hdr: Float) {
    if (!holder.surface.isValid) {
      return
    }

    if (!writerFailed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      try {
        val writer = writer ?: imageWriter(holder).also { writer = it }
        val image = writer.dequeueInputImage()
        // Resize is on its way, with a frame of its own...
        if (image.width != frame.width || image.height != frame.height) {
          return image.close()
        }

        image.dataSpace =
          if (hdr > 0) DataSpace.DATASPACE_BT2020_HLG else DataSpace.DATASPACE_SRGB
        write(frame, image, hdr)
        writer.queueInputImage(image)
        return
      } catch (e: RuntimeException) {
        writerFailed = true
        closeWriter()
      }
    }

    // No HDR support, so we make a plain copy.
    val canvas = holder.lockCanvas() ?: return
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    canvas.drawBitmap(frame, 0f, 0f, null)
    holder.unlockCanvasAndPost(canvas)
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun imageWriter(holder: SurfaceHolder) =
    ImageWriter.Builder(holder.surface)
      .setMaxImages(2)
      // 8-bit RGBA is the only RGBA format ImageWriter lets the CPU write.
      .setHardwareBufferFormat(HardwareBuffer.RGBA_8888)
      .setUsage(
        HardwareBuffer.USAGE_CPU_WRITE_OFTEN or
          HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
          HardwareBuffer.USAGE_COMPOSER_OVERLAY,
      )
      .build()

  private fun closeWriter() {
    writer?.close()
    writer = null
  }

  // Premultiplied RGBA, as is or as HLG BT.2020 brightened by the HDR intensity.
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun write(frame: Bitmap, image: Image, hdr: Float) {
    val w = image.width
    if (pixels.size != w * image.height) {
      pixels = IntArray(w * image.height)
    }
    frame.getPixels(pixels, 0, w, 0, 0, w, image.height)

    val plane = image.planes[0]
    val out = plane.buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    val stride = plane.rowStride / 4
    val gain = hlgGain(hdr)
    var last = pixels.firstOrNull()?.inv() ?: 0
    var rgba = 0
    for (y in 0 until image.height) {
      val offset = y * w
      for (i in offset until offset + w) {
        val p = pixels[i]
        // Views are mostly flat colours: convert only when the colour changes.
        if (p != last) {
          last = p
          rgba = if (hdr > 0) hlg(p, gain) else premultiplied(p)
        }
        pixels[i] = rgba
      }
      out.position(y * stride)
      out.put(pixels, offset, w)
    }
  }
}

private val OUTPUT = Executors.newSingleThreadExecutor()

private fun premultiplied(argb: Int): Int {
  val a = argb ushr 24
  return (argb shr 16 and 0xFF) * a / 255 or
    ((argb shr 8 and 0xFF) * a / 255 shl 8) or
    ((argb and 0xFF) * a / 255 shl 16) or
    (a shl 24)
}

// SDR white lands between HLG reference white, where it looks as usual, and the HLG peak.
private fun hlgGain(intensity: Float) = 0.265f.pow(1 - intensity)

private fun hlg(argb: Int, gain: Float): Int {
  val a = argb ushr 24
  val r = LINEAR[argb shr 16 and 0xFF] * gain
  val g = LINEAR[argb shr 8 and 0xFF] * gain
  val b = LINEAR[argb and 0xFF] * gain
  return hlg(0.6274f * r + 0.3293f * g + 0.0433f * b, a) or
    (hlg(0.0691f * r + 0.9195f * g + 0.0114f * b, a) shl 8) or
    (hlg(0.0164f * r + 0.0880f * g + 0.8956f * b, a) shl 16) or
    (a shl 24)
}

private val LINEAR = FloatArray(256) { i ->
  val v = i / 255f
  if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
}

// Linear light to HLG signal, premultiplied by 8-bit alpha.
private fun hlg(linear: Float, alpha: Int): Int {
  val signal = HLG[(linear * HLG_STEPS).toInt().coerceIn(0, HLG_STEPS)]
  return (signal * alpha + 127) / 255
}

private const val HLG_STEPS = 16384

private val HLG = IntArray(HLG_STEPS + 1) { i ->
  val e = i.toFloat() / HLG_STEPS
  val signal =
    if (e <= 1f / 12) sqrt(3 * e) else 0.17883277f * ln(12 * e - 0.28466892f) + 0.55991073f
  (signal * 255 + 0.5f).toInt()
}
