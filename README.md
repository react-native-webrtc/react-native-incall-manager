# react-native-incall-manager

[![npm version](https://badge.fury.io/js/react-native-incall-manager.svg)](https://badge.fury.io/js/react-native-incall-manager)
[![npm downloads](https://img.shields.io/npm/dm/react-native-incall-manager.svg?maxAge=2592000)](https://img.shields.io/npm/dm/react-native-incall-manager.svg?maxAge=2592000)


Handling media-routes/sensors/events during a audio/video chat on React Native

## Purpose:

The purpose of this module is to handle actions/events during a phone call (audio/video) on `react-native`, ex:

* Manage devices events like wired-headset plugged-in state, proximity sensors and expose functionalities to javascript.
* Automatically route audio to proper devices based on events and platform API.
* Toggle speaker or microphone on/off, toggle flashlight on/off
* Play ringtone/ringback/dtmftone

Basically, it is a telecommunication module which handles most of the requirements when making/receiving/talking with a call.  
  
This module is designed to work with [react-native-webrtc](https://github.com/oney/react-native-webrtc)  
  
## TODO / Contribution Wanted:  
  
* Make operations run on the main thread. ( iOS/Android )  
* Fix iOS audio shared instance singleton conflict with internal webrtc.  
* Detect hardware button press event and react to it.  
  ex: press bluetooth button, send an event to JS to answer/hangup.  
  ex: press power button to mute incoming ringtone.  
* Use config-based to decide which event should start and report. maybe control behavior as well.  
* Flash API on Android.  
  
## Installation:

**From npm package**: `npm install react-native-incall-manager`  
**From git package**: `npm install git://github.com/zxcpoiu/react-native-incall-manager.git`  

===================================================
### Android:
  
This module requires Android 7.0 (API level 24) or later. Set `minSdkVersion` to at least `24` in your app's `android/app/build.gradle`.

React Native 0.60+ links the module automatically (autolinking); there is nothing to add to `settings.gradle`, `build.gradle` or `MainApplication`.

#### Permissions

The module needs these permissions in your app's `AndroidManifest.xml`:

```xml
<!-- start() and startRingtone() hold a partial wake lock, pokeScreen() a full one;
     without it start() throws a SecurityException -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- setMode / speakerphone / Bluetooth SCO routing is ignored without it -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<!-- Bluetooth headsets on Android 11 and lower -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<!-- Bluetooth headsets on Android 12+; a runtime permission, see below -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

`WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS` and `BLUETOOTH` are granted at install. On Android 12 (API 31) and later `BLUETOOTH_CONNECT` must also be granted at runtime before `start()`, for example with `PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT)`; without it the module offers no `BLUETOOTH` route.

#### Optional sound files on android

If you want to use bundled ringtone/ringback/busytone sound instead of system sound,  
put files in `android/app/src/main/res/raw`  
and rename file correspond to sound type:  

```
incallmanager_busytone.mp3  
incallmanager_ringback.mp3  
incallmanager_ringtone.mp3 
```

On android, as long as your file extension supported by android, this module will load it.

===================================================

### ios:

`react-native link react-native-incall-manager`

#### Using CocoaPods

Update the following line with your path to node_modules/ and add it to your Podfile:

`pod 'ReactNativeIncallManager', :path => '../node_modules/react-native-incall-manager'`

#### Manually Linking

In case `react-native link` doesn't work,

- Drag `node_modules/react-native-incall-manager/ios/RNInCallManager.xcodeproj` under `<your_xcode_project>/Libraries`
- Select `<your_xcode_project>` --> `Build Phases` --> `Link Binary With Libraries`
  - Drag `Libraries/RNInCallManager.xcodeproj/Products/libRNInCallManager.a` to `Link Binary With Libraries`
- Select `<your_xcode_project>` --> `Build Settings`
  - In `Header Search Paths`, add `$(SRCROOT)/../node_modules/react-native-incall-manager/ios/RNInCallManager`

#### Clean project if messed up:

  The installation steps are a bit complex, it might be related your xcode version, xcode cache, converting swift version, and your own path configurations. if something messed up, please follow steps below to clean this project, then do it again steps by steps.

  1. Delete all project/directory in xcode related to incall-manager
  2. Delete `react-native-incall-manager` in node_modules ( rm -rf )
  3. Xcode -> Product -> clean
  4. Close xcode
  5. Run `npm install` again
  6. Open xcode and try the install process again steps by steps

  If someone knows a simpler way to set this project up, let me know plz.

#### Optional sound files on iOS

If you want to use bundled ringtone/ringback/busytone sound instead of system sound 

1. Add files into your_project directory under your project's xcodeproject root. ( or drag into it as described above. )
2. Check `copy file if needed`
3. Make sure filename correspond to sound type:

```
incallmanager_busytone.mp3
incallmanager_ringback.mp3 
incallmanager_ringtone.mp3 
```

On ios, we only support mp3 files currently.

## Usage:

This module implements a basic handle logic automatically, just:

```javascript
import InCallManager from 'react-native-incall-manager';

// --- start manager when the chat start based on logics of your app 
// On Call Established:
InCallManager.start({media: 'audio'}); // audio/video, default: audio

// ... it will also register and emit events ...

// --- On Call Hangup:
InCallManager.stop();
// ... it will also remove event listeners ...
```

If you want to use ringback:

```javascript
// ringback is basically for OUTGOING call. and is part of start().

InCallManager.start({media: 'audio', ringback: '_BUNDLE_'}); // or _DEFAULT_ or _DTMF_
//when callee answered, you MUST stop ringback explicitly:
InCallManager.stopRingback();
```

If you want to use busytone:

```javascript
// busytone is basically for OUTGOING call. and is part of stop()
// If the call failed or callee are busing,
// you may want to stop the call and play busytone
InCallManager.stop({busytone: '_DTMF_'}); // or _BUNDLE_ or _DEFAULT_
```

If you want to use ringtone:

```javascript
// ringtone is basically for INCOMING call. it's independent to start() and stop()
// if you receiving an incoming call, before user pick up,
// you may want to play ringtone to notify user.
InCallManager.startRingtone('_BUNDLE_'); // or _DEFAULT_ or system filename with extension

// when user pickup
InCallManager.stopRingtone();
InCallManager.start();

// or user hangup
InCallManager.stopRingtone();
InCallManager.stop();

```

On Android, an audible incoming ringtone requests transient audio focus with
ringtone audio attributes. A vibration-only ringtone does not request focus
unless a Bluetooth HFP output is connected. Ringtone timeout and cleanup only
release focus acquired for that ringtone; they do not release focus retained
by an active call or an explicit `requestAudioFocus()` call. Ringtone vibration
uses ringtone audio attributes and is cancelled by `stopRingtone()`.
When call or explicitly requested focus overlaps a ringtone, call audio
attributes take priority. Each `requestAudioFocus()` and `start()` call makes a
fresh Android focus request, including after a focus-loss callback. An explicit
`abandonAudioFocus()` remains a global request to release this module's current
Android audio focus; ringtone and call cleanup do not implicitly reacquire it.
Pair each explicit `requestAudioFocus()` with `abandonAudioFocus()`. `stop()`
and `stopRingtone()` release only their own lifecycle interest, so a retained
explicit request continues to hold focus until it is explicitly abandoned.

Also can interact with events if you want:
See API section.

```javascript
import { DeviceEventEmitter } from 'react-native';

DeviceEventEmitter.addListener('Proximity', function (data) {
    // --- do something with events
});

```



## Automatic Basic Behavior:

**On start:**  
* Store current settings, set KeepScreenOn flag = true, and register some event listeners.
* If media type is `audio`, route voice to earpiece, otherwise route to speaker.
* iOS: an `audio` call enables the proximity sensor (a `video` call doesn't), and when it detects the user close to the screen the system turns the screen off.
* Android: the proximity sensor is on for both media types; when it detects the user close to the screen while audio is routed to the earpiece, the screen is turned off to avoid accidental touches. It doesn't change the route.
* When newly external device plugged, such as wired-headset, route audio to an external device.
* Optional play ringback

**On stop:**  

* Set KeepScreenOn flag = false, remote event listeners, restore original user settings.
* Optionally play busytone

## Custom Behavior:  

You can customize behavior using API/events exposed by this module. See `API` section.

Note: iOS only supports `auto` currently.

## API:

**Methods**

|  Method      |  android |   ios   |  description |
|  :---  |   :---:  |  :---:  |     :---    |
| start(`{media: ?string, auto: ?boolean, ringback: ?string}`)   | :smile: | :smile: | start incall manager.</br> ringback accept non-empty string or it won't play</br>default: `{media:'audio', auto: true, ringback: ''}`  |
| stop(`{busytone: ?string}`)   | :smile: | :smile: | stop incall manager</br> busytone accept non-empty string or it won't play</br> default: `{busytone: ''}` |
| turnScreenOn()   | :smile: | :rage: | force turn screen on |
| turnScreenOff()   | :smile: | :rage: | force turn screen off |
| setKeepScreenOn(`enable: ?boolean`)   | :smile: | :smile: | set KeepScreenOn flag = true or false</br>default: false |
| setSpeakerphoneOn(`enable: ?boolean`)   | :smile: | :rage: | toggle speaker ON/OFF once. but not force</br>default: false |
| setForceSpeakerphoneOn(`flag: ?boolean`)   | :smile: | :smile: | true -> force speaker on</br> false -> force speaker off</br> null -> use default behavior according to media type</br>default: null |
| setMicrophoneMute(`enable: ?boolean`)   | :smile: | :rage: | mute/unmute micophone</br>default: false</br>p.s. if you use webrtc, you can just use `track.enabled = false` to mute |
| async getAudioUri(`audioType: string, fileType: string`)   | :smile: | :smile: | get audio Uri path. this would be useful when you want to pass Uri into another module.</br>`audioType`: 'ringtone', 'ringback' or 'busytone'</br>`fileType`: '_DEFAULT_', '_BUNDLE_' or a system file name</br>resolves `null` if there is none |
| startRingtone(`ringtone: string, ?vibrate_pattern: array, ?ios_category: string, ?seconds: number`)   | :smile: | :smile: | play ringtone. </br>`ringtone`: '_DEFAULT_' or '_BUNDLE_'</br>`vibrate_pattern`: same as RN, but does not support repeat</br>`ios_category`: ios only, if you want to use specific audio category</br>`seconds`: android only, specify how long do you want to play rather than play once nor repeat. in sec.|
| stopRingtone()   | :smile: | :smile: | stop play ringtone if previous started via `startRingtone()` |
| stopRingback()   | :smile: | :smile: | stop play ringback if previous started via `start()` |
| setFlashOn(`enable: ?boolean, brightness: ?number`)  | :rage: | :smile: | set flash light on/off |
| async getIsWiredHeadsetPluggedIn()  | :smile: | :smile: | return wired headset plugged in state</br>resolves `{isWiredHeadsetPluggedIn: boolean}` |



**Events**

|  Event      |  android |   ios   |  description |
|  :---  |   :---:  |  :---:  |     :---    |
| 'Proximity'   | :smile: | :smile: | proximity sensor detected changes.<br>data: `{'isNear': boolean}` |
| 'WiredHeadset'| :smile: | :smile:  | fire when wired headset plug/unplug<br>data: `{'isPlugged': boolean, 'hasMic': boolean, 'deviceName': string }` |
| 'NoisyAudio'  | :smile: | :rage: | see [android doc](http://developer.android.com/reference/android/media/AudioManager.html#ACTION_AUDIO_BECOMING_NOISY).<br>data: `null` |
| 'MediaButton' | :neutral_face: | :rage: | when external device controler pressed button. see [android doc](http://developer.android.com/reference/android/content/Intent.html#ACTION_MEDIA_BUTTON) <br>data: `{'eventText': string, 'eventCode': number }`<br>Android 8.0+ delivers headset buttons to media sessions, not to this broadcast, so it does not fire for them there; Android 7.x sends it only while no media session is active. |
| 'onAudioFocusChange' | :smile: | :rage: | see [android doc](http://developer.android.com/reference/android/media/AudioManager.OnAudioFocusChangeListener.html#onAudioFocusChange(int)) <br>data: `{'eventText': string, 'eventCode': number }` |

**NOTE: platform OS always has the final decision, so some toggle API may not work in some cases
be careful when customizing your own behavior**

## LICENSE:

**[ISC License](https://opensource.org/licenses/ISC)** ( functionality equivalent to **MIT License** )

## Original Author:
[![zxcpoiu](https://github.com/zxcpoiu.png)](https://github.com/zxcpoiu)
