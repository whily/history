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
import android.location.LocationManager
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

  private var screenZoomLevel = 0
  private val minZoomLevel = -2
  private val maxZoomLevel = 0

  private val dpi = Math.min(context.getResources().getDisplayMetrics().xdpi,
                             context.getResources().getDisplayMetrics().ydpi)

  private val paint = new Paint()
  paint.setAntiAlias(true)
  paint.setStyle(Paint.Style.FILL)
  paint.setTextSize(36f)


  val locMgr = context.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
  val loc = locMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
  // User location.
  var userLon = 0.0
  var userLat = 0.0
  if (loc != null) {
    userLon = loc.getLongitude()
    userLat = loc.getLatitude()
  }

  override def onTouchEvent(event: MotionEvent): Boolean = {
    event.getAction() & MotionEvent.ACTION_MASK match {
      case MotionEvent.ACTION_DOWN =>
        prevX = event.getX()
        prevY = event.getY()

      case MotionEvent.ACTION_MOVE =>
        // Only move if ScaleDetector is not processing a gesture.
        if (!scaleDetector.isInProgress()) {
          // For the map scaling factor.
          val scalingFactor = math.pow(2.0, screenZoomLevel)
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
    map.draw(canvas, paint, centerLon, centerLat, screenZoomLevel, 
             userLon, userLat)
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
        screenZoomLevel = Math.max(minZoomLevel, screenZoomLevel - 1)
      } else {
        // Zoom out
        screenZoomLevel = Math.min(maxZoomLevel, screenZoomLevel + 1)
      }
      invalidate()
    }
  }
}
