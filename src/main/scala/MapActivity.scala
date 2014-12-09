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
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
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

    val button = findViewById(R.id.button1).asInstanceOf[Button]
    val activity = this
    button.setOnClickListener(new OnClickListener() {
      override def onClick(view: View) {
        startActivity(new Intent(activity, classOf[BookActivity]))
      }
    })

    Util.requestImmersiveMode(this)
  }
}

/*
HomeFragment.java
package info.androidhive.slidingmenu;
 
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
 
public class HomeFragment extends Fragment {
     
    public HomeFragment(){}
     
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
  
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);
          
        return rootView;
    }
}
 */
