/**
 * Labels to draw on map.
 *
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License: 
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2014 Yujian Zhang
 */

package net.whily.android.history

import android.graphics.{Canvas, Paint}
import net.whily.scasci.geo.Point

/** Labels to draw on map. 
  * @param text label text
  * @param size label size in dp
  */
abstract class Label(text: String, size: Int) {
  def draw(canvas: Canvas, paint: Paint)
}

class Polyline(points: Array[Point])
class Polygon(points: Array[Point])

class PointLabel(text: String, size: Int, point: Point) extends
    Label(text, size) {
  def draw(canvas: Canvas, paint: Paint) {
  }
}

class LineLabel(text: String, size: Int, line: Polyline) extends
    Label(text, size) {
  def draw(canvas: Canvas, paint: Paint) {
  }
}

class AreaLabel(text: String, size: Int, area: Polygon) extends
    Label(text, size) {
  def draw(canvas: Canvas, paint: Paint) {
  }
}
