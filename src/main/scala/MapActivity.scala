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

package net.whily.android.history

import android.app.{ActionBar, Activity}
import android.os.Bundle
import android.view.View
import android.util.Log
import net.whily.scaland.{ExceptionHandler, Util}

class MapActivity extends Activity {
  private var bar: ActionBar = null
  private val tag = "MapActivity"
  
  override def onCreate(icicle: Bundle) { 
    super.onCreate(icicle)

    // Set handler for uncaught exception raised from current activity.
    Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this))

    setContentView(R.layout.map)  
    setTitle("")
    
    bar = getActionBar
    bar.setHomeButtonEnabled(true)

    Util.requestImmersiveMode(this)
  }
}
