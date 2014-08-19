History
=======

An Android app to read Chinese history books (currently Sanguozhi/三国志), view 
corresponding maps, and more.

Features to include:

* History book.
* Maps.

For more information about History, please go to
  <https://github.com/whily/history>

Development
-----------

The following tools are needed to build History from source:

* JDK version 6/7 from <http://www.java.com> if Java is not available. 
  Note that JDK is preinstalled on Mac OS X and available via package manager
  on many Linux systems. 
* Android SDK r22.
* Scala (2.10.0)
* sbt (0.12.4)
* [Inkscape](http://inkscape.org) and [ImageMagick](http://www.imagemagick.org)
  to generate icons.

### Generate the icons

In project directory, run following command:

        $ ./genart           

### Download the maps

There are two choices of maps: one from Natural Earth, another from
Blue Marble. The difference between the two is that Natural Earth maps
shows water (rivers, lakes etc.), while Blue Marble has higher
resolution. Download map according to descriptoin below, and
comment/uncomment accordingly in both 'script/cropmap' and
'src/main/scala/TileMap.scala'.

1. Download maps

Download 1:10m Natural Earth II with Shaded Relief, Water, and
Drainages from
<http://www.naturalearthdata.com/downloads/10m-raster-data/10m-natural-earth-2/>.
Direct link is:
<http://www.naturalearthdata.com/http//www.naturalearthdata.com/download/10m/raster/NE2_HR_LC_SR_W_DR.zip>. Unzip
`NE2_HR_LC_SR_W_DR.tif` into tools directory.

Download Blue Marble Land Surface, Shallow Water, and Shaded
Topography Eastern Hemisphere map from
<http://earthobservatory.nasa.gov/Features/BlueMarble/BlueMarble_2002.php/>.
Direct link is:
<http://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57752/land_shallow_topo_east.tif>. 

2. Unnzip `NE2_HR_LC_SR_W_DR.tif` into `tools` directory, or put
`land_shallow_topo_east.tif` directly into `tools` directory.

3. In the directory, run script `cropmap`, which automatically crop
the tile maps.

### Build the code

The library dependencies include
[scasci](https://github.com/whily/scasci) and
[scaland](https://github.com/whily/scaland). Please follow the steps
discussed in those libraries on how to use them.

To compile/run the code, follow the steps below:

1. This step is a work around. It seems that the plugin sbt-android
   assumes that tools like `aapt` and `dx` are located in
   `$ANDROID_HOME/platform-tools`. However at least in Android SDK
   r22, the location is `$ANDROID_HOME/build-tools/18.0.1/`. The
   simplest solution is to copy those binaries (including directory
   **lib** which is related to `dx`) to folder
   `$ANDROID_HOME/platform-tools`.
   
2. In the project directory, run the following command to build the
   app and start it in a connected device:

        $ sbt android:start-device
        
### Testing

There are two types of testing can be performed:

* Unit testing. Simply run the following command in shell:
    
        $ sbt test
        
* Android integration testing. Run the following commands in sbt:

        > project tests
        > android:install-device
        > android:test-device  

