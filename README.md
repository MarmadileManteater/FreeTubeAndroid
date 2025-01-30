<p align="center" >
 <img src="https://docs.freetubeapp.io/images/logoColor.png" width=500 align="center" alt="FreeTube Logo">
</p>
<h2 align='center'>
An open source YouTube player built with privacy in mind.
</h2>

<p align='center'>
  <a href='https://github.com/MarmadileManteater/FreeTubeCordova/actions/workflows/buildCordova.yml'>
    <img alt='Build Status' src='https://github.com/MarmadileManteater/FreeTubeCordova/actions/workflows/buildCordova.yml/badge.svg'>
  </a>
  <a href="https://apt.izzysoft.de/fdroid/index/apk/io.freetubeapp.freetube">
    <img alt='izzyondroid version' src="https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/io.freetubeapp.freetube">
  </a>
<a href="https://hosted.weblate.org/engage/free-tube/">
<img src="https://hosted.weblate.org/widgets/free-tube/-/svg-badge.svg" alt="Translation status" />
</a>
</p>
<hr>
<p align="center"><a href="#screenshots">Screenshots</a> &bull; <a href="#how-does-it-work">How does it work?</a> &bull; <a href="#features">Features</a> &bull; <a href="#how-to-build-and-test">Building and testing</a> &bull; <a href="#contributing">Contributing</a> &bull; <a href="#localization">Localization</a> &bull; <a href="#contact">Contact</a> &bull; <a href="#upstream-donations">Donate</a> &bull; <a href="#license">License</a></p>
<p align="center"><a href="https://freetubeapp.io/">Website</a> &bull; <a href="https://blog.freetubeapp.io/">Blog</a> &bull; <a href="https://docs.freetubeapp.io/">Documentation</a> &bull; <a href="https://docs.freetubeapp.io/faq/">FAQ</a> &bull; <a href="https://github.com/FreeTubeApp/FreeTube/discussions">Discussions</a></p>
<hr>

FreeTube Android is an open source YouTube player built with privacy in mind. Use YouTube without advertisements and prevent Google from tracking you with their cookies and JavaScript.
Available as an APK and as a PWA (progressive web app). FreeTube Android is a fork of [FreeTube](https://www.github.com/FreeTubeApp).

> [!NOTE]
> FreeTube Android is currently in Beta. While it should work well for most users, there are still bugs and missing features that need to be addressed.

<p align='center'>
  <a href='https://apt.izzysoft.de/fdroid/index/apk/io.freetubeapp.freetube'>
    <img height='100' alt='Get it on IzzyOnDroid' src='https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png'>
  </a>
</p>

<p align="center"><a href="https://github.com/MarmadileManteater/FreeTubeCordova/releases">Download FreeTube Android</a></p>

<hr>

## How does it work?
The APK uses a built in extractor to grab and serve data / videos, and can optionally use the [Invidious API](https://github.com/iv-org/invidious). The PWA *only* uses the Invidious API. No official YouTube APIs are used to obtain data. Your subscriptions and history are stored locally on your device and are never sent out.

## Features
* Watch videos without ads
* Use YouTube without Google tracking you using cookies and JavaScript
* Subscribe to channels without an account
* Connect to an externally setup proxy such as Tor
* View and search your local subscriptions, history, and saved videos
* Organize your subscriptions into "Profiles" to create a more focused feed
* Export & import subscriptions
* Youtube Trending
* Youtube Chapters
* Most popular videos page based on the set Invidious instance
* SponsorBlock 
* Full Theme support
* Keyboard shortcuts
* Option to show only family friendly content
* Show/hide functionality or elements within the app using the distraction free settings

Go to [FreeTube's Documentation](https://docs.freetubeapp.io/) if you'd like to know more about how to operate FreeTube and its features.

## Screenshots
<img alt="watching video" src="./metadata/en-US/images/phoneScreenshots/1.png" width=300> <img alt="trending videos" src="./metadata/en-US/images/phoneScreenshots/2.png" width=300> <img alt="subscription feed" src="./metadata/en-US/images/phoneScreenshots/3.png" width=300>


### Automated Builds (Nightly / Weekly)
Builds are automatically created from changes to our development branch via [GitHub Actions](https://github.com/MarmadileManteater/FreeTubeCordova/actions/workflows/buildCordova.yml).

The first build with a green check mark is the latest build. You will need to have a GitHub account to download these builds.

## How to build and test
### Commands for the APK
```bash
# 📦 Packs the project using `webpack.android.config.js`
yarn pack:android
# 🤖 Packs the botguard script
yarn pack:botGuardScript:android
# 🚧 for development
yarn pack:android:dev
```
> [!NOTE]
> These commands only build the assets necessary for the project located in `android/` to be built. In order to obtain a complete build, you will need to build the project located in `android/` with `gradle`.
### Commands for the PWA
```bash
# 🐛 Debugs the project using `webpack.web.config.js`
yarn dev:web
# 📦 Packs the project using `webpack.web.config.js` 
yarn pack:web
```

### Commands for the PWA Docker Image
```bash

# 💨 Creates and runs the image locally. Add `--platform=linux/arm64` to docker build for ARM64 devices including Apple Silicon
docker build -t freetubecordova . # Warning, might take a while on Apple Silicon
docker run --name ftcordova -d -p 8080:80 freetubecordova

# ⬇ Pulls the latest from the Github Container Registry (ghcr.io)
docker pull ghcr.io/marmadilemanteater/freetubecordova:latest
# 👟 Runs the image from ghcr.io
docker run --name ftcordova -d -p 8080:80 ghcr.io/marmadilemanteater/freetubecordova:latest

# 🏃 Runs the image from Docker Hub.
docker run --name ftcordova -d -p 8080:80 owentruong/freetubecordova:latest

# 🏃‍♂️ Runs the image from Docker Hub (ARM64)
docker run --platform=linux/arm64 --name ftcordova -d -p 8080:80 owentruong/freetubecordova:latest-arm64
```
## Contributing

**NOTICE: MOST CHANGES SHOULD PROBABLY BE MADE TO [UPSTREAM](https://www.github.com/freetubeapp/freetube) UNLESS DIRECTLY RELATED TO CORDOVA CODE OR WORKFLOWS.**

If you like to get your hands dirty and want to contribute, we would love to
have your help.  Send a pull request and someone will review your code. Please
follow the [Contribution
Guidelines](https://github.com/MarmadileManteater/FreeTubeCordova/blob/development/CONTRIBUTING.md)
before sending your pull request.


## Localization
<a href="https://hosted.weblate.org/engage/free-tube/">
<img src="https://hosted.weblate.org/widgets/free-tube/-/287x66-grey.png" alt="Translation status" />
</a>

If you'd like to localize FreeTube Android, please send submissions to [FreeTube's weblate](https://hosted.weblate.org/engage/free-tube/).

## Contact
If you ever have any questions, feel free to make an issue here on GitHub. 

## Upstream Donations
If you enjoy using FreeTube Android, you're welcome to leave a donation using the following methods to support upstream development and maintenance.  

* Bitcoin Address: `1Lih7Ho5gnxb1CwPD4o59ss78pwo2T91eS`

While your donations are much appreciated, only donate if you really want to.  Donations are used for keeping the website up and running and eventual code signing costs.

## License
[![GNU AGPLv3 Image](https://www.gnu.org/graphics/agplv3-155x51.png)](https://www.gnu.org/licenses/agpl-3.0.html)  

FreeTube is Free Software: You can use, study share and improve it at your
will. Specifically you can redistribute and/or modify it under the terms of the
[GNU Affero General Public License](https://www.gnu.org/licenses/agpl-3.0.html) as
published by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.  
