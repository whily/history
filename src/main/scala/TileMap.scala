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
    for (place <- places) {
      drawPointLabel(screenZoomLevel, canvasWidth, canvasHeight, centerLon, centerLat,
        place.lon, place.lat, place.ptype, place.name, canvas, paint)
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
    paint.setStrokeWidth(10.0f)
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

  private val ThreeKingdomPlaces =
    Array(Place("洛阳", 34.631514, 112.454681, PlaceType.Capital),   // To be confirmed
          Place("长安", 34.266667, 108.9),        // To be confirmed
          Place("武關", 33.71,110.35, PlaceType.Town),        // To be confirmed
          Place("函谷關", 34.519467, 110.869081, PlaceType.Town),        // To be confirmed
          Place("萧關", 36.016667, 106.25, PlaceType.Town),        // To be confirmed
          Place("大散關", 34.35, 107.366667, PlaceType.Town),        // To be confirmed
          Place("邺", 36.333333, 114.616667),      // Should be 20 km sw of the county.
          Place("许", 34.035803, 113.852478),    // To be confirmed
          Place("寿春", 32.0, 116.5),             // Estimated
          Place("鄄城", 35.566667, 115.5),    // To be confirmed
          Place("房陵", 32.1, 110.6),    // To be confirmed
          Place("上庸", 32.316667, 110.15),    // To be confirmed
          Place("潼关", 34.486389, 110.263611, PlaceType.Town),    // To be confirmed
          Place("合肥", 31.85, 117.266667),    // To be confirmed
          Place("濮阳", 35.75, 115.016667),    // To be confirmed
          Place("东阿", 36.333333, 116.25),    // To be confirmed
          Place("范", 35.85, 115.5),    // To be confirmed

          // 雍州
          // 中國歷史地圖集/三国/雍州: 祁山=2/3礼县 + 1/3天水市
          //   礼县 34.100833, 104.976944,  天水市 34.576, 105.709
          Place("祁山", 34.25922, 105.22096, PlaceType.County),
          // 中國歷史地圖集/三国/雍州: 上邽=天水市
          Place("上邽", 34.576, 105.709, PlaceType.County),

          // 司隸
          Place("河内郡", 35.1, 113.4),    // 懷縣/武陟县 To be confirmed
          Place("河東郡", 35.138333, 111.220833),    // 安邑/夏县 To be confirmed
          Place("左馮翊", 34.506, 109.051),    // 高陵縣/高陵縣 To be confirmed
          Place("右扶風", 34.2995, 108.4905),    // 槐里县/兴平市 To be confirmde
          Place("郿縣(褒斜道)", 34.27786, 107.60493, PlaceType.County),    // 眉县 To be confirmed

          // 冀州
          Place("渤海郡(南皮)", 38.033333, 116.7),    // 南皮/南皮县 To be confirmed

          // 豫州
          Place("汝南郡", 32.958056, 114.64),    // To be confirmed

          // 兖州
          Place("泰山郡", 36.204722, 117.159444),    // To be confirmed
          Place("濟北國", 36.8, 116.766667),    // To be confirmed
          Place("陈留郡", 34.8, 114.3),    // To be confirmed
          Place("官渡", 34.720967, 114.014228, PlaceType.Town),    // To be confirmed
          Place("乌巢", 35.3, 114.2, PlaceType.Town),    // To be confirmed
          Place("黎陽縣", 35.676111, 114.55, PlaceType.County),    // 浚县 To be confirmed
          Place("白馬", 35.466375, 114.636289, PlaceType.Town),    // 滑县 To be confirmed
          Place("延津", 35.3, 114.2, PlaceType.County),    // 延津 To be confirmed

          // 徐州
          Place("广陵郡", 32.4, 119.416667),    // To be confirmed
          Place("下邳", 34.316667, 117.95, PlaceType.Province),    // To be confirmed

          // 青州
          Place("北海國", 36.883333, 118.733333),    // To be confirmed
          Place("平原郡", 37.166667, 116.433333),    // 平原县/平原县 To be confirmed

          // 凉州
          Place("武都郡", 33.916667, 105.666667),    // 下辨县/成县 To be confirmed
          Place("金城郡", 36.3425, 102.858889),    // 允吾县/民和 To be confirmed
          Place("隴西郡(狄道縣)", 35.379422, 103.8564),    // 狄道縣/临洮县 To be confirmed

          // 并州
          Place("上党郡", 36.123889, 112.876944),    //  长子县/长子县 To be confirmed
          Place("太原郡", 37.866667, 112.55),    //  晋阳/太原市 To be confirmed
          Place("云中郡", 38.416667, 112.733333),    // 云中县/忻州市 To be confirmed
          Place("雁门郡", 39.3198, 112.432),    //  阴馆县/朔城区 To be confirmed
          Place("朔方郡", 40.327778, 107.003056),    //  临戎县/磴口县 To be confirmed

          // 幽州
          Place("涿郡", 39.486, 115.974),    // 涿县/涿州市 To be confirmed
          Place("代郡", 40.3675, 113.753056),    // 高柳县/阳高县 To be confirmed
          Place("渔阳郡", 40.374444, 116.839444),    // 渔阳县/密云县 To be confirmed
          Place("辽西郡", 41.533333, 121.233333),    // 阳乐县/义县 To be confirmed
          Place("辽东郡", 41.266667, 123.183333),    // 襄平县/辽阳市 To be confirmed
          Place("玄菟郡", 41.733333, 125.033333),    // 高句骊县/新宾 To be confirmed
          Place("乐浪郡", 39.019444, 125.738056),    // 朝鲜县/平壤 To be confirmed

          // 益州
          Place("成都", 30.658611, 104.064722, PlaceType.Capital),   // To be confirmed
          Place("汉中郡", 33.066667, 107.016667),    // To be confirmed
          Place("定軍山", 33.13756, 106.664275, PlaceType.Town),    // To be confirmed
          Place("兴势山", 33.216667, 107.533333, PlaceType.Town),    // 洋县 To be confirmed
          Place("街亭", 34.9071, 105.69833, PlaceType.Town),    // 秦安县??? To be confirme
          Place("列柳城", 33.91711, 106.5204, PlaceType.Town),    // 双石铺镇??? To be confirmed
          Place("陈仓", 34.35, 107.366667),    // 寶雞 To be confirmed
          Place("汉", 33.15, 106.666667, PlaceType.Town),    // 勉县 To be confirmed
          Place("乐", 32.15, 107.316667, PlaceType.Town),    // 城固县 To be confirmed
          Place("子口", 34.1645, 108.9502, PlaceType.Town),    // 长安区 To be confirmed
          Place("午口", 33.0475, 108.23, PlaceType.Town),    // 石泉縣 To be confirmed
          Place("木门道", 34.44066, 105.4979, PlaceType.Town),    // 牡丹镇 To be confirmed
          Place("沓中", 34, 103.55),    // 迭部县 To be confirmed
          Place("阳平關", 32.961353, 106.055392, PlaceType.Town),        // To be confirmed
          Place("涪城", 31.466667, 104.683333),    // To be confirmed
          Place("葭萌", 32.433333, 105.816667),    // To be confirmed
          Place("绵竹", 31.333333, 104.2),    // To be confirmed
          Place("阴平", 32.916667, 104.766667),    // To be confirmed
          Place("剑阁", 32.211505, 105.573795),    // 剑门关
          Place("永安", 31.0175, 109.465),    // To be confirmed
          Place("巴郡", 29.560454, 106.5734),    // 江州县/渝中区 To be confirmed
          Place("广汉郡(雒)", 30.99, 104.25),    // 雒县/广汉市 To be confirmed
          Place("犍为郡", 30.193333, 103.866667),    // 武阳县/彭山县 To be confirmed
          Place("益州郡", 24.668442, 102.591053),    // 滇池县/晋宁县 To be confirmed
          Place("永昌郡", 25.103889, 99.158056),    // 不韋縣/保山市? To be confirmed
          Place("牂柯郡", 26.573056, 106.706111),    // 故且蘭縣/貴陽市 To be confirmed
          Place("越巂郡", 27.894444, 102.264444),    // 邛都縣/西昌市 To be confirmed

          Place("建业", 32.05, 118.766667, PlaceType.Capital),    // To be confirmed
          Place("濡须", 31.678333, 117.735278),     // To be confirmed
          Place("庐陵", 27.133333, 115),    // To be confirmed
          Place("江夏", 30.554722, 114.312778),    // To be confirmed

          // 荆州
          Place("襄阳", 32.066667, 112.083333),    // To be confirmed
          Place("南阳", 33.004722, 112.5275),    // To be confirmed
          Place("新野縣", 32.52, 112.36, PlaceType.County),    // 新野縣 To be confirmed
          Place("當陽", 30.975278, 112.334444, PlaceType.County),    // 荆门市掇刀区. Different from 中國歷史地圖集: to be confirmed
          Place("宜城", 31.7, 112.366667, PlaceType.County),    // 宜城市 (中國歷史地圖集/三国/荆州)
          Place("夷陵", 30.766667, 111.316667, PlaceType.County),    // To be confirmed
          Place("夷道", 30.383333, 111.45, PlaceType.County),    // 宜都 (Marked by 中國歷史地圖集/三国/荆州)
          Place("猇亭", 30.535833, 111.423056, PlaceType.Town),    // 猇亭区 (Estimated from 中國歷史地圖集/三国/荆州)
          Place("赤壁", 29.72647, 113.93091, PlaceType.Town),    // To be confirmed
          Place("烏林", 30.016667, 113.533333, PlaceType.Town),    // 洪湖市 To be confirmed
          Place("麥城", 30.70029, 111.92649, PlaceType.Town),    // 两河镇 (当阳市) To be confirmed
          Place("武陵郡", 29.0035, 111.6928),    // 临沅县 To be confirmed
          Place("长沙郡", 28.196111, 112.972222),    // To be confirmed
          Place("桂阳郡", 25.8, 113.05),    // To be confirmed
          Place("零陵郡", 25.616667, 110.666667),    // To be confirmed
          Place("江陵", 30.133333, 112.5) ,       // To be confirmed
          Place("秭歸縣", 30.832677, 110.978394, PlaceType.County) ,       // 秭歸縣 To be confirmed
          Place("巫縣", 31.0769, 109.878216, PlaceType.County) ,       // 巫山縣 To be confirmed
          Place("華容縣", 30.4, 112.9) ,       // 潜江市 To be confirmed

          // 扬州
          Place("丹阳郡", 30.945, 118.814167),    // To be confirmed
          Place("庐江郡", 31.259898, 117.307838),    // 舒县 To be confirmed
          Place("会稽郡", 30.081944, 120.494722),    // To be confirmed
          Place("吴郡", 31.3, 120.6),    //  To be confirmed
          Place("豫章郡", 28.683333, 115.883333),    // 南昌 To be confirmed
          Place("柴桑縣", 29.738056, 115.987222),    // 九江市 To be confirmed
          Place("巴丘", 29.366667, 113.433333),    // 岳阳 To be confirmed

          // 交州
          Place("南海郡", 23.128795, 113.258976),    // 番禺县/广州市 To be confirmed
          Place("苍梧郡", 23.45, 111.466667),    // 广信县/封开县 To be confirmed
          Place("郁林郡", 22.633333, 110.15),    // 布山县/玉林市 To be confirmed
          Place("合浦郡", 21.675, 109.193056),    // 合浦县/合浦县 To be confirmed
          Place("交趾郡", 21.183333, 106.05),    // 龙编县/北宁市? To be confirmed
          Place("九真郡", 19.78, 105.71),    // 胥浦县/东山县 To be confirmed
          Place("日南郡", 16.830278, 107.097222)    // 西卷县/東河市 To be confirmed
    )

  // "To be confirmed" means that check is needed to verify with 中国历史地图集.
  private val TangPlaces =
    Array(
      // Province to be confirmed.
      Place("武關", 33.71,110.35, PlaceType.Town),        // To be confirmed
      Place("華州", 34.45, 109.75, PlaceType.Prefecture), // 治所在鄭縣（今華縣） To be confirmed
      Place("同州", 34.783611, 109.976667, PlaceType.Prefecture), // 治所在武鄉縣（今陝西省大荔縣） To be confirmed
      Place("陝州", 34.716667, 111.1, PlaceType.Prefecture), // 治所在陝縣（今河南省三門峽市陝州區）  To be confirmed
      Place("虢州", 34.525, 110.866667, PlaceType.Prefecture), // 治所在弘農縣（今河南省靈寶市）   To be confirmed
      Place("蒲州", 34.83444, 110.3245, PlaceType.Prefecture), // 治所在河東縣（今山西省永濟市西南蒲州鎮）  To be confirmed

      // 京兆府
      Place("长安", 34.266667, 108.9, PlaceType.Capital),        // To be confirmed
      Place("奉天", 34.496944, 108.248056, PlaceType.County), // 縣（今陝西乾縣）

      // 鳳翔節度使
      Place("鳳翔", 34.56, 107.420556, PlaceType.Prefecture), // 府,治所在雍縣（今陝西鳳翔） To be confirmed

      // 涇原節度使， 後稱彰義軍節度使
      Place("涇州", 35.332, 107.353, PlaceType.Prefecture), // 治所在安定縣（今甘肅涇川） To be confirmed
      Place("原州", 36.016667, 106.25, PlaceType.Prefecture), // 治所在平高縣（今寧夏固原） To be confirmed

      // 邠寧節度使
      Place("邠州", 35.054167, 108.081389, PlaceType.Prefecture), // 治所在新平縣（今陝西彬縣） To be confirmed
      Place("寧州", 35.5375, 108.155278, PlaceType.Prefecture), // 治今甘肅寧縣  To be confirmed
      Place("慶州", 35.739, 107.632, PlaceType.Prefecture), // 治今甘肅慶陽  To be confirmed

      // 鄜坊節度使
      Place("鄜州", 35.983333, 109.133333, PlaceType.Prefecture), // 治所在洛交縣（今陝西富縣） To be confirmed
      Place("坊州", 35.579722, 109.263056, PlaceType.Prefecture), // 治所在中部縣（今陝西黃陵） To be confirmed
      Place("丹州", 36.099444, 110.128333, PlaceType.Prefecture), // 治今陝西宜川  To be confirmed
      Place("延州", 36.5855, 109.4897, PlaceType.Prefecture), // 治今陝西省延安市東北  To be confirmed

      // 朔方節度使
      Place("靈州", 38.1, 106.333333, PlaceType.Prefecture), // 治所在回樂縣（今寧夏吳忠） To be confirmed
      Place("夏州", 38.000239, 108.851124, PlaceType.Prefecture), // 治所在朔方縣(今陝西省靖邊縣北白城子)   To be confirmed
      Place("鹽州", 37.449722, 107.828889, PlaceType.Prefecture), // 治所在五原縣（今陝西省定邊縣）  To be confirmed
      Place("綏州", 37.506944, 110.258889, PlaceType.Prefecture), // 治所在上縣（今綏德縣）  To be confirmed
      Place("銀州", 37.96466, 109.82087, PlaceType.Prefecture), // 治所在儒林縣（今陝西省橫山縣東黨岔鎮） To be confirmed
      Place("豐州", 41.09, 108.266111, PlaceType.Prefecture), // 內蒙古自治區五原縣南?? To be confirmed
      Place("勝州", 39.866667, 111.233333, PlaceType.Prefecture), // 治所在榆林縣（今內蒙古自治區准格爾旗東北十二連城, GPS coordinates is for 准格爾旗）  To be confirmed
      Place("麟州", 38.819444, 110.489722, PlaceType.Prefecture), // 治今陝西神木  To be confirmed
      Place("西受降城", 40.765961, 107.391206, PlaceType.Town), // 治今內蒙古巴彥淖爾市內  To be confirmed
      Place("中受降城", 40.633333, 109.983333, PlaceType.Town), // 治今內蒙古包頭市內  To be confirmed
      Place("東受降城", 40.816667, 111.65, PlaceType.Town), // 治今內蒙古呼和浩特市內  To be confirmed

      // 荊南節度使
      Place("江陵", 30.133333, 112.5, PlaceType.Prefecture), // 府,即荊州，治所在江陵縣（今湖北省江陵縣）
      Place("忠州", 30.3283, 108.047, PlaceType.Prefecture), // 治所在臨江縣（今重慶市忠縣）

      // 湖南觀察使
      Place("潭州", 28.196111, 112.972222, PlaceType.Prefecture), // 治所在長沙（今長沙）
      Place("邵州", 27.25, 111.466667, PlaceType.Prefecture), // 治所在邵陽縣（今湖南省邵陽市）
      Place("武岡", 26.359167, 110.310833, PlaceType.County), // 縣，属邵州（今湖南省城步苗族自治縣）
      Place("衡州", 26.9, 112.6, PlaceType.Prefecture), // 治所在衡陽縣（今湖南省衡陽市）
      Place("永州", 26.2215, 111.6169, PlaceType.Prefecture), // 治所在零陵縣（今湖南省永州市），GPS data from 零陵區
      Place("郴州", 25.8, 113.05, PlaceType.Prefecture), // 治所在郴縣（今湖南省郴州市）

      // 振武軍節度使
      Place("單于都護府", 40.44943, 111.78640, PlaceType.Prefecture),
        // （今內蒙古和林格爾北的土城子古城， searched on maps.bing.com for 古城子遗址, then looked up in http://dbsgeo.com/latlon/）

      Place("洛阳", 34.631514, 112.454681, PlaceType.Prefecture),   // To be confirmed

      // 魏博節度使
      Place("魏州", 36.28954, 115.11817, PlaceType.Prefecture), // 治今河北大名縣 To be confirmed
      Place("博州", 36.2729,  115.5909,  PlaceType.Prefecture), // 治今山東聊城市 To be confirmed
      Place("相州", 36.1,     114.333333, PlaceType.Prefecture), // 治今河南安陽市 To be confirmed
      Place("貝州", 37.06938, 115.65908, PlaceType.Prefecture), // 治今河北清河縣 To be confirmed
      Place("衛州", 35.461786, 113.805389, PlaceType.Prefecture), // 治今河南輝縣市 To be confirmed
      Place("澶州", 35.75,    115.016667, PlaceType.Prefecture), // 治今河南濮陽市 To be confirmed

      // 淮西節度使
      Place("蔡州", 33.007068, 114.362412, PlaceType.Prefecture), // 治今河南汝南 To be confirmed
      Place("申州", 32.131783, 114.071128, PlaceType.Prefecture), // 治今河南信陽 To be confirmed
      Place("光州", 32.146111, 115.138056, PlaceType.Prefecture), // 治今河南潢川 To be confirmed

      // 成德節度使
      Place("恆州", 38.148561, 114.561653, PlaceType.Prefecture), // 治今河北正定縣 To be confirmed
      Place("冀州", 37.551, 115.579, PlaceType.Prefecture), // 治今河北冀州市  To be confirmed
      Place("趙州", 37.746389, 114.768889, PlaceType.Prefecture), // 治今河北趙縣  To be confirmed
      Place("深州", 38.002, 115.559, PlaceType.Prefecture), // 治今河北深州市  To be confirmed

      // 幽州節度使
      Place("幽州", 39.916667, 116.383333, PlaceType.Prefecture), // 治今北京 To be confirmed
      Place("檀州", 40.374444, 116.839444, PlaceType.Prefecture), // 治今北京密雲  To be confirmed
      Place("薊州", 40.036389, 117.396944, PlaceType.Prefecture), // 治今天津薊縣  To be confirmed
      Place("媯州", 40.416667, 115.516667, PlaceType.Prefecture), // 治今河北懷來  To be confirmed
      Place("涿州", 39.486, 115.974, PlaceType.Prefecture), // 治今河北涿州  To be confirmed
      Place("莫州", 38.712, 116.099, PlaceType.Prefecture), // 治今河北任丘北  To be confirmed
      Place("瀛州", 38.447, 116.099, PlaceType.Prefecture), // 治今河北河間  To be confirmed
      Place("平州", 39.9, 118.9, PlaceType.Prefecture), // 治今河北盧龍  To be confirmed
      Place("營州", 41.433333, 120.166667, PlaceType.Prefecture), // 治今遼寧朝陽，唐末遷至今河北昌黎  To be confirmed

      // 淄青節度使
      Place("青州", 36.696667, 118.479722, PlaceType.Prefecture), // 治今山東青州市  To be confirmed
      Place("淄州", 36.643611, 117.966944, PlaceType.Prefecture), // 治今山東淄川  To be confirmed
      Place("齊州", 36.633333, 117.016667, PlaceType.Prefecture), // 治今山東濟南市  To be confirmed
      Place("沂州", 35.05, 118.316667, PlaceType.Prefecture), // 治今山東臨沂  To be confirmed
      Place("密州", 35.995278, 119.406667, PlaceType.Prefecture), // 治今山東諸城  To be confirmed
      Place("海州", 34.6, 119.166667, PlaceType.Prefecture), // 治今江蘇連雲港西南海州鎮  To be confirmed
      Place("登州", 37.8, 120.75, PlaceType.Prefecture), // 治今山東蓬萊  To be confirmed
      Place("萊州", 37.179167, 119.933333, PlaceType.Prefecture), // 治今山東萊州市  To be confirmed
      Place("曹州", 35.233333, 115.433333, PlaceType.Prefecture), // 治今山東菏澤  To be confirmed
      Place("濮州", 35.566667, 115.5, PlaceType.Prefecture), // 治今山東鄄城  To be confirmed
      Place("兗州", 35.55, 116.783333, PlaceType.Prefecture), // 治今山東兗州市  To be confirmed
      Place("鄆州", 35.908333, 116.3, PlaceType.Prefecture), // 治今山東東平 To be confirmed

      // 隴右節度使
      Place("鄯州", 36.516667, 102.416667, PlaceType.Prefecture), //  治今青海省海東市樂都區 To be confirmed
      Place("秦州", 34.580833, 105.724167, PlaceType.Prefecture), //  治今甘肅省天水市秦州區 To be confirmed
      Place("河州", 35.601, 103.211, PlaceType.Prefecture), //  治今甘肅省臨夏 To be confirmed
      Place("渭州", 35.083333, 104.65, PlaceType.Prefecture), //  治今甘肅省隴西縣 To be confirmed
      Place("蘭州", 36.016667, 103.866667, PlaceType.Prefecture), //   甘肅省蘭州市 To be confirmed
      //Place("臨州", , PlaceType.Prefecture), // 治今甘肅省臨洮縣???  To be confirmed
      Place("武州", 33.217, 105.146, PlaceType.Prefecture), // 治今甘肅省武都  To be confirmed
      Place("洮州", 34.7, 103.666667, PlaceType.Prefecture), // 治今甘肅省臨潭縣  To be confirmed
      Place("岷州", 35.379422, 103.8564, PlaceType.Prefecture), // 治今甘肅省臨洮縣??  To be confirmed
      Place("廓州", 36.04015, 101.433298, PlaceType.Prefecture), // 治今青海省貴德縣  To be confirmed
      Place("疊州", 34, 103.55, PlaceType.Prefecture), // 治今甘肅省迭部縣  To be confirmed
      Place("宕州", 33.633333, 104.333333, PlaceType.Prefecture), // 治今甘肅省舟曲縣  To be confirmed

      // // 河西節度使
      Place("涼州", 37.928, 102.641, PlaceType.Prefecture), // 治今武威市  Confirmed
      Place("甘州", 39.014444, 100.665833, PlaceType.Prefecture), // 治今甘肅省張掖市 Confirmed
      Place("肅州", 39.740986, 98.503418, PlaceType.Prefecture), // 治今甘肅省酒泉市 Confirmed
      Place("瓜州", 40.2467, 96.2027, PlaceType.Prefecture), // 治今甘肅省瓜州縣鎖陽城遺址 Confirmed
      Place("沙州", 40.139246, 94.644500, PlaceType.Prefecture), // 治今敦煌沙州城遺址, Confirmed

      // 北庭節度使
      Place("庭州", 44.0683584,89.2119097, PlaceType.Prefecture), //  治今吉木萨尔县城北12公里北庭乡,北庭故城. Confirmed
      Place("伊州", 42.8306, 93.505, PlaceType.Prefecture), // 治今新疆维吾尔自治区哈密市伊州区 Confirmed
      Place("西州", 42.856405,89.5264445, PlaceType.Prefecture), // 治今吐鲁番市东南高昌故城  Confirmed

      // 安西節度使
      Place("龟兹", 41.566667, 82.95, PlaceType.Prefecture), // 治今库车 Confirmed
      Place("焉耆", 41.9670519, 86.4605719, PlaceType.Prefecture), // 治今博格达沁故城  Confirmed
      Place("疏勒", 39.466667, 75.983333, PlaceType.Prefecture), // 治今喀什市  Confirmed
      Place("于阗", 37.100404, 79.7724963, PlaceType.Prefecture), // 治今和田西部的约特干 (Not consistent with 中国历史地图集)
      Place("碎叶", 42.805222, 75.199889, PlaceType.Prefecture), // 即今阿克·贝希姆遗址，位於今吉尔吉斯斯坦楚河州托克馬克市西南8公里处. To be confirmed
      Place("孽多城", 35.920833, 74.308333, PlaceType.Town),  // 小勃律首都, 治今巴基斯坦吉爾吉特.  Confirmed
      Place("怛羅斯", 42.525, 72.233333, PlaceType.Town),  // 接近哈薩克斯坦的塔拉茲.  Confirmed

      // 劍南節度使
      Place("益州", 30.659722, 104.063333, PlaceType.Prefecture), // 治今四川省成都市
      Place("維州", 31.416667, 103.166667, PlaceType.Prefecture), // 今四川省理縣東北  To be confirmed

      // 吐蕃
      Place("邏些城", 29.65, 91.1, PlaceType.Capital) // 治今拉薩. Confirmed

      // Place("", , PlaceType.Prefecture), // 治今  To be confirmed
    )
  private val places = TangPlaces

  private val YellowRiver1 = new River(Array(
    96.16415449310454733, 35.12966543176486311, 96.22877037852123294, 35.11176178593153452,
    96.24927819102123294, 35.10919830936903452, 96.48316490977123294, 35.10565827030653452,
    96.53419030039623294, 35.09613678593153452, 96.65487714935454733, 35.05011627811903452,
    96.70549563893791856, 35.04771556197319882, 96.74765058685454733, 35.05324941613986311,
    96.81128990977123294, 35.06134674686903452, 96.82927493581291856, 35.05390045780653452,
    96.86793053477123294, 35.01850006718153452, 96.88990319102123294, 35.00678131718153452,
    96.94670657643791856, 34.99970123905653452, 96.97461998789623294, 34.99164459843153452,
    96.98674563893791856, 34.97569407759819882, 96.99488365977123294, 34.94835032759819882,
    97.01539147227123294, 34.93744537968153452, 97.04208418060454733, 34.93280670780653452,
    97.07203209727123294, 34.93060944218153452
  ))

  private val YellowRiver2 =  new River(Array(
    97.28817793060454733, 34.83547597863986311, 97.30119876393791856, 34.81797923384819882,
    97.31592858164623294, 34.81683991093153452, 97.35279381602123294, 34.82180410363986311,
    97.40634199310454733, 34.81793854374403452, 97.42481530039623294, 34.82180410363986311,
    97.43726647227123294, 34.82843659061903452, 97.48495527435454733, 34.87128327030653452,
    97.50171959727123294, 34.87624746301486311, 97.54696699310454733, 34.87600332238986311
  ))

  private val YellowRiver3 = new River(Array(
    97.76278730560454733, 35.08808014530653452, 97.79590905039623294,35.11111074426486311,
    97.84888756602123294, 35.11225006718153452, 97.95175214935454733, 35.09613678593153452,
    97.99162845143791856, 35.08059316613986311, 98.01848392018791856, 35.04942454634819882,
    98.04297936289623294, 35.01231517134819882, 98.07585696706291856, 34.97882721561903452,
    98.10849043060454733, 34.95388418176486311, 98.12444095143791856, 34.93703847863986311,
    98.13127688893791856, 34.92076243697319882, 98.13111412852123294, 34.91002024947319882,
    98.13241621185454733, 34.90025462447319882, 98.13729902435454733, 34.89337799686903452,
    98.14755293060454733, 34.89069244999403452, 98.16179446706291856, 34.88922760624403452,
    98.19214928477123294, 34.87641022343153452, 98.26368248789623294, 34.86078522343153452,
    98.27507571706291856, 34.85215892134819882, 98.28467858164623294, 34.84210846561903452,
    98.30665123789623294, 34.83551666874403452, 98.34961998789623294, 34.82798899947319882,
    98.37794030039623294, 34.81529368697319882, 98.39177493581291856, 34.79682037968153452,
    98.39625084727123294, 34.77362702030653452, 98.39576256602123294, 34.74669017134819882,
    98.39511152435454733, 34.71983470259819882, 98.40219160247954733, 34.66677480676486311,
    98.40145918060454733, 34.64927806197319882, 98.39478600352123294, 34.63487376509819882,
    98.38095136810454733, 34.61916738488986311, 98.36190839935454733, 34.61041901249403452,
    98.34074954518791856, 34.60675690311903452, 98.32667076914623294, 34.59817129113986311,
    98.32984459727123294, 34.57477448124403452, 98.34514407643791856, 34.56325918176486311,
    98.39478600352123294, 34.54743073124403452, 98.40552819102123294, 34.53721751509819882,
    98.41350345143791856, 34.52663808801486311, 98.45215905039623294, 34.50328196822319882,
    98.46631920664623294, 34.49221425988986311, 98.46729576914623294, 34.48806386926486311,
    98.46574954518791856, 34.47687409061903452, 98.46631920664623294, 34.47235748905653452,
    98.46924889414623294, 34.46861399947319882, 98.47844485768791856, 34.45974355676486311,
    98.47999108164623294, 34.45868561405653452, 98.49016360768791856, 34.42438385624403452,
    98.49708092539623294, 34.41400787968153452, 98.51474043060454733, 34.40119049686903452,
    98.62737063893791856, 34.35744863488986311, 98.64112389414623294, 34.35504791874403452,
    98.70972740977123294, 34.35504791874403452, 98.72429446706291856, 34.36859772343153452,
    98.72429446706291856, 34.39952220259819882, 98.72071373789623294, 34.43321360884819882,
    98.72364342539623294, 34.45494212447319882, 98.73397871185454733, 34.45787181197319882,
    98.78296959727123294, 34.45123932499403452, 98.79786217539623294, 34.45567454634819882,
    98.83692467539623294, 34.47919342655653452, 98.86793053477123294, 34.47870514530653452,
    98.92823326914623294, 34.45307037968153452, 98.95289147227123294, 34.45868561405653452,
    98.97087649831291856, 34.44684479374403452, 98.97934003997954733, 34.43939850468153452,
    98.98650149831291856, 34.43077220259819882, 99.00204511810454733, 34.43687571822319882,
    99.01620527435454733, 34.43264394738986311, 99.04184003997954733, 34.41026439009819882,
    99.04468834727123294, 34.40969472863986311, 99.05323326914623294, 34.41099681197319882,
    99.05608157643791856, 34.41026439009819882, 99.05722089935454733, 34.40635814009819882,
    99.05689537852123294, 34.39724355676486311, 99.06226647227123294, 34.38715241093153452,
    99.06959069102123294, 34.36249420780653452, 99.04590905039623294, 34.32115306197319882,
    99.03785240977123294, 34.30064524947319882, 99.03492272227123294, 34.27004629113986311,
    99.03671308685454733, 34.25360748905653452, 99.04159589935454733, 34.24351634322319882,
    99.05608157643791856, 34.22589752811903452, 99.06356855560454733, 34.21393463749403452,
    99.06755618581291856, 34.20502350468153452, 99.06934655039623294, 34.19440338749403452,
    99.06959069102123294, 34.17751699426486311, 99.07056725352123294, 34.17108795780653452,
    99.07496178477123294, 34.16002024947319882, 99.07577558685454733, 34.15334707238986311,
    99.07374108164623294, 34.14618561405653452, 99.06438235768791856, 34.13259511926486311,
    99.06226647227123294, 34.12567780155653452, 99.06006920664623294, 34.11290110884819882,
    99.05079186289623294, 34.08344147343153452, 99.04859459727123294, 34.07135651249403452,
    99.04843183685454733, 34.04600657759819882, 99.05038496185454733, 34.03477610884819882,
    99.05608157643791856, 34.01984284061903452, 99.06739342539623294, 33.99884674686903452,
    99.06877688893791856, 33.98688385624403452, 99.06226647227123294, 33.97146230676486311,
    99.08147220143791856, 33.96682363488986311, 99.09677168060454733, 33.95014069218153452,
    99.10694420664623294, 33.92963287968153452, 99.11060631602123294, 33.91339752811903452,
    99.11923261810454733, 33.90668366093153452, 99.17212975352123294, 33.87584056197319882,
    99.24805748789623294, 33.78436920780653452, 99.28199303477123294, 33.75914134322319882,
    99.30616295664623294, 33.74660879113986311, 99.32284589935454733, 33.74323151249403452,
    99.36109459727123294, 33.74616119999403452, 99.36923261810454733, 33.74787018436903452,
    99.37688235768791856, 33.75275299686903452, 99.38233483164623294, 33.76019928593153452,
    99.38428795664623294, 33.76972077030653452, 99.38648522227123294, 33.77541738488986311,
    99.39014733164623294, 33.77960846561903452, 99.39201907643791856, 33.78396230676486311,
    99.38485761810454733, 33.79425690311903452, 99.38111412852123294, 33.80145905155653452,
    99.37842858164623294, 33.80902741093153452, 99.37818444102123294, 33.81439850468153452,
    99.39527428477123294, 33.82721588749403452, 99.42212975352123294, 33.82807037968153452,
    99.48047936289623294, 33.81846751509819882, 99.50123131602123294, 33.80910879113986311,
    99.52548261810454733, 33.80280182499403452, 99.53174889414623294, 33.79307689009819882,
    99.53549238372954733, 33.78192780155653452, 99.54200280039623294, 33.77346425988986311,
    99.56934655039623294, 33.76597728072319882, 99.58643639414623294, 33.76793040572319882,
    99.64576256602123294, 33.76565175988986311, 99.65870201914623294, 33.76972077030653452,
    99.69247480560454733, 33.79547760624403452, 99.69434655039623294, 33.80011627811903452,
    99.69727623789623294, 33.80479564009819882, 99.70329837331291856, 33.80695221561903452,
    99.72754967539623294, 33.80695221561903452, 99.77149498789623294, 33.79999420780653452,
    99.79224694102123294, 33.79266998905653452, 99.81495201914623294, 33.77838776249403452,
    99.83700605560454733, 33.77566152551486311, 99.84734134206291856, 33.76972077030653452,
    99.85783938893791856, 33.76129791874403452, 99.87566165456291856, 33.75275299686903452,
    99.88591556081291856, 33.74616119999403452, 99.89234459727123294, 33.73904043176486311,
    99.90145918060454733, 33.72341543176486311, 99.90577233164623294, 33.71820709843153452,
    99.91553795664623294, 33.7324290572319882,  99.92392011810454733, 33.71210358280653452,
    99.93303470143791856, 33.70974355676486311, 99.95923912852123294, 33.69061920780653452,
    99.99911543060454733, 33.67564524947319882, 100.01563561289623294, 33.66360097863986311,
    100.01376386810454733, 33.66384511926486311, 100.01563561289623294, 33.65733470259819882,
    100.01913496185454733, 33.64895254113986311, 100.02247155039623294, 33.64313385624403452,
    100.04826907643791856, 33.61835358280653452, 100.05315188893791856, 33.61517975468153452,
    100.06413821706291856, 33.61090729374403452, 100.07056725352123294, 33.60150787968153452,
    100.07496178477123294, 33.59210846561903452, 100.08008873789623294, 33.58791738488986311,
    100.12492923268791856, 33.58165110884819882, 100.14869225352123294, 33.57359446822319882,
    100.15894615977123294, 33.57424550988986311, 100.16684003997954733, 33.57868073124403452,
    100.17139733164623294, 33.58555735884819882, 100.17408287852123294, 33.59190501509819882,
    100.17595462331291856, 33.59467194218153452, 100.18726647227123294, 33.59947337447319882,
    100.22616621185454733, 33.62335846561903452, 100.23780358164623294, 33.63251373905653452,
    100.28069095143791856, 33.67731354374403452, 100.28866621185454733, 33.69151439009819882,
    100.28614342539623294, 33.70648834843153452, 100.27588951914623294, 33.71983470259819882,
    100.26343834727123294, 33.73261139530653452, 100.25464928477123294, 33.74616119999403452,
    100.24089602956291856, 33.73871491093153452, 100.24561608164623294, 33.76032135624403452,
    100.26718183685454733, 33.76190827030653452, 100.31674238372954733, 33.74616119999403452,
    100.32333418060454733, 33.75759511926486311, 100.35783938893791856, 33.75641510624403452,
    100.37501061289623294, 33.76255931197319882, 100.40479576914623294, 33.78693268436903452,
    100.42164147227123294, 33.79673899947319882, 100.43669681081291856, 33.80072662968153452,
    100.44914798268791856, 33.80609772343153452, 100.47999108164623294, 33.82953522343153452,
    100.49854576914623294, 33.83490631718153452, 100.51677493581291856, 33.83771393436903452,
    100.56495201914623294, 33.85480377811903452, 100.58073977956291856, 33.86530182499403452,
    100.60450280039623294, 33.88918691613986311, 100.63209069102123294, 33.91046784061903452,
    100.66399173268791856, 33.92556386926486311, 100.70020592539623294, 33.93048737186903452,
    100.76604251393791856, 33.92816803593153452, 100.78223717539623294, 33.92426178593153452,
    100.79102623789623294, 33.91270579634819882, 100.80152428477123294, 33.89382558801486311,
    100.81454511810454733, 33.87982819218153452, 100.83057701914623294, 33.88332754113986311,
    100.83651777435454733, 33.86151764530653452, 100.85035240977123294, 33.83356354374403452,
    100.86988365977123294, 33.80796946822319882, 100.89210045664623294, 33.79328034061903452,
    100.89698326914623294, 33.79983144738986311, 100.89820397227123294, 33.80280182499403452,
    100.89950605560454733, 33.80695221561903452, 100.90390058685454733, 33.78961823124403452,
    100.91203860768791856, 33.78558991093153452, 100.92042076914623294, 33.78713613488986311,
    100.92619876393791856, 33.78644440311903452, 100.93018639414623294, 33.77794017134819882,
    100.92872155039623294, 33.77094147343153452, 100.92237389414623294, 33.76491933801486311,
    100.91252688893791856, 33.75914134322319882, 100.95899498789623294, 33.73806386926486311,
    100.97990970143791856, 33.73749420780653452, 100.99569746185454733, 33.75173574426486311,
    100.99463951914623294, 33.74274323124403452, 100.99610436289623294, 33.73346588749403452,
    101.00066165456291856, 33.72658925988986311, 101.00936933685454733, 33.72504303593153452,
    101.01653079518791856, 33.72960032759819882, 101.01954186289623294, 33.73781972863986311,
    101.02149498789623294, 33.74725983280653452, 101.03443444102123294, 33.77175527551486311,
    101.03443444102123294, 33.79108307499403452, 101.02833092539623294, 33.80984121301486311,
    101.01905358164623294, 33.82432689009819882, 101.01563561289623294, 33.83136627811903452,
    101.01474043060454733, 33.84044017134819882, 101.01563561289623294, 33.85850657759819882,
    101.01832115977123294, 33.87193431197319882, 101.02548261810454733, 33.87689850468153452,
    101.03459720143791856, 33.87697988488986311, 101.04346764414623294, 33.87584056197319882,
    101.06397545664623294, 33.86379629113986311, 101.07422936289623294, 33.86159902551486311,
    101.10254967539623294, 33.86257558801486311, 101.11182701914623294, 33.86159902551486311,
    101.13908938893791856, 33.84442780155653452, 101.18287194102123294, 33.78607819218153452,
    101.23625735768791856, 33.75962962447319882, 101.26075280039623294, 33.73212311405653452,
    101.28817793060454733, 33.71137116093153452, 101.32333418060454733, 33.71820709843153452,
    101.33554121185454733, 33.70819733280653452, 101.35661868581291856, 33.70372142134819882,
    101.37655683685454733, 33.69712962447319882, 101.38550865977123294, 33.68069082238986311,
    101.38965905039623294, 33.67662181197319882, 101.39909915456291856, 33.67784251509819882,
    101.40837649831291856, 33.68219635624403452, 101.41773522227123294, 33.69452545780653452,
    101.42920983164623294, 33.69216543176486311, 101.44296308685454733, 33.68663157759819882,
    101.45427493581291856, 33.68410879113986311, 101.46281985768791856, 33.68732330936903452,
    101.48389733164623294, 33.70038483280653452, 101.49537194102123294, 33.70453522343153452,
    101.50871829518791856, 33.70477936405653452, 101.54004967539623294, 33.69778066613986311,
    101.54550214935454733, 33.69452545780653452, 101.56340579518791856, 33.68044668176486311,
    101.57422936289623294, 33.67727285363986311, 101.58602949310454733, 33.67580800988986311,
    101.60661868581291856, 33.67019277551486311, 101.61890709727123294, 33.66978587447319882,
    101.68702233164623294, 33.68231842655653452, 101.73731530039623294, 33.68345774947319882,
    101.75367272227123294, 33.68024323124403452, 101.76677493581291856, 33.67206452030653452,
    101.82032311289623294, 33.62506744999403452, 101.83757571706291856, 33.60598379113986311,
    101.84473717539623294, 33.58478424686903452, 101.85173587331291856, 33.57294342655653452,
    101.88379967539623294, 33.54621002811903452, 101.89258873789623294, 33.52643463749403452,
    101.89112389414623294, 33.51752350468153452, 101.88762454518791856, 33.50751373905653452,
    101.88575280039623294, 33.49742259322319882, 101.88917076914623294, 33.48855215051486311,
    101.97453860768791856, 33.40350983280653452, 102.00928795664623294, 33.37628815311903452,
    102.05225670664623294, 33.35435618697319882, 102.09669030039623294, 33.33991119999403452,
    102.16553795664623294, 33.33319733280653452, 102.17432701914623294, 33.33458079634819882,
    102.18319746185454733, 33.34052155155653452, 102.20207767018791856, 33.35822174686903452,
    102.21151777435454733, 33.36192454634819882, 102.22112063893791856, 33.36973704634819882,
    102.25635826914623294, 33.41315338749403452, 102.26653079518791856, 33.41901276249403452,
    102.28899173268791856, 33.41608307499403452, 102.32138105560454733, 33.40660228072319882,
    102.33350670664623294, 33.41351959843153452, 102.34319095143791856, 33.41164785363986311,
    102.34929446706291856, 33.40643952030653452, 102.35198001393791856, 33.40350983280653452,
    102.36125735768791856, 33.40432363488986311, 102.37549889414623294, 33.40827057499403452,
    102.38591556081291856, 33.40973541874403452, 102.43938235768791856, 33.44851308801486311,
    102.46184329518791856, 33.45754629113986311, 102.45899498789623294, 33.46149323124403452,
    102.45419355560454733, 33.47178782759819882, 102.46729576914623294, 33.46991608280653452,
    102.47120201914623294, 33.46869537968153452, 102.47608483164623294, 33.46495189009819882,
    102.48161868581291856, 33.46495189009819882, 102.46534264414623294, 33.48627350468153452,
    102.45850670664623294, 33.49872467655653452, 102.45419355560454733, 33.51276276249403452,
    102.45272871185454733, 33.52326080936903452, 102.45329837331291856, 33.53550853072319882,
    102.45989017018791856, 33.54157135624403452, 102.47608483164623294, 33.53327057499403452,
    102.48161868581291856, 33.54006582238986311, 102.42343183685454733, 33.57623932499403452,
    102.41325931081291856, 33.57794830936903452, 102.40259850352123294, 33.59402090051486311,
    102.35572350352123294, 33.60932037968153452, 102.34514407643791856, 33.62575918176486311,
    102.34188886810454733, 33.63202545780653452, 102.32772871185454733, 33.64610423384819882,
    102.32447350352123294, 33.65334707238986311, 102.32642662852123294, 33.67613353072319882,
    102.33139082122954733, 33.69778066613986311, 102.33651777435454733, 33.70917389530653452,
    102.34188886810454733, 33.71731191613986311, 102.34286543060454733, 33.72512441613986311,
    102.33472740977123294, 33.73562246301486311, 102.32740319102123294, 33.73448314009819882,
    102.31739342539623294, 33.72638580936903452, 102.30486087331291856, 33.72024160363986311,
    102.29045657643791856, 33.72504303593153452, 102.29810631602123294, 33.73440175988986311,
    102.30591881602123294, 33.73993561405653452, 102.31454511810454733, 33.74465566613986311,
    102.32447350352123294, 33.75173574426486311, 102.31413821706291856, 33.76744212447319882,
    102.29932701914623294, 33.78070709843153452, 102.28077233164623294, 33.78990306197319882,
    102.25977623789623294, 33.79328034061903452, 102.24708092539623294, 33.79755280155653452,
    102.24374433685454733, 33.80695221561903452, 102.24846438893791856, 33.81635162968153452,
    102.25977623789623294, 33.82062409061903452, 102.26392662852123294, 33.82603587447319882,
    102.25725345143791856, 33.83799876509819882, 102.19434655039623294, 33.91925690311903452,
    102.16651451914623294, 33.94582754113986311, 102.13282311289623294, 33.96519603072319882,
    102.10995527435454733, 33.95372142134819882, 102.06959069102123294, 33.96845123905653452,
    102.05030358164623294, 33.95783112186903452, 102.02719160247954733, 33.97907135624403452,
    101.98642011810454733, 34.00027090051486311, 101.94345136810454733, 34.01056549686903452,
    101.91309655039623294, 33.99937571822319882, 101.85840905039623294, 34.03974030155653452,
    101.82984459727123294, 34.05206940311903452, 101.81641686289623294, 34.06016673384819882,
    101.80323326914623294, 34.07505931197319882, 101.78508548268791856, 34.06618886926486311,
    101.75953209727123294, 34.07269928593153452, 101.73731530039623294, 34.08881256718153452,
    101.72877037852123294, 34.10858795780653452, 101.71843509206291856, 34.10329824426486311,
    101.71477298268791856, 34.10578034061903452, 101.71257571706291856, 34.11151764530653452,
    101.70761152435454733, 34.11603424686903452, 101.68091881602123294, 34.12225983280653452,
    101.64698326914623294, 34.13593170780653452, 101.63575280039623294, 34.14520905155653452,
    101.63925214935454733, 34.15704987186903452, 101.63078860768791856, 34.16604238488986311,
    101.62175540456291856, 34.17145416874403452, 101.61475670664623294, 34.17824941613986311,
    101.61133873789623294, 34.19118886926486311, 101.56959069102123294, 34.20807526249403452,
    101.52938886810454733, 34.21845123905653452, 101.51701907643791856, 34.21930573124403452,
    101.49610436289623294, 34.21804433801486311, 101.48845462331291856, 34.21845123905653452,
    101.48047936289623294, 34.22162506718153452, 101.47274824310454733, 34.22626373905653452,
    101.46338951914623294, 34.23033274947319882, 101.42538496185454733, 34.23423899947319882,
    101.36500084727123294, 34.25263092655653452, 101.33423912852123294, 34.25653717655653452,
    101.32333418060454733, 34.25946686405653452, 101.31495201914623294, 34.26496002811903452,
    101.30787194102123294, 34.27204010624403452, 101.29924563893791856, 34.27794017134819882,
    101.28614342539623294, 34.28054433801486311, 101.27971438893791856, 34.28363678593153452,
    101.26571699310454733, 34.29730866093153452, 101.25578860768791856, 34.30040110884819882,
    101.24268639414623294, 34.29999420780653452, 101.23397871185454733, 34.30072662968153452,
    101.22934003997954733, 34.30560944218153452, 101.22657311289623294, 34.32782623905653452,
    101.22234134206291856, 34.33527252811903452, 101.21501712331291856, 34.33978912968153452,
    101.20419355560454733, 34.34137604374403452, 101.19727623789623294, 34.34516022343153452,
    101.19556725352123294, 34.35443756718153452, 101.19581139414623294, 34.36579010624403452,
    101.19426517018791856, 34.37616608280653452, 101.19605553477123294, 34.38381582238986311,
    101.19638105560454733, 34.38996002811903452, 101.19426517018791856, 34.39602285363986311,
    101.19044030039623294, 34.39691803593153452, 101.17774498789623294, 34.39508698124403452,
    101.17383873789623294, 34.39602285363986311, 101.16570071706291856, 34.40635814009819882,
    101.16407311289623294, 34.41095612186903452, 101.16529381602123294, 34.41583893436903452,
    101.16643313893791856, 34.42735423384819882, 101.16399173268791856, 34.44322337447319882,
    101.14901777435454733, 34.48163483280653452, 101.13965905039623294, 34.49961985884819882,
    101.06055748789623294, 34.50588613488986311, 101.05217532643791856, 34.50930410363986311,
    101.04249108164623294, 34.52419668176486311, 101.03296959727123294, 34.52761465051486311,
    101.02051842539623294, 34.53009674686903452, 101.01408938893791856, 34.53591543176486311,
    101.00904381602123294, 34.54230377811903452, 101.00196373789623294, 34.54682037968153452,
    100.95069420664623294, 34.54694244999403452, 100.92896569102123294, 34.55182526249403452,
    100.91993248789623294, 34.57164134322319882, 100.91684003997954733, 34.59577057499403452,
    100.90870201914623294, 34.61387767134819882, 100.88583418060454733, 34.64984772343153452,
    100.87061608164623294, 34.70880768436903452, 100.86133873789623294, 34.71812571822319882,
    100.83586673268791856, 34.71491119999403452, 100.81755618581291856, 34.70665110884819882,
    100.78223717539623294, 34.68459707238986311, 100.76343834727123294, 34.67784251509819882,
    100.74293053477123294, 34.67369212447319882, 100.69678795664623294, 34.67096588749403452,
    100.67481530039623294, 34.67572662968153452, 100.63567142018791856, 34.69289785363986311,
    100.61825605560454733, 34.69147369999403452, 100.57398522227123294, 34.73981354374403452,
    100.55909264414623294, 34.74530670780653452, 100.53956139414623294, 34.75808340051486311,
    100.52255293060454733, 34.77277252811903452, 100.51522871185454733, 34.78396230676486311,
    100.49463951914623294, 34.80548737186903452, 100.44719485768791856, 34.81895579634819882,
    100.35450280039623294, 34.82798899947319882, 100.33635501393791856, 34.83384837447319882,
    100.32772871185454733, 34.84825267134819882, 100.31674238372954733, 34.88324616093153452,
    100.30925540456291856, 34.89215729374403452, 100.29420006602123294, 34.90692780155653452,
    100.28866621185454733, 34.91734446822319882, 100.28581790456291856, 34.92938873905653452,
    100.28866621185454733, 34.95831940311903452, 100.28581790456291856, 34.98456452030653452,
    100.27833092539623294, 34.99937571822319882, 100.25464928477123294, 35.02720774947319882,
    100.24903405039623294, 35.04621002811903452, 100.24903405039623294, 35.08921946822319882,
    100.24089602956291856, 35.10919830936903452, 100.24765058685454733, 35.13287994999403452,
    100.24716230560454733, 35.17361074426486311, 100.23845462331291856, 35.21491119999403452,
    100.22046959727123294, 35.24066803593153452, 100.23023522227123294, 35.25385162968153452,
    100.24195397227123294, 35.26203034061903452, 100.25334720143791856, 35.26793040572319882,
    100.26197350352123294, 35.27476634322319882, 100.27222740977123294, 35.28949616093153452,
    100.27125084727123294, 35.29543691613986311, 100.24846438893791856, 35.30206940311903452,
    100.21989993581291856, 35.31643300988986311, 100.20533287852123294, 35.33063385624403452,
    100.20370527435454733, 35.34979889530653452, 100.21428470143791856, 35.37844472863986311,
    100.19312584727123294, 35.37095774947319882, 100.19117272227123294, 35.40228912968153452,
    100.16838626393791856, 35.42865631718153452, 100.14234459727123294, 35.45022207238986311,
    100.13111412852123294, 35.46657949426486311, 100.13591556081291856, 35.47211334843153452,
    100.15479576914623294, 35.47943756718153452, 100.15894615977123294, 35.48395416874403452,
    100.15796959727123294, 35.50783925988986311, 100.15894615977123294, 35.51496002811903452,
    100.16643313893791856, 35.53278229374403452, 100.17481530039623294, 35.54234446822319882,
    100.19996178477123294, 35.56281159061903452, 100.20761152435454733, 35.57404205936903452,
    100.24065188893791856, 35.66168854374403452, 100.28248131602123294, 35.72394440311903452,
    100.29582767018791856, 35.75128815311903452, 100.29615319102123294, 35.77167389530653452,
    100.33716881602123294, 35.77167389530653452, 100.34839928477123294, 35.77476634322319882,
    100.36044355560454733, 35.79096100468153452, 100.37118574310454733, 35.79840729374403452,
    100.39055423268791856, 35.80056386926486311, 100.41122480560454733, 35.79572174686903452,
    100.44703209727123294, 35.77912018436903452, 100.44255618581291856, 35.80084869999403452,
    100.44434655039623294, 35.81818268436903452, 100.45508873789623294, 35.82965729374403452,
    100.47779381602123294, 35.83376699426486311, 100.49708092539623294, 35.84157949426486311,
    100.52922610768791856, 35.87787506718153452, 100.54306074310454733, 35.88898346561903452,
    100.55469811289623294, 35.89044830936903452, 100.57927493581291856, 35.88800690311903452,
    100.59091230560454733, 35.88898346561903452, 100.60108483164623294, 35.89386627811903452,
    100.61793053477123294, 35.90635814009819882, 100.62883548268791856, 35.90888092655653452,
    100.63778730560454733, 35.91311269738986311, 100.63892662852123294, 35.92247142134819882,
    100.63827558685454733, 35.93191152551486311, 100.64210045664623294, 35.93614329634819882,
    100.70411217539623294, 35.97589752811903452, 100.72046959727123294, 35.98908112186903452,
    100.72754967539623294, 36.00128815311903452, 100.72763105560454733, 36.03143952030653452,
    100.73088626393791856, 36.05280182499403452, 100.74170983164623294, 36.07086823124403452,
    100.81592858164623294, 36.13226959843153452, 100.84237714935454733, 36.14008209843153452,
    100.90097089935454733, 36.13275787968153452, 100.93840579518791856, 36.13975657759819882,
    100.95809980560454733, 36.14024485884819882, 100.99268639414623294, 36.13263580936903452,
    101.00489342539623294, 36.13243235884819882, 101.01270592539623294, 36.13458893436903452,
    101.02979576914623294, 36.14227936405653452, 101.03801517018791856, 36.14484284061903452,
    101.06413821706291856, 36.14642975468153452, 101.07960045664623294, 36.13967519738986311,
    101.08757571706291856, 36.12453847863986311, 101.09180748789623294, 36.10089752811903452,
    101.10547936289623294, 36.10093821822319882, 101.13518313893791856, 36.11558665572319882,
    101.15064537852123294, 36.11957428593153452, 101.16627037852123294, 36.11847565311903452,
    101.17986087331291856, 36.11359284061903452, 101.18905683685454733, 36.10378652551486311,
    101.19117272227123294, 36.08759186405653452, 101.20549563893791856, 36.08612702030653452,
    101.23438561289623294, 36.09247467655653452, 101.24960371185454733, 36.09320709843153452,
    101.26213626393791856, 36.09117259322319882, 101.27369225352123294, 36.08795807499403452,
    101.32707767018791856, 36.06159088749403452, 101.35881595143791856, 36.05349355676486311,
    101.39201907643791856, 36.05109284061903452, 101.42725670664623294, 36.05292389530653452,
    101.45647220143791856, 36.05772532759819882, 101.47852623789623294, 36.06594472863986311,
    101.63127688893791856, 36.15399811405653452, 101.65381920664623294, 36.17377350468153452,
    101.66643313893791856, 36.18158600468153452, 101.68108157643791856, 36.18565501509819882,
    101.69719485768791856, 36.18435293176486311, 101.78272545664623294, 36.14606354374403452,
    101.78923587331291856, 36.14496491093153452, 101.81194095143791856, 36.14634837447319882,
    101.82658938893791856, 36.14439524947319882, 101.83993574310454733, 36.14065175988986311,
    101.85230553477123294, 36.13434479374403452, 101.90577233164623294, 36.08840566613986311,
    101.91309655039623294, 36.08698151249403452, 101.91285240977123294, 36.06842682499403452,
    101.95842532643791856, 36.05324941613986311, 101.94776451914623294, 36.03860097863986311,
    101.98560631602123294, 36.02081940311903452, 102.01612389414623294, 35.99156321822319882,
    102.03663170664623294, 35.95612213749403452, 102.04403730560454733, 35.91937897343153452,
    102.04810631602123294, 35.91229889530653452, 102.06649824310454733, 35.89472077030653452,
    102.07073001393791856, 35.88528066613986311, 102.07178795664623294, 35.86412181197319882,
    102.07398522227123294, 35.85525136926486311, 102.07813561289623294, 35.84743886926486311,
    102.09888756602123294, 35.83014557499403452, 102.12151126393791856, 35.82709381718153452,
    102.16358483164623294, 35.83376699426486311, 102.18238365977123294, 35.84015534061903452,
    102.22559655039623294, 35.86827220259819882, 102.24610436289623294, 35.87470123905653452,
    102.34514407643791856, 35.87470123905653452, 102.41740970143791856, 35.88772207238986311,
    102.44149824310454733, 35.88776276249403452, 102.45842532643791856, 35.88292064009819882,
    102.48918704518791856, 35.86688873905653452, 102.50627688893791856, 35.86168040572319882,
    102.55486087331291856, 35.85704173384819882, 102.60458418060454733, 35.84210846561903452,
    102.63648522227123294, 35.83864980676486311, 102.70484459727123294, 35.83946360884819882,
    102.71680748789623294, 35.83734772343153452, 102.72559655039623294, 35.83319733280653452,
    102.73503665456291856, 35.83034902551486311, 102.74976647227123294, 35.83197662968153452,
    102.75953209727123294, 35.83751048384819882, 102.78720136810454733, 35.85993073124403452,
    102.83733157643791856, 35.86066315311903452, 102.85889733164623294, 35.85696035363986311,
    102.87256920664623294, 35.84625885624403452, 102.89950605560454733, 35.84988027551486311,
    103.00920657643791856, 35.82579173384819882, 103.11646569102123294, 35.77818431197319882,
    103.13331139414623294, 35.78111399947319882, 103.13819420664623294, 35.79161204634819882,
    103.14983157643791856, 35.79804108280653452, 103.16423587331291856, 35.80365631718153452,
    103.18628990977123294, 35.81708405155653452, 103.19678795664623294, 35.82033925988986311,
    103.21900475352123294, 35.82233307499403452, 103.22934003997954733, 35.82550690311903452,
    103.24455813893791856, 35.83966705936903452, 103.25318444102123294, 35.84284088749403452,
    103.26767011810454733, 35.84597402551486311, 103.28711998789623294, 35.85382721561903452,
    103.30420983164623294, 35.86387767134819882, 103.31145267018791856, 35.87384674686903452,
    103.31731204518791856, 35.88519928593153452, 103.35930423268791856, 35.91168854374403452,
    103.27051842539623294, 35.93842194218153452, 103.26368248789623294, 35.94171784061903452,
    103.25025475352123294, 35.95201243697319882, 103.24293053477123294, 35.95567454634819882,
    103.23593183685454733, 35.95685455936903452, 103.22193444102123294, 35.95661041874403452,
    103.21526126393791856, 35.95746491093153452, 103.20362389414623294, 35.96145254113986311,
    103.19402102956291856, 35.96739329634819882, 103.18873131602123294, 35.97650787968153452,
    103.18913821706291856, 35.98977285363986311, 103.19402102956291856, 35.99933502811903452,
    103.20240319102123294, 36.00710683801486311, 103.22071373789623294, 36.02057526249403452,
    103.25041751393791856, 36.05548737186903452, 103.26132246185454733, 36.06561920780653452,
    103.39234459727123294, 36.13104889530653452, 103.42294355560454733, 36.15607330936903452,
    103.42473392018791856, 36.16156647343153452, 103.43067467539623294, 36.16583893436903452,
    103.44678795664623294, 36.18463776249403452, 103.45484459727123294, 36.18891022343153452,
    103.49276777435454733, 36.18492259322319882, 103.50261477956291856, 36.18207428593153452,
    103.51514733164623294, 36.16978587447319882, 103.54102623789623294, 36.13422272343153452,
    103.55445397227123294, 36.12681712447319882, 103.57463626393791856, 36.13031647343153452,
    103.61044355560454733, 36.14520905155653452, 103.63428795664623294, 36.14789459843153452,
    103.64625084727123294, 36.14651113488986311, 103.65455162852123294, 36.14333730676486311,
    103.66773522227123294, 36.13365306197319882, 103.69556725352123294, 36.10386790572319882,
    103.69841556081291856, 36.09890371301486311, 103.74895267018791856, 36.09467194218153452,
    103.87647545664623294, 36.06464264530653452, 103.92180423268791856, 36.06098053593153452,
    104.01278730560454733, 36.07196686405653452, 104.03467858164623294, 36.07847728072319882,
    104.04395592539623294, 36.08893463749403452, 104.04542076914623294, 36.11005280155653452,
    104.04468834727123294, 36.12193431197319882, 104.04070071706291856, 36.12372467655653452,
    104.04794355560454733, 36.13650136926486311, 104.04143313893791856, 36.14288971561903452,
    104.02336673268791856, 36.14789459843153452, 104.01864668060454733, 36.18223704634819882,
    104.04794355560454733, 36.20502350468153452, 104.09091230560454733, 36.22308991093153452,
    104.12696373789623294, 36.24351634322319882, 104.13233483164623294, 36.25104401249403452,
    104.14014733164623294, 36.27704498905653452, 104.15723717539623294, 36.29999420780653452,
    104.19149824310454733, 36.33527252811903452, 104.23259524831291856, 36.36835358280653452,
    104.25953209727123294, 36.37868886926486311, 104.31731204518791856, 36.38259511926486311,
    104.34514407643791856, 36.39032623905653452, 104.36426842539623294, 36.40436432499403452,
    104.39901777435454733, 36.43939850468153452, 104.42001386810454733, 36.45050690311903452,
    104.44532311289623294, 36.45412832238986311, 104.46981855560454733, 36.45502350468153452,
    104.49390709727123294, 36.45966217655653452, 104.51758873789623294, 36.47467682499403452,
    104.54615319102123294, 36.50344472863986311, 104.55909264414623294, 36.51080963749403452,
    104.59278405039623294, 36.52142975468153452, 104.60792076914623294, 36.52899811405653452,
    104.62004642018791856, 36.54254791874403452, 104.63135826914623294, 36.56415436405653452,
    104.63892662852123294, 36.57164134322319882, 104.65276126393791856, 36.57916901249403452,
    104.68482506602123294, 36.58864980676486311, 104.69491621185454733, 36.59296295780653452,
    104.71037845143791856, 36.60602448124403452, 104.71379642018791856, 36.62079498905653452,
    104.70923912852123294, 36.66461823124403452, 104.71143639414623294, 36.66974518436903452,
    104.71607506602123294, 36.67483144738986311, 104.72071373789623294, 36.68170807499403452,
    104.72299238372954733, 36.69224681197319882, 104.71916751393791856, 36.69733307499403452,
    104.69190514414623294, 36.71845123905653452, 104.68409264414623294, 36.72162506718153452,
    104.68124433685454733, 36.72333405155653452, 104.67717532643791856, 36.72846100468153452,
    104.67253665456291856, 36.73904043176486311, 104.66822350352123294, 36.74376048384819882,
    104.61280358164623294, 36.77256907759819882, 104.60474694102123294, 36.79218170780653452,
    104.60002688893791856, 36.81504954634819882, 104.59245852956291856, 36.83311595259819882,
    104.57911217539623294, 36.83917877811903452, 104.56080162852123294, 36.84157949426486311,
    104.54436282643791856, 36.84723541874403452, 104.53736412852123294, 36.86330800988986311,
    104.52784264414623294, 36.87193431197319882, 104.48202558685454733, 36.88430410363986311,
    104.46623782643791856, 36.89089590051486311, 104.44752037852123294, 36.89488353072319882,
    104.40870201914623294, 36.88202545780653452, 104.40137780039623294, 36.89089590051486311,
    104.39307701914623294, 36.90582916874403452, 104.37395267018791856, 36.90774160363986311,
    104.33594811289623294, 36.90082428593153452, 104.33000735768791856, 36.90985748905653452,
    104.32870527435454733, 36.93052806197319882, 104.33749433685454733, 37.00678131718153452,
    104.33212324310454733, 37.01984284061903452, 104.30518639414623294, 37.05593496301486311,
    104.29843183685454733, 37.06724681197319882, 104.29859459727123294, 37.07965729374403452,
    104.30518639414623294, 37.10004303593153452, 104.31714928477123294, 37.12262604374403452,
    104.32829837331291856, 37.12779368697319882, 104.34172610768791856, 37.12779368697319882,
    104.35979251393791856, 37.13483307499403452, 104.35132897227123294, 37.14142487186903452,
    104.33879642018791856, 37.14789459843153452, 104.32561282643791856, 37.15269603072319882,
    104.30591881602123294, 37.15700918176486311, 104.30616295664623294, 37.16229889530653452,
    104.31104576914623294, 37.16823965051486311, 104.31544030039623294, 37.17239004113986311,
    104.32398522227123294, 37.17654043176486311, 104.33228600352123294, 37.17442454634819882,
    104.33977298268791856, 37.17047760624403452, 104.34612063893791856, 37.16897207238986311,
    104.40674889414623294, 37.19468821822319882, 104.43897545664623294, 37.20343659061903452,
    104.46965579518791856, 37.19566478072319882, 104.49268639414623294, 37.20856354374403452,
    104.57821699310454733, 37.32408274947319882, 104.60311933685454733, 37.33966705936903452,
    104.62720787852123294, 37.34369537968153452, 104.67896569102123294, 37.34410228072319882,
    104.69621829518791856, 37.34943268436903452, 104.72828209727123294, 37.36603424686903452,
    104.74561608164623294, 37.37112050988986311, 104.80095462331291856, 37.37169017134819882,
    104.81902102956291856, 37.37787506718153452, 104.83253014414623294, 37.38821035363986311,
    104.84343509206291856, 37.39911530155653452, 104.85694420664623294, 37.40782298384819882,
    104.87745201914623294, 37.41140371301486311, 104.91936282643791856, 37.40782298384819882,
    104.93873131602123294, 37.40851471561903452, 104.95696048268791856, 37.41539134322319882,
    104.99260501393791856, 37.44245026249403452, 104.99716230560454733, 37.45266347863986311,
    104.99553470143791856, 37.46332428593153452, 104.99366295664623294, 37.46950918176486311,
    104.99732506602123294, 37.47325267134819882, 105.01148522227123294, 37.47683340051486311,
    105.02011152435454733, 37.47374095259819882, 105.04151451914623294, 37.45685455936903452,
    105.05241946706291856, 37.45355866093153452, 105.06324303477123294, 37.45709869999403452,
    105.08545983164623294, 37.46999746301486311, 105.09620201914623294, 37.47272369999403452,
    105.10206139414623294, 37.47496165572319882, 105.11109459727123294, 37.48480866093153452,
    105.11670983164623294, 37.48700592655653452, 105.12297610768791856, 37.48590729374403452,
    105.13225345143791856, 37.48126862186903452, 105.13721764414623294, 37.48016998905653452,
    105.15097089935454733, 37.48126862186903452, 105.17489668060454733, 37.48590729374403452,
    105.18848717539623294, 37.48700592655653452, 105.19817142018791856, 37.48586660363986311,
    105.21835371185454733, 37.48078034061903452, 105.22999108164623294, 37.48016998905653452,
    105.24325605560454733, 37.48309967655653452, 105.26181074310454733, 37.49245840051486311,
    105.27466881602123294, 37.49445221561903452, 105.54786217539623294, 37.49762604374403452,
    105.57325280039623294, 37.50067780155653452, 105.62794030039623294, 37.52452220259819882,
    105.69393964935454733, 37.53558991093153452, 105.78419030039623294, 37.56887441613986311,
    105.85792076914623294, 37.57872142134819882, 105.87606855560454733, 37.58604564009819882,
    105.88908938893791856, 37.59882233280653452, 105.91797936289623294, 37.64227936405653452,
    105.94044030039623294, 37.68801504113986311, 105.94328860768791856, 37.69586823124403452,
    105.94605553477123294, 37.71242910363986311, 105.95899498789623294, 37.75303782759819882,
    105.96314537852123294, 37.77444082238986311, 105.96184329518791856, 37.80597565311903452,
    105.96314537852123294, 37.81598541874403452, 105.97559655039623294, 37.84247467655653452,
    105.97730553477123294, 37.85419342655653452, 105.99268639414623294, 37.88503652551486311,
    106.02906334727123294, 37.92222728072319882, 106.07105553477123294, 37.95364004113986311,
    106.10401451914623294, 37.96686432499403452, 106.12151126393791856, 37.97146230676486311,
    106.13957767018791856, 37.98253001509819882, 106.16920006602123294, 38.00775787968153452,
    106.18213951914623294, 38.02277252811903452, 106.20272871185454733, 38.05829498905653452,
    106.22063235768791856, 38.08230215051486311, 106.22356204518791856, 38.09182363488986311,
    106.22372480560454733, 38.11395905155653452, 106.22632897227123294, 38.12746816613986311,
    106.23235110768791856, 38.13690827030653452, 106.23918704518791856, 38.14439524947319882,
    106.24415123789623294, 38.15180084843153452, 106.24699954518791856, 38.16058991093153452,
    106.24846438893791856, 38.17597077030653452, 106.25041751393791856, 38.18589915572319882,
    106.27092532643791856, 38.22382233280653452, 106.27808678477123294, 38.23326243697319882,
    106.40796959727123294, 38.36225006718153452, 106.49878990977123294, 38.41172923384819882,
    106.51701907643791856, 38.42686595259819882, 106.53923587331291856, 38.47463613488986311,
    106.56470787852123294, 38.50433991093153452, 106.57276451914623294, 38.52655670780653452,
    106.53565514414623294, 38.53534577030653452, 106.52100670664623294, 38.56439850468153452,
    106.51856530039623294, 38.57326894738986311, 106.52076256602123294, 38.58441803593153452,
    106.54045657643791856, 38.62144603072319882, 106.59424889414623294, 38.66632721561903452,
    106.58204186289623294, 38.68093496301486311, 106.57569420664623294, 38.69025299686903452,
    106.57341556081291856, 38.69867584843153452, 106.57317142018791856, 38.71039459843153452,
    106.57715905039623294, 38.71295807499403452, 106.59693444102123294, 38.71959056197319882,
    106.60409589935454733, 38.72097402551486311, 106.60751386810454733, 38.72479889530653452,
    106.60849043060454733, 38.73375071822319882, 106.60792076914623294, 38.75165436405653452,
    106.64893639414623294, 38.80353424686903452, 106.66553795664623294, 38.82025787968153452,
    106.71778405039623294, 38.85818105676486311, 106.84034264414623294, 38.98761627811903452,
    106.85938561289623294, 39.01361725468153452, 106.86622155039623294, 39.04315827030653452,
    106.85377037852123294, 39.07721588749403452, 106.88160240977123294, 39.10455963749403452,
    106.84815514414623294, 39.16388580936903452, 106.83668053477123294, 39.17279694218153452,
    106.81959069102123294, 39.18048737186903452, 106.80803470143791856, 39.19891998905653452,
    106.80128014414623294, 39.22162506718153452, 106.79908287852123294, 39.24172597863986311,
    106.80909264414623294, 39.30927155155653452, 106.80241946706291856, 39.32709381718153452,
    106.79778079518791856, 39.33490631718153452, 106.79346764414623294, 39.35289134322319882,
    106.78882897227123294, 39.36090729374403452, 106.78036543060454733, 39.36835358280653452,
    106.76514733164623294, 39.37836334843153452, 106.75879967539623294, 39.38507721561903452,
    106.74992923268791856, 39.41880931197319882, 106.75245201914623294, 39.46552155155653452,
    106.77531985768791856, 39.55927155155653452, 106.77857506602123294, 39.58433665572319882,
    106.77833092539623294, 39.59548574426486311, 106.77352949310454733, 39.61656321822319882,
    106.77247155039623294, 39.62872955936903452, 106.77654056081291856, 39.65245189009819882,
    106.78362063893791856, 39.66433340051486311, 106.78508548268791856, 39.67462799686903452,
    106.77247155039623294, 39.69354889530653452, 106.76734459727123294, 39.69599030155653452,
    106.76050865977123294, 39.69688548384819882, 106.75448652435454733, 39.69879791874403452,
    106.75196373789623294, 39.70416901249403452, 106.75294030039623294, 39.71373118697319882,
    106.75749759206291856, 39.72968170780653452, 106.75879967539623294, 39.73859284061903452,
    106.75993899831291856, 39.74213287968153452, 106.76270592539623294, 39.74717845259819882,
    106.76514733164623294, 39.75393300988986311, 106.76563561289623294, 39.76251862186903452,
    106.76319420664623294, 39.77232493697319882, 106.75489342539623294, 39.78835683801486311,
    106.75196373789623294, 39.79665761926486311, 106.74455813893791856, 39.85492584843153452,
    106.73975670664623294, 39.86273834843153452, 106.71737714935454733, 39.88617584843153452,
    106.71037845143791856, 39.89899323124403452, 106.73829186289623294, 39.91950104374403452,
    106.73845462331291856, 39.92975494999403452, 106.73365319102123294, 39.93891022343153452,
    106.73023522227123294, 39.94778066613986311, 106.73462975352123294, 39.95734284061903452,
    106.73804772227123294, 39.97150299686903452, 106.73300214935454733, 39.99030182499403452,
    106.72453860768791856, 40.00739166874403452, 106.71778405039623294, 40.01630280155653452,
    106.70907636810454733, 40.03811269738986311, 106.72714277435454733, 40.06260814009819882,
    106.85865319102123294, 40.17975494999403452, 106.87012780039623294, 40.18362050988986311,
    106.87427819102123294, 40.16657135624403452, 106.97730553477123294, 40.24164459843153452,
    106.99903405039623294, 40.25108470259819882, 107.00709069102123294, 40.25714752811903452,
    107.01474043060454733, 40.26931386926486311, 107.02588951914623294, 40.30267975468153452,
    107.03183027435454733, 40.31049225468153452, 107.05787194102123294, 40.33279043176486311,
    107.06600996185454733, 40.33783600468153452, 107.08098392018791856, 40.34174225468153452,
    107.11386152435454733, 40.34556712447319882, 107.12802168060454733, 40.35150787968153452,
    107.15154056081291856, 40.37604401249403452, 107.15870201914623294, 40.40204498905653452,
    107.15406334727123294, 40.42833079634819882, 107.14169355560454733, 40.45392487186903452,
    107.14722740977123294, 40.46222565311903452, 107.16325931081291856, 40.47724030155653452,
    107.16968834727123294, 40.48187897343153452, 107.17986087331291856, 40.48639557499403452,
    107.20573977956291856, 40.49441152551486311, 107.21436608164623294, 40.49921295780653452,
    107.22022545664623294, 40.51239655155653452, 107.20517011810454733, 40.54022858280653452,
    107.20386803477123294, 40.56378815311903452, 107.21184329518791856, 40.58270905155653452,
    107.22486412852123294, 40.59516022343153452, 107.25530032643791856, 40.61558665572319882,
    107.26295006602123294, 40.62543366093153452, 107.26612389414623294, 40.63292064009819882,
    107.27076256602123294, 40.63780345259819882, 107.28264407643791856, 40.63947174686903452,
    107.30982506602123294, 40.64008209843153452, 107.32219485768791856, 40.64227936405653452,
    107.34856204518791856, 40.65245189009819882, 107.38005618581291856, 40.65668366093153452,
    107.39503014414623294, 40.66058991093153452, 107.44035892018791856, 40.70160553593153452,
    107.46656334727123294, 40.70990631718153452, 107.49024498789623294, 40.72662994999403452,
    107.51392662852123294, 40.73920319218153452, 107.53907311289623294, 40.73505280155653452,
    107.56145267018791856, 40.73725006718153452, 107.57398522227123294, 40.74685293176486311,
    107.58350670664623294, 40.75743235884819882, 107.64397220143791856, 40.77476634322319882,
    107.66309655039623294, 40.77606842655653452, 107.69459069102123294, 40.76886627811903452,
    107.70663496185454733, 40.77142975468153452, 107.71143639414623294, 40.78693268436903452,
    107.70671634206291856, 40.80158112186903452, 107.69963626393791856, 40.81659577030653452,
    107.69979902435454733, 40.82977936405653452, 107.71770267018791856, 40.83873118697319882,
    107.73243248789623294, 40.85126373905653452, 107.75839277435454733, 40.85948314009819882,
    107.86207115977123294, 40.86989980676486311, 107.88518313893791856, 40.86749909061903452,
    107.90552819102123294, 40.85516998905653452, 107.91627037852123294, 40.85016510624403452,
    107.92831464935454733, 40.85175202030653452, 107.95045006602123294, 40.85858795780653452,
    108.01197350352123294, 40.85858795780653452, 108.08668053477123294, 40.84772369999403452,
    108.11500084727123294, 40.85175202030653452, 108.14210045664623294, 40.87055084843153452,
    108.15674889414623294, 40.87714264530653452, 108.17326907643791856, 40.87970612186903452,
    108.17880293060454733, 40.87457916874403452, 108.19752037852123294, 40.83873118697319882,
    108.20875084727123294, 40.82790761926486311, 108.22421308685454733, 40.81793854374403452,
    108.24081464935454733, 40.81391022343153452, 108.26889082122954733, 40.82668691613986311,
    108.28370201914623294, 40.82294342655653452, 108.30673261810454733, 40.81081777551486311,
    108.33781985768791856, 40.81016673384819882, 108.40479576914623294, 40.82058340051486311,
    108.43775475352123294, 40.81700267134819882, 108.45175214935454733, 40.80853912968153452,
    108.46729576914623294, 40.79332103072319882, 108.47974694102123294, 40.77545807499403452,
    108.48487389414623294, 40.75897858280653452, 108.49260501393791856, 40.75250885624403452,
    108.52824954518791856, 40.73977285363986311, 108.55087324310454733, 40.71796295780653452,
    108.57626386810454733, 40.68272532759819882, 108.58106530039623294, 40.67023346561903452,
    108.59083092539623294, 40.66156647343153452, 108.61288496185454733, 40.65412018436903452,
    108.67042076914623294, 40.64264557499403452, 108.68026777435454733, 40.63263580936903452,
    108.68759199310454733, 40.62103912968153452, 108.69719485768791856, 40.61216868697319882,
    108.71395918060454733, 40.60993073124403452, 108.74805748789623294, 40.61798737186903452,
    108.76612389414623294, 40.61216868697319882, 108.75709069102123294, 40.58808014530653452,
    108.76360110768791856, 40.57318756718153452, 108.77979576914623294, 40.56403229374403452,
    108.81128990977123294, 40.55186595259819882, 108.82032311289623294, 40.54673899947319882,
    108.83000735768791856, 40.54328034061903452, 108.84237714935454733, 40.54328034061903452,
    108.84839928477123294, 40.54694244999403452, 108.85645592539623294, 40.55390045780653452,
    108.86670983164623294, 40.56073639530653452, 108.87940514414623294, 40.56378815311903452,
    108.88323001393791856, 40.55841705936903452, 108.88664798268791856, 40.54669830936903452,
    108.89161217539623294, 40.53497955936903452, 108.90040123789623294, 40.52960846561903452,
    108.91309655039623294, 40.53168366093153452, 108.92139733164623294, 40.53469472863986311,
    108.92945397227123294, 40.53436920780653452, 108.94100996185454733, 40.52623118697319882,
    108.95069420664623294, 40.52216217655653452, 108.96379642018791856, 40.52134837447319882,
    108.98926842539623294, 40.52277252811903452, 109.00025475352123294, 40.52631256718153452,
    109.00440514414623294, 40.53497955936903452, 109.00562584727123294, 40.55695221561903452,
    109.01539147227123294, 40.54743073124403452, 109.02588951914623294, 40.54519277551486311,
    109.03655032643791856, 40.54885488488986311, 109.04664147227123294, 40.55695221561903452,
    109.04639733164623294, 40.54063548384819882, 109.04989668060454733, 40.52867259322319882,
    109.05787194102123294, 40.52501048384819882, 109.08733157643791856, 40.54665761926486311,
    109.09815514414623294, 40.54722728072319882, 109.12232506602123294, 40.52960846561903452,
    109.15007571706291856, 40.52370840051486311, 109.18506920664623294, 40.52069733280653452,
    109.20728600352123294, 40.51190827030653452, 109.19752037852123294, 40.48871491093153452,
    109.22234134206291856, 40.49205149947319882, 109.28671308685454733, 40.52277252811903452,
    109.27955162852123294, 40.49042389530653452, 109.28711998789623294, 40.48822662968153452,
    109.33513431081291856, 40.48733144738986311, 109.35547936289623294, 40.48342519738986311,
    109.39657636810454733, 40.46820709843153452, 109.41309655039623294, 40.47736237186903452,
    109.45948326914623294, 40.48981354374403452, 109.47234134206291856, 40.50230540572319882,
    109.46070397227123294, 40.50230540572319882, 109.45004316497954733, 40.50450267134819882,
    109.43995201914623294, 40.50885651249403452, 109.43091881602123294, 40.51532623905653452,
    109.44703209727123294, 40.52081940311903452, 109.48251386810454733, 40.52460358280653452,
    109.49903405039623294, 40.52960846561903452, 109.50586998789623294, 40.51670970259819882,
    109.51921634206291856, 40.52265045780653452, 109.53655032643791856, 40.53550853072319882,
    109.55445397227123294, 40.54328034061903452, 109.57284589935454733, 40.54226308801486311,
    109.58204186289623294, 40.53912994999403452, 109.59083092539623294, 40.53998444218153452,
    109.60832767018791856, 40.55072662968153452, 109.66513105560454733, 40.49677155155653452,
    109.69044030039623294, 40.48468659061903452, 109.70085696706291856, 40.47760651249403452,
    109.71338951914623294, 40.47528717655653452, 109.73764082122954733, 40.49245840051486311,
    109.74903405039623294, 40.49782949426486311, 109.76229902435454733, 40.50116608280653452,
    109.78956139414623294, 40.50401439009819882, 109.82805423268791856, 40.51532623905653452,
    109.88331139414623294, 40.51532623905653452, 109.89250735768791856, 40.51996491093153452,
    109.90137780039623294, 40.52265045780653452, 109.91065514414623294, 40.52277252811903452,
    109.91602623789623294, 40.52029043176486311, 109.92424563893791856, 40.51239655155653452,
    109.93116295664623294, 40.50914134322319882, 109.94149824310454733, 40.50588613488986311,
    109.95289147227123294, 40.50397369999403452, 109.96363365977123294, 40.50812409061903452,
    109.97209720143791856, 40.52277252811903452, 109.98829186289623294, 40.51158274947319882,
    109.99960371185454733, 40.51280345259819882, 110.01018313893791856, 40.51906972863986311,
    110.02393639414623294, 40.52277252811903452, 110.03964277435454733, 40.52057526249403452,
    110.05477949310454733, 40.51670970259819882, 110.06902102956291856, 40.51597728072319882,
    110.08253014414623294, 40.52277252811903452, 110.09522545664623294, 40.51068756718153452,
    110.10230553477123294, 40.50539785363986311, 110.10987389414623294, 40.50230540572319882,
    110.12281334727123294, 40.50287506718153452, 110.14258873789623294, 40.51288483280653452,
    110.15333092539623294, 40.51532623905653452, 110.15430748789623294, 40.51890696822319882,
    110.16757246185454733, 40.54022858280653452, 110.17514082122954733, 40.54157135624403452,
    110.20256595143791856, 40.53961823124403452, 110.21168053477123294, 40.53705475468153452,
    110.22315514414623294, 40.52277252811903452, 110.23780358164623294, 40.48273346561903452,
    110.24610436289623294, 40.47443268436903452, 110.28638756602123294, 40.48391347863986311,
    110.30364017018791856, 40.48139069218153452, 110.29354902435454733, 40.46137116093153452,
    110.30665123789623294, 40.45319244999403452, 110.34709720143791856, 40.45315175988986311,
    110.35572350352123294, 40.45079173384819882, 110.36207115977123294, 40.44419993697319882,
    110.40341230560454733, 40.42043691613986311, 110.41920006602123294, 40.39838287968153452,
    110.43026777435454733, 40.38849518436903452, 110.44141686289623294, 40.38934967655653452,
    110.45639082122954733, 40.39708079634819882, 110.46737714935454733, 40.39459869999403452,
    110.47502688893791856, 40.38507721561903452, 110.47909589935454733, 40.37201569218153452,
    110.48650149831291856, 40.37201569218153452, 110.50684655039623294, 40.38137441613986311,
    110.54297936289623294, 40.36054108280653452, 110.58073977956291856, 40.33185455936903452,
    110.60604902435454733, 40.31740957238986311, 110.63819420664623294, 40.32212962447319882,
    110.65813235768791856, 40.32180410363986311, 110.68848717539623294, 40.30573151249403452,
    110.72730553477123294, 40.29673899947319882, 110.74610436289623294, 40.29006582238986311,
    110.76587975352123294, 40.28034088749403452, 110.77979576914623294, 40.27606842655653452,
    110.81495201914623294, 40.27643463749403452, 110.83440188893791856, 40.27370840051486311,
    110.86483808685454733, 40.25881582238986311, 110.87989342539623294, 40.25531647343153452,
    110.94133548268791856, 40.25328196822319882, 110.96485436289623294, 40.26264069218153452,
    110.98739668060454733, 40.26166412968153452, 110.99659264414623294, 40.26589590051486311,
    111.01490319102123294, 40.28339264530653452, 111.03467858164623294, 40.29690175988986311,
    111.06104576914623294, 40.26719798384819882, 111.18482506602123294, 40.20811595259819882,
    111.20956464935454733, 40.18044668176486311, 111.21973717539623294, 40.17271556197319882,
    111.23129316497954733, 40.16897207238986311, 111.25497480560454733, 40.16677480676486311,
    111.27719160247954733, 40.16075267134819882, 111.29070071706291856, 40.15904368697319882,
    111.30274498789623294, 40.15607330936903452, 111.32829837331291856, 40.11871979374403452,
    111.33838951914623294, 40.11066315311903452, 111.36133873789623294, 40.09796784061903452,
    111.36988365977123294, 40.09084707238986311, 111.37989342539623294, 40.06244537968153452,
    111.38078860768791856, 40.05731842655653452, 111.39739017018791856, 40.04791901249403452,
    111.40536543060454733, 40.02582428593153452, 111.41138756602123294, 39.98155345259819882,
    111.43653405039623294, 39.91730377811903452, 111.43531334727123294, 39.90643952030653452,
    111.42343183685454733, 39.89594147343153452, 111.39722740977123294, 39.83075592655653452,
    111.36524498789623294, 39.77753327030653452, 111.35946699310454733, 39.75043366093153452,
    111.36988365977123294, 39.72150299686903452, 111.41757246185454733, 39.68366119999403452,
    111.42009524831291856, 39.67243073124403452, 111.42676842539623294, 39.65888092655653452,
    111.43563886810454733, 39.64659251509819882, 111.44556725352123294, 39.63898346561903452,
    111.46314537852123294, 39.62018463749403452, 111.45362389414623294, 39.61725494999403452,
    111.42514082122954733, 39.62531159061903452, 111.42424563893791856, 39.61721425988986311,
    111.42514082122954733, 39.53595612186903452, 111.42090905039623294, 39.52204010624403452,
    111.37248782643791856, 39.48908112186903452, 111.34237714935454733, 39.44436269738986311,
    111.31698652435454733, 39.42938873905653452, 111.28410892018791856, 39.42487213749403452,
    111.19914798268791856, 39.42706940311903452, 111.16399173268791856, 39.42316315311903452,
    111.13428795664623294, 39.41050853072319882, 111.10987389414623294, 39.38507721561903452,
    111.11817467539623294, 39.37604401249403452, 111.14120527435454733, 39.36725494999403452,
    111.15080813893791856, 39.35712311405653452, 111.16684003997954733, 39.36469147343153452,
    111.17457115977123294, 39.35675690311903452, 111.17920983164623294, 39.34235260624403452,
    111.18482506602123294, 39.33043040572319882, 111.20020592539623294, 39.32090892134819882,
    111.21477298268791856, 39.31635162968153452, 111.22461998789623294, 39.31049225468153452,
    111.22657311289623294, 39.29694244999403452, 111.22160892018791856, 39.28339264530653452,
    111.15821373789623294, 39.17279694218153452, 111.13420657643791856, 39.09943268436903452,
    111.11768639414623294, 39.06732819218153452, 111.08863365977123294, 39.04250722863986311,
    111.02190188893791856, 39.01858144738986311, 110.99463951914623294, 39.00377024947319882,
    110.97258548268791856, 38.97418854374403452, 110.99415123789623294, 38.93720123905653452,
    111.00350996185454733, 38.91608307499403452, 111.00342858164623294, 38.90253327030653452,
    110.99415123789623294, 38.88528066613986311, 110.97999108164623294, 38.81651439009819882,
    110.96900475352123294, 38.79885488488986311, 110.95753014414623294, 38.78526439009819882,
    110.94833418060454733, 38.77110423384819882, 110.94280032643791856, 38.73936595259819882,
    110.93751061289623294, 38.73155345259819882, 110.92139733164623294, 38.71723053593153452,
    110.91504967539623294, 38.70844147343153452, 110.90430748789623294, 38.67938873905653452,
    110.87151126393791856, 38.62128327030653452, 110.87322024831291856, 38.61168040572319882,
    110.87525475352123294, 38.60748932499403452, 110.90064537852123294, 38.58038971561903452,
    110.90129642018791856, 38.57367584843153452, 110.89901777435454733, 38.54535553593153452,
    110.89698326914623294, 38.53534577030653452, 110.89071699310454733, 38.52509186405653452,
    110.87647545664623294, 38.50995514530653452, 110.87012780039623294, 38.50181712447319882,
    110.86532636810454733, 38.49030182499403452, 110.85946699310454733, 38.46796295780653452,
    110.85254967539623294, 38.45709869999403452, 110.83375084727123294, 38.44582754113986311,
    110.79208418060454733, 38.43956126509819882, 110.77401777435454733, 38.42609284061903452,
    110.75562584727123294, 38.39040761926486311, 110.74610436289623294, 38.37771230676486311,
    110.71119225352123294, 38.35679759322319882, 110.70508873789623294, 38.35443756718153452,
    110.70069420664623294, 38.34674713749403452, 110.67099043060454733, 38.31692129113986311,
    110.65967858164623294, 38.31000397343153452, 110.64747155039623294, 38.30560944218153452,
    110.63428795664623294, 38.30329010624403452, 110.60434003997954733, 38.30137767134819882,
    110.58985436289623294, 38.29669830936903452, 110.57927493581291856, 38.28721751509819882,
    110.56861412852123294, 38.23220449426486311, 110.56861412852123294, 38.22687409061903452,
    110.55925540456291856, 38.21690501509819882, 110.54712975352123294, 38.21088287968153452,
    110.53280683685454733, 38.20787181197319882, 110.51701907643791856, 38.20705800988986311,
    110.51091556081291856, 38.20209381718153452, 110.49960371185454733, 38.16604238488986311,
    110.50928795664623294, 38.14496491093153452, 110.50904381602123294, 38.12803782759819882,
    110.49960371185454733, 38.09035879113986311, 110.50709069102123294, 38.01154205936903452,
    110.50782311289623294, 38.00865306197319882, 110.51164798268791856, 38.00507233280653452,
    110.51327558685454733, 38.00100332238986311, 110.51360110768791856, 37.98253001509819882,
    110.51327558685454733, 37.98049550988986311, 110.51417076914623294, 37.97016022343153452,
    110.51295006602123294, 37.96124909061903452, 110.51490319102123294, 37.95490143436903452,
    110.52409915456291856, 37.95250071822319882, 110.54509524831291856, 37.94965241093153452,
    110.56251061289623294, 37.94200267134819882, 110.57691490977123294, 37.93126048384819882,
    110.58895918060454733, 37.91901276249403452, 110.65308678477123294, 37.81309642134819882,
    110.67440839935454733, 37.79616933801486311, 110.72893313893791856, 37.77354564009819882,
    110.74268639414623294, 37.76475657759819882, 110.75285892018791856, 37.74567291874403452,
    110.74374433685454733, 37.73244863488986311, 110.70508873789623294, 37.71356842655653452,
    110.72706139414623294, 37.70026276249403452, 110.77890058685454733, 37.67829010624403452,
    110.79444420664623294, 37.65831126509819882, 110.78768964935454733, 37.65241119999403452,
    110.77043704518791856, 37.64118073124403452, 110.76661217539623294, 37.63788483280653452,
    110.76604251393791856, 37.62396881718153452, 110.77100670664623294, 37.61334869999403452,
    110.77719160247954733, 37.60386790572319882, 110.78671308685454733, 37.57351308801486311,
    110.78858483164623294, 37.56134674686903452, 110.78370201914623294, 37.55593496301486311,
    110.77580813893791856, 37.55186595259819882, 110.76946048268791856, 37.54234446822319882,
    110.74610436289623294, 37.48016998905653452, 110.73845462331291856, 37.46942780155653452,
    110.71534264414623294, 37.44977448124403452, 110.69621829518791856, 37.43939850468153452,
    110.65333092539623294, 37.43390534061903452, 110.63672936289623294, 37.41815827030653452,
    110.63005618581291856, 37.39655182499403452, 110.63331139414623294, 37.37921784061903452,
    110.64625084727123294, 37.36770254113986311, 110.66789798268791856, 37.36355215051486311,
    110.68710371185454733, 37.35712311405653452, 110.68751061289623294, 37.34210846561903452,
    110.67099043060454733, 37.30951569218153452, 110.66936282643791856, 37.30109284061903452,
    110.67001386810454733, 37.29572174686903452, 110.66968834727123294, 37.29039134322319882,
    110.66480553477123294, 37.28217194218153452, 110.66081790456291856, 37.28030019738986311,
    110.64763431081291856, 37.27663808801486311, 110.64356530039623294, 37.27472565311903452,
    110.64087975352123294, 37.26963939009819882, 110.63672936289623294, 37.25637441613986311,
    110.63371829518791856, 37.25084056197319882, 110.56861412852123294, 37.17853424686903452,
    110.53679446706291856, 37.15538157759819882, 110.52759850352123294, 37.14439524947319882,
    110.51636803477123294, 37.11501699426486311, 110.50871829518791856, 37.10158925988986311,
    110.49618574310454733, 37.09597402551486311, 110.48926842539623294, 37.09182363488986311,
    110.47893313893791856, 37.08165110884819882, 110.46949303477123294, 37.06940338749403452,
    110.46558678477123294, 37.05878327030653452, 110.45989017018791856, 37.05113353072319882,
    110.44638105560454733, 37.04425690311903452, 110.41773522227123294, 37.03518300988986311,
    110.41773522227123294, 37.02769603072319882, 110.42538496185454733, 37.02704498905653452,
    110.44507897227123294, 37.02151113488986311, 110.42725670664623294, 37.01019928593153452,
    110.40796959727123294, 37.01475657759819882, 110.38917076914623294, 37.02183665572319882,
    110.37305748789623294, 37.01776764530653452, 110.37281334727123294, 37.00726959843153452,
    110.38379967539623294, 36.99498118697319882, 110.39787845143791856, 36.98480866093153452,
    110.40715579518791856, 36.98053619999403452, 110.41797936289623294, 36.96723053593153452,
    110.41252688893791856, 36.93752675988986311, 110.39478600352123294, 36.90692780155653452,
    110.36923261810454733, 36.89118073124403452, 110.37671959727123294, 36.87848541874403452,
    110.40080813893791856, 36.86111074426486311, 110.41081790456291856, 36.84955475468153452,
    110.40381920664623294, 36.83726634322319882, 110.40723717539623294, 36.82994212447319882,
    110.41423587331291856, 36.82400136926486311, 110.41773522227123294, 36.81602610884819882,
    110.41496829518791856, 36.80512116093153452, 110.40910892018791856, 36.79576243697319882,
    110.39722740977123294, 36.78131744999403452, 110.39307701914623294, 36.78070709843153452,
    110.38575280039623294, 36.78103261926486311, 110.37899824310454733, 36.77989329634819882,
    110.37671959727123294, 36.77509186405653452, 110.37989342539623294, 36.76996491093153452,
    110.39210045664623294, 36.76500071822319882, 110.39722740977123294, 36.76080963749403452,
    110.39991295664623294, 36.76052480676486311, 110.41773522227123294, 36.75397369999403452,
    110.41220136810454733, 36.75368886926486311, 110.41309655039623294, 36.74530670780653452,
    110.41610761810454733, 36.73615143436903452, 110.41773522227123294, 36.73350657759819882,
    110.43905683685454733, 36.74225494999403452, 110.44100996185454733, 36.72642649947319882,
    110.43205813893791856, 36.70396556197319882, 110.42115319102123294, 36.69257233280653452,
    110.41057376393791856, 36.69501373905653452, 110.40048261810454733, 36.69883860884819882,
    110.39283287852123294, 36.69855377811903452, 110.38974043060454733, 36.68878815311903452,
    110.39356530039623294, 36.68231842655653452, 110.48292076914623294, 36.59288157759819882,
    110.48975670664623294, 36.57733795780653452, 110.49854576914623294, 36.52448151249403452,
    110.49960371185454733, 36.50759511926486311, 110.49594160247954733, 36.48399485884819882,
    110.48218834727123294, 36.44855377811903452, 110.47632897227123294, 36.40485260624403452,
    110.45875084727123294, 36.34931061405653452, 110.45907636810454733, 36.32709381718153452,
    110.47014407643791856, 36.28615957238986311, 110.47299238372954733, 36.26369863488986311,
    110.46900475352123294, 36.24323151249403452, 110.45061282643791856, 36.20685455936903452,
    110.44507897227123294, 36.18541087447319882, 110.44686933685454733, 36.14179108280653452,
    110.49748782643791856, 35.97960032759819882, 110.50066165456291856, 35.93313222863986311,
    110.50595136810454733, 35.91197337447319882, 110.50709069102123294, 35.90082428593153452,
    110.51075280039623294, 35.88678619999403452, 110.51954186289623294, 35.88507721561903452,
    110.53069095143791856, 35.88703034061903452, 110.54127037852123294, 35.88381582238986311,
    110.56015058685454733, 35.84914785363986311, 110.57545006602123294, 35.71246979374403452,
    110.61133873789623294, 35.63772207238986311, 110.61963951914623294, 35.59719472863986311,
    110.60263105560454733, 35.55422597863986311, 110.57276451914623294, 35.52794017134819882,
    110.55591881602123294, 35.50849030155653452, 110.55844160247954733, 35.49957916874403452,
    110.56275475352123294, 35.49074941613986311, 110.55925540456291856, 35.47081126509819882,
    110.55274498789623294, 35.44989655155653452, 110.54745527435454733, 35.43817780155653452,
    110.53752688893791856, 35.42776113488986311, 110.52344811289623294, 35.41657135624403452,
    110.50879967539623294, 35.40766022343153452, 110.49618574310454733, 35.40399811405653452,
    110.46827233164623294, 35.35602448124403452, 110.45875084727123294, 35.34312571822319882,
    110.44483483164623294, 35.33502838749403452, 110.43710371185454733, 35.32233307499403452,
    110.43165123789623294, 35.30780670780653452, 110.42457115977123294, 35.29413483280653452,
    110.41724694102123294, 35.28640371301486311, 110.37256920664623294, 35.25531647343153452,
    110.35759524831291856, 35.23822662968153452, 110.33513431081291856, 35.19794342655653452,
    110.32618248789623294, 35.14919668176486311, 110.31690514414623294, 35.12567780155653452,
    110.31047610768791856, 35.09088776249403452, 110.29184003997954733, 35.05036041874403452,
    110.28459720143791856, 35.00962962447319882, 110.26864668060454733, 34.95941803593153452,
    110.25936933685454733, 34.94468821822319882, 110.27100670664623294, 34.91343821822319882,
    110.27222740977123294, 34.88145579634819882, 110.26270592539623294, 34.85118235884819882,
    110.24244225352123294, 34.82489655155653452, 110.23723392018791856, 34.80133698124403452,
    110.24162845143791856, 34.71893952030653452, 110.25196373789623294, 34.66315338749403452,
    110.26994876393791856, 34.63727448124403452
  ))

  private val YangtzeRiver4 = new River(Array(
    113.12761477956291856, 29.46275462447319882, 113.14543704518791856, 29.47199127811903452,
    113.16211998789623294, 29.49310944218153452, 113.18604576914623294, 29.53168366093153452,
    113.41553795664623294, 29.76447174686903452, 113.44597415456291856, 29.78005605676487022,
    113.49732506602123294, 29.81940338749403452, 113.51921634206291856, 29.84162018436903452,
    113.59001712331291856, 29.87990957238987022, 113.60702558685454733, 29.89020416874403452,
    113.62997480560454733, 29.90879954634819882, 113.65031985768791856, 29.92938873905653452,
    113.66423587331291856, 29.95461660363987022, 113.67595462331291856, 29.96112702030653452,
    113.68775475352123294, 29.96344635624403452, 113.69312584727123294, 29.95958079634819882,
    113.70362389414623294, 29.93414948124403452, 113.70671634206291856, 29.92890045780653452,
    113.74862714935454733, 29.91998932499403452, 113.79240970143791856, 29.93341705936903452,
    113.83106530039623294, 29.95827871301487022, 113.88965905039623294, 30.01300690311903452,
    113.92335045664623294, 30.03355540572319882, 114.04395592539623294, 30.07920970259819882,
    114.06226647227123294, 30.08893463749403452, 114.06983483164623294, 30.10333893436903452,
    114.06438235768791856, 30.12437571822319882, 114.05087324310454733, 30.13633860884819882,
    114.01514733164623294, 30.15485260624403452, 113.98340905039623294, 30.18516673384819882,
    113.96428470143791856, 30.19794342655653452, 113.94011477956291856, 30.20323314009819882,
    113.91806074310454733, 30.19806549686903452, 113.89527428477123294, 30.18874746301487022,
    113.87444095143791856, 30.18484121301487022, 113.85816490977123294, 30.19582754113987022,
    113.85759524831291856, 30.23106517134819882, 113.88550865977123294, 30.26520416874403452,
    113.92717532643791856, 30.28851959843153452, 113.96802819102123294, 30.29136790572319882,
    113.98340905039623294, 30.28257884322319882, 113.99675540456291856, 30.26862213749403452,
    114.01856530039623294, 30.24050527551487022, 114.03907311289623294, 30.22960032759819882,
    114.05396569102123294, 30.24017975468153452, 114.08602949310454733, 30.31740957238987022,
    114.10661868581291856, 30.35232168176487022, 114.24170983164623294, 30.49742259322319882,
    114.28736412852123294, 30.57599518436903452, 114.30323326914623294, 30.59304433801487022
  ))

  private val YangtzeRiver5 = new River(Array(
    116.19752037852123294, 29.75141022343153452, 116.20753014414623294, 29.77948639530653452
  ))

  private val YangtzeRiver6 = new River(Array(
    119.60637454518786171, 32.19688548384819882, 119.58781985768786171, 32.19245026249403452,
    119.57235761810454733, 32.19175853072319882, 119.55738365977123294, 32.20014069218153452,
    119.54004967539623294, 32.22211334843153452, 119.53036543060454733, 32.23171621301486311,
    119.51734459727123294, 32.23806386926486311, 119.50481204518786171, 32.23952871301486311,
    119.45256595143786171, 32.23346588749403452, 119.38575280039623294, 32.23741282759819882,
    119.36532636810454733, 32.23440175988986311, 119.34620201914623294, 32.22675202030653452,
    119.32447350352123294, 32.21462636926486311, 119.30372155039623294, 32.20616282759819882,
    119.27833092539623294, 32.20128001509819882, 119.25220787852123294, 32.20001862186903452,
    119.22942142018786171, 32.20266347863986311, 119.12924238372954733, 32.22854238488986311,
    119.07870527435454733, 32.23273346561903452, 119.02784264414623294, 32.22129954634819882,
    118.96338951914623294, 32.18777090051486311, 118.92774498789623294, 32.17682526249403452,
    118.90967858164623294, 32.17389557499403452, 118.88347415456286171, 32.17320384322319882,
    118.82040449310454733, 32.18899160363986311, 118.79322350352123294, 32.19281647343153452,
    118.78655032643786171, 32.19269440311903452, 118.78028405039623294, 32.19110748905653452,
    118.77434329518786171, 32.18707916874403452, 118.76905358164623294, 32.18121979374403452,
    118.76465905039623294, 32.17450592655653452, 118.76148522227123294, 32.16803619999403452,
    118.75953209727123294, 32.16290924686903452, 118.75847415456286171, 32.16099681197319882,
    118.74748782643786171, 32.14724355676486311, 118.73406009206286171, 32.12486399947319882,
    118.70639082122954733, 32.09336985884819882, 118.68946373789623294, 32.06244537968153452,
    118.66895592539623294, 32.03949616093153452, 118.65837649831291856, 32.03058502811903452,
    118.64592532643791856, 32.01618073124403452, 118.61483808685454733, 31.99001699426486311,
    118.58635501393791856, 31.94957103072319882, 118.55331464935454733, 31.91628652551486311,
    118.54053795664623294, 31.89244212447319882, 118.53475996185454733, 31.88446686405653452,
    118.50481204518791856, 31.85919830936903452, 118.49862714935454733, 31.85126373905653452,
    118.44800865977123294, 31.75641510624403452, 118.42619876393791856, 31.72447337447319882,
    118.41504967539623294, 31.69977448124403452, 118.40381920664623294, 31.62323639530653452,
    118.37964928477123294, 31.55617910363986311, 118.34799238372954733, 31.50759511926486311,
    118.31600996185454733, 31.47129954634819882, 118.30982506602123294, 31.46006907759819882,
    118.30518639414623294, 31.44879791874403452, 118.30217532643791856, 31.43834056197319882,
    118.30176842539623294, 31.42780182499403452, 118.30486087331291856, 31.41644928593153452,
    118.31544030039623294, 31.40082428593153452, 118.31861412852123294, 31.39496491093153452,
    118.32235761810454733, 31.38316478072319882, 118.32447350352123294, 31.37103912968153452,
    118.32545006602123294, 31.35639069218153452, 118.32447350352123294, 31.33832428593153452,
    118.32129967539623294, 31.32082754113987022, 118.31511477956291856, 31.30805084843153452,
    118.30616295664623294, 31.30040110884819882, 118.29664147227123294, 31.29739004113987022,
    118.22014407643791856, 31.29112376509819882, 118.21005293060454733, 31.28778717655653452,
    118.17383873789623294, 31.26589590051487022, 118.15593509206291856, 31.25914134322319882,
    118.11272220143791856, 31.25250885624403452, 118.06055748789623294, 31.23322174686903452,
    118.04045657643791856, 31.23334381718153452, 118.02784264414623294, 31.24367910363987022,
    118.01417076914623294, 31.23761627811903452, 118.00237063893791856, 31.22325267134819882,
    117.99594160247954733, 31.20860423384819882, 117.99358157643791856, 31.19127024947319882,
    117.99293053477123294, 31.13898346561903452, 117.98869876393791856, 31.12282949426487022,
    117.98039798268791856, 31.10997142134819882, 117.96827233164623294, 31.10028717655653452,
    117.95272871185454733, 31.09357330936903452, 117.93726647227123294, 31.08893463749403452,
    117.92188561289623294, 31.08600494999403452, 117.90715579518791856, 31.08767324426487022,
    117.89454186289623294, 31.09678782759819882, 117.87924238372954733, 31.12225983280653452,
    117.87037194102123294, 31.13332754113987022, 117.85686282643791856, 31.14093659061903452,
    117.83936608164623294, 31.14366282759819882, 117.82276451914623294, 31.14118073124403452,
    117.80754642018791856, 31.13438548384819882, 117.79338626393791856, 31.12445709843153452,
    117.78150475352123294, 31.11395905155653452, 117.77239017018791856, 31.10386790572319882,
    117.76653079518791856, 31.09210846561903452, 117.76327558685454733, 31.07648346561903452,
    117.76368248789623294, 31.05870189009819882, 117.76889082122954733, 31.02374909061903452,
    117.76807701914623294, 31.00665924686903452, 117.74716230560454733, 30.94346751509819882,
    117.73202558685454733, 30.87445709843153452, 117.69727623789623294, 30.80373769738987022,
    117.68742923268791856, 30.79665761926487022, 117.64258873789623294, 30.78949616093153452,
    117.60865319102123294, 30.78013743697319882, 117.59335371185454733, 30.77370840051487022,
    117.55233808685454733, 30.74782949426487022, 117.53711998789623294, 30.74083079634819882,
    117.46607506602123294, 30.72626373905653452, 117.45793704518791856, 30.72223541874403452,
    117.43653405039623294, 30.70636627811903452, 117.42920983164623294, 30.70246002811903452,
    117.42164147227123294, 30.70050690311903452, 117.34587649831291856, 30.69875722863987022,
    117.32667076914623294, 30.69476959843153452, 117.27694746185454733, 30.67336660363987022,
    117.26075280039623294, 30.66962311405653452, 117.25237063893791856, 30.67068105676487022,
    117.23430423268791856, 30.67491282759819882, 117.22600345143791856, 30.67336660363987022,
    117.21989993581291856, 30.66710032759819882, 117.21770267018791856, 30.65847402551487022,
    117.21778405039623294, 30.64895254113987022, 117.21884199310454733, 30.64024485884819882,
    117.22071373789623294, 30.63430410363987022, 117.22567793060454733, 30.62307363488987022,
    117.22754967539623294, 30.61737702030653452, 117.22803795664623294, 30.61176178593153452,
    117.22413170664623294, 30.59723541874403452, 117.22405032643791856, 30.58620840051487022,
    117.23283938893791856, 30.55512116093153452, 117.23129316497954733, 30.54348379113987022,
    117.22567793060454733, 30.53249746301487022, 117.21786543060454733, 30.52252838749403452,
    117.20956464935454733, 30.51402415572319882, 117.18311608164623294, 30.49937571822319882,
    117.15064537852123294, 30.49424876509819882, 117.03329511810454733, 30.49058665572319882,
    117.00749759206291856, 30.48371002811903452, 116.98438561289623294, 30.47174713749403452,
    116.94865970143791856, 30.44342682499403452, 116.93612714935454733, 30.43585846561903452,
    116.92522220143791856, 30.43292877811903452, 116.91325931081291856, 30.43195221561903452,
    116.90186608164623294, 30.42975494999403452, 116.89267011810454733, 30.42328522343153452,
    116.88827558685454733, 30.40261465051487022, 116.89909915456291856, 30.38068268436903452,
    116.91399173268791856, 30.35858795780653452, 116.92139733164623294, 30.33746979374403452,
    116.92001386810454733, 30.32453034061903452, 116.90683027435454733, 30.27069733280653452,
    116.89690188893791856, 30.24420807499403452, 116.82227623789623294, 30.11542389530653452,
    116.80852298268791856, 30.10207754113987022, 116.79053795664623294, 30.09088776249403452,
    116.71607506602123294, 30.05975983280653452, 116.69052168060454733, 30.05390045780653452,
    116.66260826914623294, 30.05276113488987022, 116.65007571706291856, 30.05060455936903452,
    116.63648522227123294, 30.04332103072319882, 116.60507246185454733, 30.01768626509819882,
    116.59376061289623294, 30.01056549686903452, 116.59172610768791856, 30.00568268436903452,
    116.58790123789623294, 30.00197988488987022, 116.57911217539623294, 29.99522532759819882,
    116.57520592539623294, 29.99079010624403452, 116.56714928477123294, 29.97675202030653452,
    116.54883873789623294, 29.95412832238987022, 116.53663170664623294, 29.92320384322319882,
    116.52694746185454733, 29.90668366093153452, 116.51295006602123294, 29.89411041874403452,
    116.49219811289623294, 29.88462962447319882, 116.46656334727123294, 29.88031647343153452,
    116.41423587331291856, 29.88056061405653452, 116.38917076914623294, 29.87600332238987022,
    116.35653730560454733, 29.85826243697319882, 116.29753665456291856, 29.80870189009819882,
    116.26522871185454733, 29.78888580936903452, 116.23593183685454733, 29.78127675988987022,
    116.20655358164623294, 29.78131744999403452, 116.14698326914623294, 29.78868235884819882,
    116.11028079518791856, 29.78644440311903452, 116.07764733164623294, 29.77639394738987022,
    116.01278730560454733, 29.74815501509819882, 115.99878990977123294, 29.74400462447319882,
    115.88868248789623294, 29.73175690311903452, 115.85572350352123294, 29.73847077030653452,
    115.74333743581291856, 29.80878327030653452, 115.73642011810454733, 29.81635162968153452,
    115.72518964935454733, 29.84263743697319882, 115.71819095143791856, 29.85407135624403452,
    115.70484459727123294, 29.86070384322319882, 115.62110436289623294, 29.86237213749403452,
    115.53492272227123294, 29.85455963749403452, 115.50643964935454733, 29.85761139530653452,
    115.46338951914623294, 29.87128327030653452, 115.45004316497954733, 29.87754954634819882,
    115.43751061289623294, 29.88536204634819882, 115.42668704518791856, 29.89533112186903452,
    115.41724694102123294, 29.90936920780653452, 115.39608808685454733, 29.95608144738987022,
    115.38672936289623294, 29.96967194218153452, 115.37590579518791856, 29.98049550988987022,
    115.33847089935454733, 30.00800202030653452, 115.33049563893791856, 30.01817454634819882,
    115.30152428477123294, 30.06740957238987022, 115.28337649831291856, 30.10932037968153452,
    115.27540123789623294, 30.12095774947319882, 115.18995201914623294, 30.20876699426487022,
    115.16456139414623294, 30.22105540572319882, 115.14551842539623294, 30.22235748905653452,
    115.10694420664623294, 30.21954987186903452, 115.08798261810454733, 30.22414785363987022,
    115.07203209727123294, 30.23602936405653452, 115.06356855560454733, 30.25197988488987022,
    115.06145267018791856, 30.27045319218153452, 115.06413821706291856, 30.28986237186903452,
    115.07284589935454733, 30.32424550988987022, 115.07276451914623294, 30.34113190311903452,
    115.06495201914623294, 30.35736725468153452, 115.05298912852123294, 30.36900462447319882,
    114.98739668060454733, 30.40741608280653452, 114.96607506602123294, 30.41351959843153452,
    114.94393964935454733, 30.41604238488987022, 114.88030032643791856, 30.41498444218153452,
    114.86076907643791856, 30.41889069218153452, 114.84270267018791856, 30.42804596561903452,
    114.82927493581291856, 30.44110748905653452, 114.82056725352123294, 30.45713939009819882,
    114.81080162852123294, 30.49319082238987022, 114.80803470143791856, 30.51105377811903452,
    114.80852298268791856, 30.52631256718153452, 114.81405683685454733, 30.53990306197319882,
    114.82658938893791856, 30.55280182499403452, 114.83993574310454733, 30.56378815311903452,
    114.85157311289623294, 30.57693105676487022, 114.85539798268791856, 30.59113190311903452,
    114.84473717539623294, 30.60565827030653452, 114.82667076914623294, 30.61379629113987022,
    114.80608157643791856, 30.61627838749403452, 114.73145592539623294, 30.60944244999403452,
    114.71485436289623294, 30.60541412968153452, 114.68848717539623294, 30.59076569218153452,
    114.67684980560454733, 30.58628978072319882, 114.61304772227123294, 30.57359446822319882,
    114.60084069102123294, 30.57428619999403452, 114.58017011810454733, 30.58165110884819882,
    114.56600996185454733, 30.59332916874403452, 114.50237063893791856, 30.67316315311903452,
    114.48438561289623294, 30.68435293176487022, 114.45753014414623294, 30.69074127811903452,
    114.42912845143791856, 30.69029368697319882, 114.40137780039623294, 30.68390534061903452,
    114.37598717539623294, 30.67267487186903452, 114.35401451914623294, 30.65652090051487022,
    114.31112714935454733, 30.60964590051487022, 114.29639733164623294, 30.58685944218153452
  ))

  private val rivers = Array(YellowRiver1, YellowRiver2, YellowRiver3,
                             YangtzeRiver4, YangtzeRiver5, YangtzeRiver6)
}
