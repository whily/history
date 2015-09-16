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
* Android SDK r23.0.5.
* Scala (2.11.6)
* sbt (0.13.8)
* [Inkscape](http://inkscape.org) and [ImageMagick](http://www.imagemagick.org)
  to generate icons.

### Generate the icons

In project directory, run following command:

        $ ./genart

### Download the maps

The app uses Blue Marble map.

1. Download maps

Download Blue Marble Land Surface, Shallow Water, and Shaded
Topography Eastern Hemisphere map from
<http://earthobservatory.nasa.gov/Features/BlueMarble/BlueMarble_2002.php/>.
Direct link is:
<http://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57752/land_shallow_topo_east.tif>.

2. Put `land_shallow_topo_east.tif` directly into `tools` directory.

3. In the directory, run script `cropmap`, which automatically crop
the tile maps.

### Build the code

The library dependencies include
[scasci](https://github.com/whily/scasci),
[scaland](https://github.com/whily/scaland), and
[chinesecalendar](https://github.com/whily/chinesecalendar).  Please
follow the steps discussed in those libraries on how to use them.

To compile/run the code, run the following command to build the
   app and start it in a connected device:

        $ sbt android:run

### Testing

There are two types of testing can be performed:

* Unit testing. Simply run the following command in shell:

        $ sbt test

* Android integration testing. Run the following commands in sbt:

        > project tests
        > android:install
        > android:test
