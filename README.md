Sanguo
======

An Android app to read Sanguozhi (三国志), view corresponding maps, and more.

Features to include:

* History book.
* Maps.

Permissions below are needed to use Google Maps according to
[developer guide](https://developers.google.com/maps/documentation/android/start):

* android.permission.INTERNET Used by the API to download map tiles from Google Maps servers.
* android.permission.ACCESS_NETWORK_STATE Allows the API to check the
  connection status in order to determine whether data can be
  downloaded.
* com.google.android.providers.gsf.permission.READ_GSERVICES Allows
  the API to access Google web-based services.
* android.permission.WRITE_EXTERNAL_STORAGE Allows the API to cache
  map tile data in the device's external storage area.

For more information about Sanguo, please go to
  <https://github.com/whily/sanguo>

Development
-----------

The following tools are needed to build Sanguo from source:

* JDK version 6/7 from <http://www.java.com> if Java is not available. 
  Note that JDK is preinstalled on Mac OS X and available via package manager
  on many Linux systems. 
* Android SDK r22.
* Google Play services r16.
* Scala (2.10.0)
* sbt (0.12.4)
* [Inkscape](http://inkscape.org) and [ImageMagick](http://www.imagemagick.org)
  to generate icons.

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
   
2. Create library `lib` in the project root directory. Copy
   `google-play-services.jar` of Google Play services (e.g. the
   directory is
   `/opt/android-sdk/extras/google/google_play_services/libproject/google-play-services_lib/libs`
   in my machine. YMMV) to `lib` directory.
   
3. In directory `res\values`, create an xml file (e.g. pokey.xml)
   with following content: 
   
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
          <string name="map_api_key">YOUR_MAPS_API_KEY_HERE</string>
        </resources>
   
   To obtain Google Maps API key, visit
   https://developers.google.com/maps/documentation/android/start
   
4. In the project directory, run the following command to build the
   app and start it in a connected device:

        $ sbt android:start-device
        
Testing
-------

There are two types of testing can be performed:

* Unit testing. Simply run the following command in shell:
    
        $ sbt test
        
* Android integration testing. Run the following commands in sbt:

        > project tests
        > android:install-device
        > android:test-device  

