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

import android.content.Context
import android.graphics.{Bitmap, BitmapFactory, Canvas, Color, Paint}
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class Map(context: Context, attrs: AttributeSet) extends View(context, attrs) {
  // It seems that extension of View should be done by extending View(context, attrs)
  // instead of extending View(context) directly.

  private val map = BitmapFactory.decodeResource(getResources(), R.drawable.china)
  private val paint = new Paint()
  paint.setAntiAlias(true)
  paint.setStyle(Paint.Style.FILL)

  override protected def onDraw(canvas: Canvas) {
    canvas.drawColor(Color.GRAY)
    canvas.drawBitmap(map, 0, 0, paint)
  }
}
