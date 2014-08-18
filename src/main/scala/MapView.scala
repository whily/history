/**
 * Map View.
 *
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License: 
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2014 Yujian Zhang
 */

package net.whily.android.history

import android.app.Activity
import android.content.Context
import android.graphics.{Canvas, Color, Paint}
import android.util.AttributeSet
import android.view.{MotionEvent, ScaleGestureDetector, View}
import android.util.FloatMath
import net.whily.scaland.Util

class MapView(context: Context, attrs: AttributeSet) extends View(context, attrs) {
  // It seems that extension of View should be done by extending View(context, attrs)
  // instead of extending View(context) directly.

  private var map: TileMap = null

  private var prevX = 0.0f
  private var prevY = 0.0f

  // Detect pinch gesture
  private val scaleDetector = new ScaleGestureDetector(context, new ScaleListener())

  private var centerLat: Double = 30.0
  private var centerLon: Double = 110.0

  private var zoomLevel = 0
  private val minZoomLevel = -2
  private val maxZoomLevel = 0

  private val dpi = Math.min(context.getResources().getDisplayMetrics().xdpi,
                             context.getResources().getDisplayMetrics().ydpi)

  private val paint = new Paint()
  paint.setAntiAlias(true)
  paint.setStyle(Paint.Style.FILL)
  paint.setTextSize(36f)

  override def onTouchEvent(event: MotionEvent): Boolean = {
    event.getAction() & MotionEvent.ACTION_MASK match {
      case MotionEvent.ACTION_DOWN =>
        prevX = event.getX()
        prevY = event.getY()

      case MotionEvent.ACTION_MOVE =>
        // Only move if ScaleDetector is not processing a gesture.
        if (!scaleDetector.isInProgress()) {
          // For the map scaling factor.
          val scalingFactor = math.pow(2.0, zoomLevel)
          centerLon -= scalingFactor * map.lonDiff((event.getX() - prevX))
          centerLat -= scalingFactor * map.latDiff((event.getY() - prevY))
          prevX = event.getX()
          prevY = event.getY()
          invalidate()
        }

      case _ => {}
    }

    scaleDetector.onTouchEvent(event)
    true
  }  

  override protected def onDraw(canvas: Canvas) {
    canvas.drawColor(Color.BLACK)

    if (map == null) {
      map = new TileMap(context, 0)
    }
    map.draw(canvas, paint, centerLon, centerLat, zoomLevel)
  }

  private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    private var scaleFactor = 1f

    override def onScaleBegin(detector: ScaleGestureDetector): Boolean = {
      scaleFactor = 1f
      true
    }
    override def onScale(detector: ScaleGestureDetector): Boolean = {
      // Note that this can be called multiple times during one pinch operation. So accummulate
      // the actual scale factor.
      scaleFactor *= detector.getScaleFactor()
      true
    }

    override def onScaleEnd(detector: ScaleGestureDetector) {
      scaleFactor *= detector.getScaleFactor()
      if (scaleFactor > 1.0f) {
        // Zoom in
        zoomLevel = Math.max(minZoomLevel, zoomLevel - 1)
      } else {
        // Zoom out
        zoomLevel = Math.min(maxZoomLevel, zoomLevel + 1)
      }
      invalidate()
    }
  }
}
