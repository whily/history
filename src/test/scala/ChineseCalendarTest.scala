/**
 * Test cases for ChineseCalendar.scala.
 * 
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License: 
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2014 Yujian Zhang
 */

import net.whily.android.history.ChineseCalendar._
import java.util.GregorianCalendar
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec
 
class ChineseCalendarSpec extends FunSpec with ShouldMatchers {
  describe("Test Chinese Calendar calculation") {
    it("Check months()") {
      // It is interesting that I should use "shoulbe be ===" to test two arrays. 
      // Using "shoulbe be" always returns true, while using "shoulbe equal" cannot compile.
      months("己未 己丑 戊午 戊子 丁巳 丁亥 丙辰 丙戌 丙辰 乙酉 乙卯 甲申") should be ===
        (Array(Month("一月", "己未"), Month("二月", "己丑"), Month("三月", "戊午"),
               Month("四月", "戊子"), Month("五月", "丁巳"), Month("六月", "丁亥"),
               Month("七月", "丙辰"), Month("八月", "丙戌"), Month("九月", "丙辰"),
               Month("十月", "乙酉"), Month("十一月", "乙卯"), Month("十二月", "甲申")))
      months("甲寅 癸未 癸丑 壬午 壬子 辛巳 辛亥 庚辰 閏 庚戌 己卯 己酉 戊寅 戊申") should be ===
        (Array(Month("一月", "甲寅"), Month("二月", "癸未"), Month("三月", "癸丑"),
               Month("四月", "壬午"), Month("五月", "壬子"), Month("六月", "辛巳"),
               Month("七月", "辛亥"), Month("八月", "庚辰"), Month("閏八月", "庚戌"), 
               Month("九月", "己卯"), Month("十月", "己酉"), Month("十一月", "戊寅"), 
               Month("十二月", "戊申")))
      months("己卯 閏 戊申 戊寅 戊申 丁丑 丁未 丙子 丙午 乙亥 乙巳 甲戌 甲辰 癸酉") should be ===
        (Array(Month("一月", "己卯"), Month("閏一月", "戊申"), Month("二月", "戊寅"), 
               Month("三月", "戊申"), Month("四月", "丁丑"), Month("五月", "丁未"), 
               Month("六月", "丙子"), Month("七月", "丙午"), Month("八月", "乙亥"), 
               Month("九月", "乙巳"), Month("十月", "甲戌"), Month("十一月", "甲辰"), 
               Month("十二月", "癸酉")))
    }

    it("Check date.") {
      toGregorianCalendar("漢平帝元始元年") should be (new GregorianCalendar(1, 2, 11))
    }
  }
}