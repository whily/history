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
  // private val worldFile = new WorldFile(0.01666666666667, -0.01666666666667, 
  //                                       -179.99166666666667, 89.99166666666667)
  // // The following parameters are related to how we crop the map.
  // private val mapX    = 2048    // Number of pixels in X dimension (west-east)
  // private val mapY    = 2048    // Number of pixels in Y dimension (north-south)
  // private val mapLeft = 16650   // The left (west) of the map 
  // private val mapTop  = 2450    // The top (north) of the map

  // Blue Marble  Land Surface, Shallow Water, and Shaded Topography Eastern Hemisphere map
  // World file is from http://grasswiki.osgeo.org/wiki/Blue_Marble
  private val worldFile = new WorldFile(0.008333333333333, -0.008333333333333, 
                                        0.00416666666666665, 89.99583333333334)
  // The following parameters are related to how we crop the map.
  private val mapX    = 4096    // Number of pixels in X dimension (west-east)
  private val mapY    = 4096    // Number of pixels in Y dimension (north-south)
  private val mapLeft = 11700   // The left (west) of the map 
  private val mapTop  = 5000    // The top (north) of the map

  private val tileSize = 256

  // TODO: adjust for zoom level.
  private val nTileX = mapX / tileSize
  private val nTileY = mapY / tileSize

  private val maps = new Array[Bitmap](nTileX * nTileY)

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
          if (maps(index) == null) {
            val tileZoomLevel = if (screenZoomLevel < 0) 0 else screenZoomLevel
            maps(index) = BitmapFactory.decodeResource(context.getResources(),
              Util.getDrawableId(context, "map_" + tileZoomLevel + "_" + i + "_" + j))
          }
          canvas.drawBitmap(maps(index), null, tileRect, paint)
        } else {
          maps(index) = null    // Release bitmap memory.
        }
      }
    }

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

    paint.setColor(Color.rgb(0x1f, 0x5e, 0x67))
    placeType match {
      case PlaceType.Capital => 
        paint.setStyle(Paint.Style.STROKE)
        canvas.drawCircle(x, y, 16f, paint)
        canvas.drawCircle(x, y, 12f, paint)
        canvas.drawCircle(x, y, 8f, paint)
      case PlaceType.Province => 
        paint.setStyle(Paint.Style.STROKE)
        canvas.drawCircle(x, y, 14f, paint)
        canvas.drawCircle(x, y, 6f, paint)
      case PlaceType.Prefecture => 
        paint.setStyle(Paint.Style.STROKE)
        canvas.drawCircle(x, y, 12f, paint)
        paint.setStyle(Paint.Style.FILL)
        canvas.drawCircle(x, y, 6f, paint)
      case PlaceType.County => 
        paint.setStyle(Paint.Style.STROKE)
        canvas.drawCircle(x, y, 10f, paint)
      case PlaceType.Town => 
        paint.setStyle(Paint.Style.FILL)
        canvas.drawCircle(x, y, 8f, paint)
    }
    paint.setColor(Color.BLACK)

    val textSize = placeType match {
      case PlaceType.Capital => 44f
      case PlaceType.Province => 42f
      case PlaceType.Prefecture => 40f
      case PlaceType.County => 38f
      case PlaceType.Town => 36f
    }
    paint.setTextSize(textSize)
    canvas.drawText(text, x + 10, y, paint)
    paint.setColor(savedColor)
  }

  /** Return longitude difference given pixel difference in x-coordinate. */
  def lonDiff(pixelDiff: Double) = worldFile.lonDiff(pixelDiff)

  /** Return latitude difference given pixel differnce in y-coordinate. */
  def latDiff(pixelDiff: Double) = worldFile.latDiff(pixelDiff)

  private val places = 
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
          Place("九真郡", 19.78, 105.71),    // 胥浦县/东山县 To be confirmed 105o 42' 19", 19o 47' 44"
          Place("日南郡", 16.830278, 107.097222),    // 西卷县/東河市 To be confirmed

          Place("夷陵", 30.766667, 111.316667, PlaceType.Town)    // To be confirmed
    )
}
