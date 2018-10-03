/**
 * Draw maps with tiles.
 *
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License:
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2014-2016 Yujian Zhang
 */

package net.whily.android.history

import android.content.Context
import android.graphics.{Bitmap, BitmapFactory, Canvas, Color, Paint, Rect, RectF}
import net.whily.scasci.geo.Point
import net.whily.scaland.Util._
import net.whily.scaland.BitmapCache
import net.whily.hgc.{SpatialTemporalDatabase, Place, River, PlaceType}
import android.util.Log

/** Manage a tiles of maps and draw to Canvas.
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
  // Blue Marble  Land Surface, Shallow Water, and Shaded Topography Eastern Hemisphere map
  // World file is from http://grasswiki.osgeo.org/wiki/Blue_Marble
  private val worldFile = new WorldFile(0.008333333333333, -0.008333333333333,
                                        0.00416666666666665, 89.99583333333334)
  // The following parameters are related to how we crop the map.
  // TODO: adjust for zoom level.
  private val nTileX  = 35
  private val nTileY  = 16
  private val mapLeft = 6836    // The left (west) of the map
  private val mapTop  = 5000    // The top (north) of the map

  private val tileSize = 256

  private val mapX    = nTileX * tileSize    // Number of pixels in X dimension (west-east)
  private val mapY    = nTileY * tileSize    // Number of pixels in Y dimension (north-south)

  private val bitmapCache = new BitmapCache(context)

  private val capitalTextSizeSp    = 18
  private val provinceTextSizeSp   = 16
  private val prefectureTextSizeSp = 14
  private val countyTextSizeSp     = 12
  private val townTextSizeSp       = 10
  private val capitalTextSizePx    = sp2px(capitalTextSizeSp, context)
  private val provinceTextSizePx   = sp2px(provinceTextSizeSp, context)
  private val prefectureTextSizePx = sp2px(prefectureTextSizeSp, context)
  private val countyTextSizePx     = sp2px(countyTextSizeSp, context)
  private val townTextSizePx       = sp2px(townTextSizeSp, context)

  // Measure text width/height.
  private val textBounds = new Rect()

  // Boudning rectangles of the drawn features and labels.
  private var boundingRects: List[RectF] = Nil

  Log.d("TileMap", "Initialize")

  // Spatial temporal database
  private val database = SpatialTemporalDatabase.chinaDatabase
  private var snapshotIndex = 0

  def prevSnapshot() {
    if (snapshotIndex > 0) {
      snapshotIndex -= 1
    }
  }

  def nextSnapshot() {
    if (snapshotIndex < database.length - 1) {
      snapshotIndex += 1
    }
  }

  def getSnapshotDate(): String =
    database(snapshotIndex).date.toString

  def draw(canvas: Canvas, paint: Paint, centerLon: Double, centerLat: Double,
           screenZoomLevel: Int, userLon: Double, userLat: Double) {
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
        val left = Math.floor(worldFile.screenX(centerLon, canvasWidth / 2, mapLeft + i * tileSize, screenZoomLevel)).asInstanceOf[Int]
        val top = Math.floor(worldFile.screenY(centerLat, canvasHeight / 2, mapTop + j * tileSize, screenZoomLevel)).asInstanceOf[Int]
        val displayTileSize = screenZoomLevel match {
          case -2 => 4 * tileSize
          case -1 => 2 * tileSize
          case _  => tileSize
        }
        val tileRect = new RectF(left, top, left + displayTileSize, top + displayTileSize)
        if (RectF.intersects(tileRect, canvasRect)) {
          val tileZoomLevel = if (screenZoomLevel < 0) 0 else screenZoomLevel
          val resId = getDrawableId(context, "map_" + tileZoomLevel + "_" + i + "_" + j)
          val bitmap  = bitmapCache.loadBitmap(resId)
          if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, tileRect, null)
          }
        }
      }
    }

    val snapshot = database(snapshotIndex).snapshot
    val places = snapshot.places
    val rivers = snapshot.rivers

    // Draw rivers.
    // TODO: optimize the speed. At least only draw rivers whose bounding rectangle
    // intersects with current screen.
    for (river <- rivers) {
      drawRiver(river, screenZoomLevel, canvasWidth, canvasHeight, centerLon, centerLat, "", canvas, paint)
    }

    // We use the straighforward (and not optimal) method to draw labels
    // with one loop.
    // Basically, each feature/lable is drawn if
    //   * the bounding rectangle of the feature is fully contained in the canvas
    //     and does not intersect existing boudning rectangles, and
    //   * the bounding rectangle of the label (with maximum 8 positions) is fully
    //     contained in the canvas and does not intersect existing bouding rectangles
    // After one feature/lable is drawn, the bounding rectangles of the feature and label
    // will be added in the existing list.
    // We don't use any joint optimization approach.
    boundingRects = Nil
    for ((name, place) <- places) {
      drawPointLabel(screenZoomLevel, canvasWidth, canvasHeight, centerLon, centerLat,
        place.lon, place.lat, place.ptype, name, canvas, paint)
    }

    //val savedColor = paint.getColor()
    //paint.setColor(Color.GREEN)
    //drawPointLabel(screenZoomLevel, canvasWidth, canvasHeight, centerLon, centerLat,
    //  userLon, userLat, "", canvas, paint)
    //paint.setColor(savedColor)
  }

  // Convert from GPS coordinates to screen coordinates.
  private def toScreenCoordinates(screenZoomLevel: Int, canvasWidth: Int, canvasHeight: Int,
                             centerLon: Double, centerLat: Double, placeLon: Double, placeLat: Double) = {
    val scalingFactor = math.pow(2.0, -screenZoomLevel)
    val x = canvasWidth / 2 + (scalingFactor * worldFile.xDiff(placeLon - centerLon)).asInstanceOf[Float]
    val y = canvasHeight / 2 + (scalingFactor * worldFile.yDiff(placeLat - centerLat)).asInstanceOf[Float]
    (x, y)
  }

  private def drawPointLabel(screenZoomLevel: Int, canvasWidth: Int, canvasHeight: Int,
    centerLon: Double, centerLat: Double, placeLon: Double, placeLat: Double, placeType: PlaceType.PlaceType,
    text: String, canvas: Canvas, paint: Paint) {
    val savedColor = paint.getColor()
    val (x, y) = toScreenCoordinates(screenZoomLevel, canvasWidth, canvasHeight, centerLon, centerLat, placeLon, placeLat)
    val screenRect = new RectF(0f, 0f, canvasWidth, canvasHeight)

    paint.setColor(Color.rgb(0x0f, 0x98, 0xd4))
    val baseUnit = 0.5f * dp2px(1, context)
    val labelRadius = baseUnit * (placeType match {
      case PlaceType.Capital => 18f
      case PlaceType.Province => 16f
      case PlaceType.Prefecture => 14f
      case PlaceType.County => 12f
      case PlaceType.Town => 10f
    })

    val pointBoundingRect = new RectF(x - labelRadius, y - labelRadius,
      x + labelRadius, y + labelRadius)

    // Check whether the bounding rectangle of the feature is fully contained in the canvas.
    if (!screenRect.contains(pointBoundingRect)) {
      return
    }

    val textSize = placeType match {
      case PlaceType.Capital => capitalTextSizePx
      case PlaceType.Province => provinceTextSizePx
      case PlaceType.Prefecture => prefectureTextSizePx
      case PlaceType.County => countyTextSizePx
      case PlaceType.Town => townTextSizePx
    }
    paint.setTextSize(textSize)
    paint.getTextBounds(text, 0, text.length(), textBounds)

    val labelBoundingRect = new RectF(x + labelRadius, y - textBounds.exactCenterY(),
      x + labelRadius + textBounds.width(), y - textBounds.exactCenterY() + textBounds.height())

    if (!screenRect.contains(labelBoundingRect)) {
      return
    }

    for (rect <- boundingRects) {
      if ((RectF.intersects(rect, pointBoundingRect)) || (RectF.intersects(rect, labelBoundingRect))) {
        return
      }
    }

    boundingRects = pointBoundingRect :: labelBoundingRect :: boundingRects

    placeType match {
      case PlaceType.Capital =>
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(2.0f * baseUnit)
        canvas.drawCircle(x, y, 16f * baseUnit, paint)
        canvas.drawCircle(x, y, 12f * baseUnit, paint)
        paint.setStyle(Paint.Style.FILL)
        canvas.drawCircle(x, y, 8f * baseUnit, paint)
        // FILL style is needed to ensure correct drawing of the text.
        // Applicable for similar calls below.

      case PlaceType.Province =>
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(2.0f * baseUnit)
        canvas.drawCircle(x, y, 14f * baseUnit, paint)
        canvas.drawCircle(x, y, 6f * baseUnit, paint)
        paint.setStyle(Paint.Style.FILL)

      case PlaceType.Prefecture =>
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(2.0f * baseUnit)
        canvas.drawCircle(x, y, 12f * baseUnit, paint)
        canvas.drawCircle(x, y, 6f * baseUnit, paint)
        paint.setStyle(Paint.Style.FILL)

      case PlaceType.County =>
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(2.0f * baseUnit)
        canvas.drawCircle(x, y, 10f * baseUnit, paint)
        paint.setStyle(Paint.Style.FILL)
        canvas.drawCircle(x, y, 5f * baseUnit, paint)

      case PlaceType.Town =>
        paint.setStyle(Paint.Style.FILL)
        canvas.drawCircle(x, y, 8f * baseUnit, paint)
    }
    paint.setColor(Color.WHITE)

    canvas.drawText(text, x + labelRadius, y - textBounds.exactCenterY(), paint)
    paint.setColor(savedColor)
  }

  private def drawRiver(river: River, screenZoomLevel: Int, canvasWidth: Int, canvasHeight: Int,
                        centerLon: Double, centerLat: Double,
                        text: String, canvas: Canvas, paint: Paint) {
    val savedColor = paint.getColor()
    val screenCoordinates = river.points map
      { p =>  val (x, y) = toScreenCoordinates(screenZoomLevel, canvasWidth, canvasHeight, centerLon, centerLat, p.x, p.y)
             Point(x, y) }

    paint.setStyle(Paint.Style.STROKE)
    paint.setStrokeWidth(river.width)
    paint.setColor(Color.rgb(0x0f, 0x98, 0xd4))

    for (i <- 0 until screenCoordinates.length - 1) {
      val start = screenCoordinates(i)
      val stop = screenCoordinates(i + 1)
      canvas.drawLine(start.x.asInstanceOf[Float], start.y.asInstanceOf[Float], stop.x.asInstanceOf[Float], stop.y.asInstanceOf[Float], paint)
    }

    //paint.setColor(Color.WHITE)
    //canvas.drawText(text, x + labelRadius, y - textBounds.exactCenterY(), paint)
    paint.setColor(savedColor)
  }

  /** Return longitude difference given pixel difference in x-coordinate. */
  def lonDiff(pixelDiff: Double) = worldFile.lonDiff(pixelDiff)

  /** Return latitude difference given pixel differnce in y-coordinate. */
  def latDiff(pixelDiff: Double) = worldFile.latDiff(pixelDiff)
}
