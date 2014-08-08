/**
 * Activity to show Sanguozhi.
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
import android.os.Bundle
import android.webkit.WebView

class BookActivity extends Activity {
  private[this] var browser: WebView = null

  override def onCreate(icicle: Bundle) {
    super.onCreate(icicle)
    setContentView(R.layout.book)

    browser = findViewById(R.id.webkit).asInstanceOf[WebView]
    browser.loadDataWithBaseURL(null, getString(R.string.sanguozhi), "text/html", "UTF-8", null)
  }
}
