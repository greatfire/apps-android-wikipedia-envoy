### Wikipedia Android app

This repository is a fork of the official [Wikipedia Android app](https://play.google.com/store/apps/details?id=org.wikipedia) that has been integrated with the [Envoy library](https://github.com/greatfire/envoy). There are several additional steps required to build this version of the app:

1. Setup Cronet
   1. Download cronet.aar  
   Cronet.aar has the same API as Google's cronet (except for one extra method `setEnvoyUrl`).
   Download this aar file [cronet-0.0.87.0.4280.66.aar](https://repo1.maven.org/maven2/info/guardianproject/envoy/cronet/0.0.87.0.4280.66/cronet-0.0.87.0.4280.66.aar).
   1. Copy cronet-$BUILD.aar, envoy-$BUILD.aar, and IPtProxy.aar to `/app/libs`.
   1. Alternately, use the Maven dependencies currently included in `/app/build.gradle`.


1. To build envoy-$BUILD.aar, follow the instructions in the android directory [here](https://github.com/mnbogner/envoy/tree/envoy-feedback)
   Envoy is a bootstrapping library that has adapters for serval popular libraries (OkHttp/Retrofit, Volley, etc).
   This version of the wikipedia app relies on broadcasts from the branch of Envoy specified above.
   Copy and rename the cronet-0.0.87.0.4280.66.aar file from step one to `android/cronet/cronet-$BUILD.aar`.
   $BUILD will be debug or release depending on your build variant.
   
1. Build IPtProxy from the branch [here](https://gitlab.com/stevenmcdonald/IPtProxy/-/tree/dnstt-client), or download the IPtProxy.aar file.

1. Several parameters must be specified to support envoy services. If these parameters need to be changed, create a file called credentials.properties in the root project directory. It should have the following contents: 
   ```
   dnsttdomain=<the domain name used for DNSTT>
   dnsttkey=<the authentication key for DNSTT>
   dnsttpath=<the path to the file on the DNSTT HTTP server that contains additional urls>
   dohUrl=<the URL or address of a reachable DNS over HTTP provider>
   dotAddr=<the URL or address of a reachable DNS over TCP provider>
   hystCert=<comma delimited string representing a certificate in PEM format>
   defProxy=<comma separated list of urls>
   ```

   - Only a DNS over HTTP or DNS over TCP provider is required, an empty string should be provided for the other.
   - The optional hystCert parameter must be included if you intend to submit any Hysteria URLs. It is a comma delimited string representing a self generated root certificate for the hysteria server in PEM format.
   - If the optional defProxy parameter is included, Envoy will attempt to connect to those urls directly first. This can be included to avoid using proxy resources when the target domain is not blocked.

   After creating this file, run the following gradle command in the root project directory:
   ```
   ./gradlew hideSecretFromPropertiesFile -PpropertiesFileName=credentials.properties -Ppackage=org.greatfire.wikiunblocked
   ```

1. Specify the `greatfire` flavor when building the application, eiither on the command line or in Android Studio, e.g. `./gradlew assembleGreatfireDebug` or:

![variant](https://user-images.githubusercontent.com/6945405/173699837-5108cc88-4fe1-4165-9961-1d600e0f681c.png)

1. When running the application, click on the "More" icon a the bottom and look for the "Anonymous" icon. A check indicates that Envoy is running, an X indicates that Envoy is not running. (note that Envoy will not run if a direct connection can be made)

![screen_mark](https://user-images.githubusercontent.com/6945405/173699843-3c9a50fd-3936-49ef-95f0-eb39dedea2bd.png)

### Documentation

Documentation for the Wikipedia app is kept on [the wiki](https://www.mediawiki.org/wiki/Wikimedia_Apps/Team/Android/App_hacking).

### Issues

Please file issues with the Wiki Unblocked app in [the bug tracker](https://github.com/greatfire/apps-android-wikipedia-envoy/issues).
