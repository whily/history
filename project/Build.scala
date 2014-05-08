import sbt._

import Keys._
import AndroidKeys._

object General {
  // Some basic configuration
  val settings = Defaults.defaultSettings ++ Seq (
    name := "sanguo",
    version := "0.0.1",
    versionCode := 1,
    scalaVersion := "2.10.0",
    platformName in Android := "android-19",
    javacOptions ++= Seq("-encoding", "UTF-8", "-source", "1.6", "-target", "1.6")
  )

  // Default Proguard settings
  lazy val proguardSettings = inConfig(Android) (Seq (
    useProguard := true,
    proguardOptimizations += "-keep class net.whily.android.sanguo.** { *; }",
    proguardOptimizations += "-keep class scala.collection.SeqLike { public java.lang.String toString(); }",
    // Following is for Google Play services accordingly to: http://developer.android.com/google/play-services/setup.html
    proguardOptimizations += "-keep class * extends java.util.ListResourceBundle { protected Object[][] getContents(); }",
    proguardOptimizations += "-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable { public static final *** NULL;}",
    proguardOptimizations += "-keepnames @com.google.android.gms.common.annotation.KeepName class *",
    proguardOptimizations += "-keepclassmembernames class * { @com.google.android.gms.common.annotation.KeepName *; }",
    proguardOptimizations += "-keepnames class * implements android.os.Parcelable { public static final ** CREATOR; }"
  ))

  // Full Android settings
  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    TypedResources.settings ++
    proguardSettings ++
    AndroidManifestGenerator.settings ++
    AndroidMarketPublish.settings ++ Seq (
      keyalias in Android := "change-me",
      libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test",
      libraryDependencies += "net.whily" %% "scasci" % "0.0.1-SNAPSHOT",
      libraryDependencies += "net.whily" %% "scaland" % "0.0.1-SNAPSHOT"
    )
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "main",
    file("."),
    settings = General.fullAndroidSettings ++ AndroidEclipseDefaults.settings
  )

  lazy val tests = Project (
    "tests",
    file("tests"),
    settings = General.settings ++
               AndroidEclipseDefaults.settings ++
               AndroidTest.androidSettings ++
               General.proguardSettings ++ Seq (
      name := "sanguoTests"
    )
  ) dependsOn main
}
