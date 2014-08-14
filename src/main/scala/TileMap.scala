/**
 * Draw maps with tiles.
 *
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License: 
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2014 Yujian Zhang
 */

package net.whily.android.history

import android.content.Context
import android.graphics.{Bitmap, BitmapFactory, Canvas, Color, Paint, RectF}
import net.whily.scaland.Util
import android.util.Log

/** Mange a tiles of maps and draw to Canvas.
  * 
  * Map tiles are saved in res/drawable as 256x256 PNG files.  Zoom
  * level 0 contains the map files with original resolution, while
  * zoom level 1, 2, ..., are scaled-down (i.e. zoom out) version of
  * the map files. For example, 2-by-2 tiles in zoom level 0 becomes
  * one tile in zoom level 1, and so on. The maximum zoom level is the
  * level that such zooming out operation is possible for 256x256
  * tiles. For example, if map size is 2048x1024, then maximum zoom
  * level is 2.
  * 
  * It is also possible to have negative zoom levels (i.e. zoom
  * in). The intention is to suppose very high density screens
  * (e.g. xxhdpi) while original resolution is just too dense for
  * normal viewing. For negative zoom levels, no maps tiles are saved,
  * and zoom level 0 tiles are directly scaled on the fly.
  */
class TileMap(context: Context, zoomLevel: Int) {
  // Modify the following 5 parameters based on the attributes of the map.

  // Natural Earth II 1:10m world file NE2_HR_LC_SR_W_DR.tfw
  private val worldFile = new WorldFile(0.01666666666667, -0.01666666666667, 
                                        -179.99166666666667, 89.99166666666667)
  // The following parameters are related to how we crop the map.
  private val mapX    = 2048    // Number of pixels in X dimension (west-east)
  private val mapY    = 2048    // Number of pixels in Y dimension (north-south)
  private val mapLeft = 16650   // The left (west) of the map 
  private val mapTop  = 2450    // The top (north) of the map

  private val tileSize = 256

  // TODO: adjust for zoom level.
  private val nTileX = mapX / tileSize
  private val nTileY = mapY / tileSize

  private val maps = new Array[Bitmap](nTileX * nTileY)

  Log.d("TileMap", "Initialize")

  def draw(canvas: Canvas, paint: Paint, centerLon: Double, centerLat: Double, zoomLevel: Int) {
    // Ignore source/destination density by using RectF version of drawBitmap.

    val canvasWidth = canvas.getWidth()
    val canvasHeight = canvas.getHeight()

    // Current filtering of map method is rather naive, may consider
    // direct calculation later.
    val canvasRect = new RectF(0, 0, canvasWidth, canvasHeight)

    for (i <- 0 until nTileX) {
      for (j <- 0 until nTileY) {
        // For each tile, find its corresponding Rect for the canvas. If
        // the Rect intersects with the canvas Rect, draw the tile.
        val index = i + j * nTileX
        val left = Math.floor(worldFile.screenX(centerLon, canvasWidth / 2, mapLeft + i * tileSize, zoomLevel)).asInstanceOf[Int]
        val top = Math.floor(worldFile.screenY(centerLat, canvasHeight / 2, mapTop + j * tileSize, zoomLevel)).asInstanceOf[Int]
        val displayTileSize = zoomLevel match {
          case -1 => 2 * tileSize
          case _  => tileSize
        }
        val tileRect = new RectF(left, top, left + displayTileSize, top + displayTileSize)
        if (RectF.intersects(tileRect, canvasRect)) {
          if (maps(index) == null) {
            maps(index) = BitmapFactory.decodeResource(context.getResources(),
              Util.getDrawableId(context, "map_" + zoomLevel + "_" + i + "_" + j))
          }
          canvas.drawBitmap(maps(index), null, tileRect, paint)
        }
      }
    }

    for (place <- places) {
      val scalingFactor = math.pow(2.0, -zoomLevel)
      val x = canvasWidth / 2 + (scalingFactor * worldFile.xDiff(place.lon - centerLon)).asInstanceOf[Float]
      val y = canvasHeight / 2 + (scalingFactor * worldFile.yDiff(place.lat - centerLat)).asInstanceOf[Float]
      canvas.drawCircle(x, y, 8f, paint)
      canvas.drawText(place.name, x + 10, y, paint)
    }
  }

  /** Return longitude difference given pixel difference in x-coordinate. */
  def lonDiff(pixelDiff: Double) = worldFile.lonDiff(pixelDiff)

  /** Return latitude difference given pixel differnce in y-coordinate. */
  def latDiff(pixelDiff: Double) = worldFile.latDiff(pixelDiff)

  case class Place(name: String, lat: Double, lon: Double)

  private val places = 
    Array(Place("洛阳", 34.631514, 112.454681),   // To be confirmed
          Place("长安", 34.266667, 108.9),        // To be confirmed
          Place("邺", 36.333333, 114.616667),      // Should be 20 km sw of the county.
          Place("许都", 34.035803, 113.852478),    // To be confirmed
          Place("寿春", 32.0, 116.5),             // Estimated
          Place("官渡", 34.720967, 114.014228),    // To be confirmed
          Place("襄阳", 32.066667, 112.083333),    // To be confirmed

          Place("成都", 30.658611, 104.064722),   // To be confirmed
          Place("汉中", 33.066667, 107.016667),    // To be confirmed
          Place("涪城", 31.466667, 104.683333),    // To be confirmed
          Place("葭萌", 32.433333, 105.816667),    // To be confirmed
          Place("雒城", 30.99, 104.25),    // To be confirmed
          Place("绵竹", 31.333333, 104.2),    // To be confirmed
          Place("阴平", 32.916667, 104.766667  ),    // To be confirmed
          Place("剑阁", 31.285278, 105.523611),    // To be confirmed

          Place("建业", 32.05, 118.766667),    // To be confirmed
          Place("江陵", 30.133333, 112.5) ,       // To be confirmed
          Place("濡须", 31.678333,117.735278),     // To be confirmed
          Place("赤壁", 29.716667, 113.9),    // To be confirmed
          Place("夷陵", 30.766667, 111.316667)    // To be confirmed
    )
}
