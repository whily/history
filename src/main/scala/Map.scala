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

package net.whily.android.sanguo

import android.content.Context
import android.graphics.{Bitmap, BitmapFactory, Canvas, Color, Paint}
import android.view.MotionEvent
import android.view.View

class Map(context: Context) extends View(context) {
  private val map = BitmapFactory.decodeResource(getResources(), R.drawable.sanguo)
  private val paint = new Paint()
  paint.setAntiAlias(true)
  paint.setStyle(Paint.Style.FILL)

  override protected def onDraw(canvas: Canvas) {
    canvas.drawBitmap(map, 0, 0, paint)
  }
}
