/**
 * Places.
 *
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License: 
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2014 Yujian Zhang
 */

package net.whily.android.history

object PlaceType extends Enumeration {
  type PlaceType = Value
  val Capital, Province, Prefecture, County, Town = Value
}

case class Place(name: String, lat: Double, lon: Double, 
  ptype: PlaceType.PlaceType = PlaceType.Prefecture)
