# Wikipedia Android app

This repository is a fork of the official [Wikipedia Android app](https://play.google.com/store/apps/details?id=org.wikipedia) that has been integrated with the [Envoy library](https://github.com/greatfire/envoy).

## Building the App

1. Configuration parameters must be specified to enable Envoy services. If these parameters need to be changed, create a file called credentials.properties in the root project directory. The following options are supported:

* `defProxy`: comma separated list of Envoy URLs to proxies
* `hystCert`: If using a Hysteria server with a self-signed certificate, this is the root certificate PEM file, with newlines replaced with commas

Example:
   ```
   hystCert=MIIEtjCCAx6gAwIBAgIRAK8VbNIfz8BrRYM1uWDRoGowDQYJKoZIhvcNAQELBQAw,czEeMBwGA1UEChMVbWtjZXJ0IGRldmVsb3BtZW50IENB...
   defProxy=https://wiki.example.com/wikipedia/,https://proxy.example.org/wiki/
   ```

   After creating this file, run the following gradle command in the root project directory:
   ```
   ./gradlew hideSecretFromPropertiesFile -PpropertiesFileName=credentials.properties -Ppackage=org.greatfire.wikiunblocked
   ```

More information on Envoy URLs can be found in the [Envoy Documentation](https://github.com/greatfire/envoy/blob/master/android/README.md)

2. Specify the `greatfire` flavor when building the application, eiither on the command line or in Android Studio, e.g. `./gradlew assembleGreatfireDebug` or:

![variant](https://user-images.githubusercontent.com/6945405/173699837-5108cc88-4fe1-4165-9961-1d600e0f681c.png)

3. When running the application, click on the "More" icon a the bottom and look for the "Anonymous" icon. A check indicates that Envoy is running, an X indicates that Envoy is not running. (note that Envoy will not run if a direct connection can be made)

![screen_mark](https://user-images.githubusercontent.com/6945405/173699843-3c9a50fd-3936-49ef-95f0-eb39dedea2bd.png)

### Documentation

Documentation for the Wikipedia app is kept on [the wiki](https://www.mediawiki.org/wiki/Wikimedia_Apps/Team/Android/App_hacking).

### Issues

Please file issues with the Wiki Unblocked app in [the bug tracker](https://github.com/greatfire/apps-android-wikipedia-envoy/issues).
