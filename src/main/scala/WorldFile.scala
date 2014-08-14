/**
 * World File.
 *
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License: 
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2014 Yujian Zhang
 */

package net.whily.android.history

/** World File according to http://en.wikipedia.org/wiki/World_file
  * We assume D and B are zero.
  * @param A Line 1 of world file
  * @param E Line 4 of world file
  * @param C Line 5 of world file
  * @param F Line 6 of world file
  */
class WorldFile(A: Double, E: Double, C: Double, F: Double) {
  /** Return the longitude for pixel with x-coordinate as x. */
  def longitude(x: Double) = A * x + C

  /** Return the latitude for pixel with y-cooridnate as y. */
  def latitude(y: Double) = E * y + F

  /** Return longitude difference given pixel difference in x-coordinate. */
  def lonDiff(pixelDiff: Double) = A * pixelDiff

  /** Return latitude difference given pixel differnce in y-coordinate. */
  def latDiff(pixelDiff: Double) = E * pixelDiff

  /** Return x-coordiante difference given longitude difference. */
  def xDiff(lonDiff: Double) = lonDiff / A

  /** Return y-coordinate difference given latitude differnce. */
  def yDiff(latDiff: Double) = latDiff / E

  /** Return the screen X coordinate for a pixel X coordinate in the map file.
    * 
    * @param refLon     longitude of the reference point
    * @param refScreenX screen X coordinat of the reference point
    * @param pixelX     pixel X coordinate in the map file
    * @param zoomLevel  zoom level
    */
  def screenX(refLon: Double, refScreenX: Int, pixelX: Int, zoomLevel: Int) = {
    val scalingFactor = zoomLevel match {
      case -1 => 2
      case _  => 1
    }
    (refScreenX + scalingFactor * (pixelX + (C - refLon) / A)).asInstanceOf[Float]
  }


  /** Return the screen Y coordinate for a pixel Y coordinate in the map file.
    * 
    * @param refLat     latitude of the reference point
    * @param refScreenY screen Y coordinat of the reference point
    * @param pixelY     pixel Y coordinate in the map file
    * @param pixelX     pixel X coordinate in the map file
    */
  def screenY(refLat: Double, refScreenY: Int, pixelY: Int, zoomLevel: Int) = {
    val scalingFactor = zoomLevel match {
      case -1 => 2
      case _  => 1
    }
    (refScreenY + scalingFactor * (pixelY + (F - refLat) / E)).asInstanceOf[Float]
  }
}
