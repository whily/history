/**
 * River.
 *
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License:
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2017 Yujian Zhang
 */

package net.whily.android.history

import net.whily.scasci.geo.Point

/** Width represents how wide the river is, in range [1, 10] with 10 the widest.
  * Only approximation to draw river. */
class River(val width: Int, coordinates: Array[Double]) {
  // TODO: if performance is an issue, one approach is to partition pointes into
  // a recurives binary trees. The leaf point could be one segment contain x points, with
  // x configurable. Each segment stores the information of the bounding rectangle, while
  // the parent node contains the bounding rectangle of all the children. When drawing the river,
  // a recursive method can be called from the root node.
  assert((1 <= width) && (width <= 10) && (coordinates.length % 2 == 0))
  private val nPoints = coordinates.length / 2
  val points = new Array[Point](nPoints)
  for (i <- 0 until nPoints) {
    points(i) = Point(coordinates(2 * i), coordinates(2 * i + 1))
  }
}
