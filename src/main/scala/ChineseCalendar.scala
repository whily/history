/**
 * Chinese Calendar.
 *
 * @author  Yujian Zhang <yujian{dot}zhang[at]gmail(dot)com>
 *
 * License: 
 *   GNU General Public License v2
 *   http://www.gnu.org/licenses/gpl-2.0.html
 * Copyright (C) 2014 Yujian Zhang
 */

package net.whily.android.history

import java.util.{Calendar, GregorianCalendar}

object ChineseCalendar {
  // 干支
  val Sexagenary = Array("甲子", "乙丑", "丙寅", "丁卯", "戊辰", "己巳", "庚午", "辛未", "壬申", "癸酉",
                         "甲戌", "乙亥", "丙子", "丁丑", "戊寅", "己卯", "庚辰", "辛巳", "壬午", "癸未",
                         "甲申", "乙酉", "丙戌", "丁亥", "戊子", "己丑", "庚寅", "辛卯", "壬辰", "癸巳",
                         "甲午", "乙未", "丙申", "丁酉", "戊戌", "己亥", "庚子", "辛丑", "壬寅", "癸卯",
                         "甲辰", "乙巳", "丙午", "丁未", "戊申", "己酉", "庚戌", "辛亥", "壬子", "癸丑",
                         "甲寅", "乙卯", "丙辰", "丁巳", "戊午", "己未", "庚申", "辛酉", "壬戌", "癸亥")

  def toGregorianCalendar(monarch: String, era: String, year: Int, month: Int, day: Int): GregorianCalendar = {
    new GregorianCalendar(1, 1, 1)
  }

  def fromGregorianCalendar(calendar: GregorianCalendar): String = {
    ""
  }

  /**
    * @param month       month name
    * @param sexagenary sexagenary (干支) of the first day of the month
    */
  case class Month(month: String, sexagenary: String)

  val Months = Array("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二")
  val LeapMonth = "閏"

  val MonthInts = Array(-1, Calendar.JANUARY, Calendar.FEBRUARY)

  /** Return a data (as in Gregorian Calendar) given year, month (only
    * January and February), and date. */
  def date(year: Int, month: Int, dayOfMonth: Int) =
    new GregorianCalendar(year, MonthInts(month), dayOfMonth)

  /** Return an array of months by parsing the string S, in the format of
    *   sexageneray1 sexagenary2 ...
    * If there is a leap month, then insert character 閏.
    */
  def months(s: String): Array[Month] = {
    null
  }

  /**
    * Represents one year in Chinese calendar. 
    * 
    * @param firstDay   the first day in Gregorian Calendar.
    * @param months     the 12 or 13 months in the year
    */
  case class Year(firstDay: GregorianCalendar, months: Array[Month])

  /** Return object Year given year, month, dayOfMonth, months. */
  def y(year: Int, month: Int, dayOfMonth: Int, monthStr: String) =
    Year(date(year, month, dayOfMonth), months(monthStr))

  // Information from 中国史历日和中西历日对照表 (方诗铭，方小芬 著)
  // Array index is the AD year.
  val ADYears = Array(
    null, // There is no year as AD 0.
    y(1,  2, 11, "己未 己丑 戊午 戊子 丁巳 丁亥 丙辰 丙戌 丙辰 乙酉 乙卯 甲申"), 
    y(2,  2, 1,  "甲寅 癸未 癸丑 壬午 壬子 辛巳 辛亥 庚辰 閏 庚戌 己卯 己酉 戊寅 戊申"),
    y(3,  2, 20, "戊寅 丁未 丁丑 丙午 丙子 乙巳 乙亥 甲辰 甲戌 癸卯 癸酉 壬寅"),
    y(4,  2, 9,  "壬申 辛丑 辛未 庚子 庚午 庚子 己巳 己亥 戊辰 戊戌 丁卯 丁酉"), 
    y(5,  1, 29, "丙寅 丙申 乙丑 乙未 甲子 閏 甲午 癸亥 癸巳 癸亥 壬辰 壬戌 辛卯 辛酉"), 
    y(6,  2, 17, "庚寅 庚申 己丑 己未 戊子 戊午 丁亥 丁巳 丙戌 丙辰 乙酉 乙卯"),
    y(7,  2, 7,  ""),
    y(8,  1, 27, ""), 
    y(9,  2, 14, ""), 
    y(10, 2, 3,  ""), 
    y(11, 2, 22, ""), 
    y(12, 2, 99, ""), 
    y(13, 2, 99, "")
  )
}
