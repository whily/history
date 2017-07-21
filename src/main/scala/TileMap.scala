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

  private def drawPointLabel(screenZoomLevel: Int, canvasWidth: Int, canvasHeight: Int,
    centerLon: Double, centerLat: Double, placeLon: Double, placeLat: Double, placeType: PlaceType.PlaceType,
    text: String, canvas: Canvas, paint: Paint) {
    val savedColor = paint.getColor()
    val scalingFactor = math.pow(2.0, -screenZoomLevel)
    val x = canvasWidth / 2 + (scalingFactor * worldFile.xDiff(placeLon - centerLon)).asInstanceOf[Float]
    val y = canvasHeight / 2 + (scalingFactor * worldFile.yDiff(placeLat - centerLat)).asInstanceOf[Float]

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
}
