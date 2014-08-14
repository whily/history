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
import android.view.MotionEvent
import android.util.FloatMath
import android.view.View

class MapView(context: Context, attrs: AttributeSet) extends View(context, attrs) {
  // It seems that extension of View should be done by extending View(context, attrs)
  // instead of extending View(context) directly.

  private var map: TileMap = null

  private var prevX = 0.0f
  private var prevY = 0.0f
  private var oldDist = 0.0f
  object TouchState extends Enumeration { 
    val NONE, DRAG, ZOOM = Value 
  }
  private var touchState = TouchState.NONE

  private var centerLat: Double = 30.0
  private var centerLon: Double = 110.0

  private var zoomLevel = 0

  private val paint = new Paint()
  paint.setAntiAlias(true)
  paint.setStyle(Paint.Style.FILL)
  paint.setTextSize(36f)

  override def onTouchEvent(event: MotionEvent): Boolean = {
    event.getAction() & MotionEvent.ACTION_MASK match {
      case MotionEvent.ACTION_DOWN =>
        prevX = event.getX()
        prevY = event.getY()
        touchState = TouchState.DRAG
       
      case MotionEvent.ACTION_UP | MotionEvent.ACTION_POINTER_UP =>
        touchState = TouchState.NONE
      
      case MotionEvent.ACTION_POINTER_DOWN =>
        oldDist = spacing(event);
        if (oldDist > 10f) {
          touchState = TouchState.ZOOM
        }  

      case MotionEvent.ACTION_MOVE =>
        if (touchState == TouchState.DRAG) {
          val scalingFactor = math.pow(2.0, zoomLevel)
          centerLon -= scalingFactor * map.lonDiff((event.getX() - prevX))
          centerLat -= scalingFactor * map.latDiff((event.getY() - prevY))
        } else if (touchState == TouchState.ZOOM) {
          val newDist = spacing(event)
          if (newDist > 10f) {
            if (newDist > oldDist) {
              // Zoom in.
              if (zoomLevel == 0) {
                zoomLevel = -1
              }
            } else {
              // Zoom out.
              if (zoomLevel == -1) {
                zoomLevel = 0
              }
            }
          }          
        }
        invalidate()
    }

    true
  }  

  override protected def onDraw(canvas: Canvas) {
    canvas.drawColor(Color.BLACK)

    if (map == null) {
      map = new TileMap(context, 0)
    }
    map.draw(canvas, paint, centerLon, centerLat, zoomLevel)
  }

  // Calculate how far two fingers are.
  // From http://www.zdnet.com/blog/burnette/how-to-use-multi-touch-in-android-2-part-6-implementing-the-pinch-zoom-gesture/1847
  private def spacing(event: MotionEvent): Float = {
    val x = event.getX(0) - event.getX(1)
    val y = event.getY(0) - event.getY(1)
    FloatMath.sqrt(x * x + y * y)
  }  
}
