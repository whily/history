/**
 * Activity to show map.
 *
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License: 
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2014 Yujian Zhang
 */

package net.whily.android.sanguo

import scala.collection.mutable
import android.app.{ActionBar, Activity}
import android.content.{Intent, Context}
import android.graphics.{Canvas, Color, Paint}
import android.os.Bundle
import android.view.{Menu, MenuItem, MotionEvent, View}
import android.widget.{ArrayAdapter, LinearLayout}
import net.whily.scaland.{Render2DActivity, Render2DView, Util}

class MapViewActivity extends Render2DActivity {
  private var bar: ActionBar = null
  
  override def onCreate(icicle: Bundle) { 
    super.onCreate(icicle)

    renderView = new ShowView(this)
    setContentView(renderView)  
    setTitle("")
    
    bar = getActionBar
    bar.setHomeButtonEnabled(true)

    Util.requestImmersiveMode(this)
  }
}

class ShowView(context: Context) extends Render2DView(context) with Runnable {
  val paint = new Paint()
  paint.setAntiAlias(true)
  paint.setStyle(Paint.Style.FILL)

  def drawOn(canvas: Canvas) {
  }
}
