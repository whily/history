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

import android.app.{ActionBar, Activity}
import android.os.Bundle
import android.view.View
import net.whily.scaland.Util

class MapActivity extends Activity {
  private var bar: ActionBar = null
  
  override def onCreate(icicle: Bundle) { 
    super.onCreate(icicle)

    setContentView(R.layout.map)  
    setTitle("")
    
    bar = getActionBar
    bar.setHomeButtonEnabled(true)

    Util.requestImmersiveMode(this)
  }
}
