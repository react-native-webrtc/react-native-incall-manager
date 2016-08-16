# react-native-incall-manager

[![npm version](https://badge.fury.io/js/react-native-incall-manager.svg)](https://badge.fury.io/js/react-native-incall-manager)
[![npm downloads](https://img.shields.io/npm/dm/react-native-incall-manager.svg?maxAge=2592000)](https://img.shields.io/npm/dm/react-native-incall-manager.svg?maxAge=2592000)


Handling media-routes/sensors/events during a audio/video chat on React Native

## Purpose:
The purpose of this module is to handle actions/events during a phone call (audio/video) on `react-native`, ex:
* manage devices events like wired-headset plugged, proximity sensors and expose to javascript.
* automatically route audio to proper device based on events and platform API.
* toggle speaker or microphone on/off, toggle flash light on/off (not implemented yes)
* play ringtone/ringback/dtmftone

basically, it is a telecommunication module which handle most of requirements when making/receiving/talking to a call.

This module is desinged to work with [react-native-webrtc](https://github.com/oney/react-native-webrtc)
you can find demo here: https://github.com/oney/RCTWebRTCDemo

## Installation:

**from npm package**: `npm install react-native-incall-manager`  
**from git package**: `npm install git://github.com/zxcpoiu/react-native-incall-manager.git`  

===================================================
####android:

After install, you can use `rnpm` (`npm install rnpm -g`) to link android.  
use `rnpm link react-native-incall-manager` to link or manually if you like.

**optional sound files on android**
if you want to use bundled ringtone/ringback/busytone sound instead of system sound 
  
We use android support library v4 to check/request permissions.  
You should add `compile "com.android.support:support-v4:23.0.1"` in `$your_project/android/app/build.gradle` dependencies on android.  
  
put files in `android/app/src/main/res/raw`
and rename file correspond to sound type:

```
incallmanager_busytone.mp3  
incallmanager_ringback.mp3  
incallmanager_ringback.mp3  
```

on android, as long as your file extension supported by android, this module will load it.


===================================================

####ios:

since ios part written in swift and it doesn't support static library yet.  
before that, you should add this project manually:

- **Add files in to your project:**

  1. Open your project in xcode
  2. find your_project directory under your project's xcodeproject root. ( it's a sub-directoory, not root xcodeproject itself )
  3. you can do either:
    * directly drag your node_modules/react-native-incall-manager/ios/RNInCallManager/ into it.
    * right click on your_project directory, `add files` to your project and add `node_modules/react-native-incall-manager/ios/RNInCallManager/`
  4. on the pou-up window, uncheck `Copy items if needed` and select `Added folders: Create groups` then add it. you will see a new directory named `RNInCallmanager under your_project` directory.

- **Setup Objective-C Bridging Header:**  
  1. click your `project's xcodeproject root`, go to `build setting` and search `Objective-C Bridging Header`
  2. set you header location, the default path is: `ReactNativeProjectRoot/ios/`, in this case, you should set `../node_modules/react-native-incall-manager/ios/RNInCallManager/RNInCallManager-Bridging-Header.h`

**optional sound files on android**
if you want to use bundled ringtone/ringback/busytone sound instead of system sound 

1. add files into your_project directory under your project's xcodeproject root. ( or drag into it as described above. )
2. check `copy file if needed`
3. make sure filename correspond to sound type:

```
incallmanager_busytone.mp3
incallmanager_ringback.mp3  
incallmanager_ringback.mp3  
```

on ios, we only support mp3 files currently.

## Usage:

This module implement a basic handle logic automatically, just:

```javascript
import InCallManager from 'react-native-incall-manager';

// --- start manager when the chat start based on logics of your app 
// On Call Established:
InCallManager.start({media: 'audio'}); // audio/video, default: audio

// ... it will also register and emit events ...

// --- On Call Hangup:
InCallManager.stop();
// ... it will also remote event listeners ...
```

if you want to use ringback:

```javascript
// ringback is basically for OUTGOING call. and is part of start().

InCallManager.start({media: 'audio', ringback: '_BUNDLE_'}); // or _DEFAULT_ or _DTMF_
//when callee answered, you MUST stop ringback explicitly:
InCallManager.stopRingback();
```

if you want to use busytone:

```javascript
// busytone is basically for OUTGOING call. and is part of stop()
// If the call failed or callee are busing,
// you may want to stop the call and play busytone
InCallManager.stop({busytone: '_DTMF_'}); // or _BUNDLE_ or _DEFAULT_
```

if you want to use ringtone:

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

also can interact with events if you want:
see API section.

```javascript
import { DeviceEventEmitter } from 'react-native';

DeviceEventEmitter.addListener('Proximity', function (data) {
    // --- do something with events
});

```

## About Permission:


since version 1.2.0, two functions and a property were added:

```javascript
// --- function
async checkRecordPermission() // return promise
async requestRecordPermission() // return promise

// --- property
recordPermission = 'unknow' or 'granted' or 'denied', default is 'unknow'
```

After incall-manager initialized, it will check current state of record permission and set to `recordPermission` property.
so you can just write below code in your `ComponentDidMount` like:

```javascript
if (InCallManager.recordPermission !== 'granted') {
    InCallManager.requestRecordPermission()
    .then((requestedRecordPermissionResult) => {
        console.log("InCallManager.requestRecordPermission() requestedRecordPermissionResult: ", requestedRecordPermissionResult);
    })
    .catch((err) => {
        console.log("InCallManager.requestRecordPermission() catch: ", err);
    });
}
```

We use android support library v4 to check/request permissions.  
You should add `compile "com.android.support:support-v4:23.0.1"` in `$your_project/android/app/build.gradle` dependencies on android.


**NOTE for android:**

React Native does not officially support api 23 currently ( it is on api 22 now. see: [RN known issues](https://facebook.github.io/react-native/docs/known-issues.html#android-m-permissions)) and android supports request permission at runtime since api 23, so it will always return 'granted' immediately after calling `checkRecordPermission()` or `requestRecordPermission()`.

If you really need the functionality, you can do the following to make them work but at your own risk:  
( I've tested it though, but who knows :) )

Step 1: change your `targetSdkVersion` to 23 in `$your_project/android/app/build.gradle`  
Step 2: override `onRequestPermissionsResult` in your `MainActivity.java` like:  

```
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        InCallManagerPackage.onRequestPermissionsResult(requestCode, permissions, grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
```

then you can test it on android 6 now.

**Another thing you should know is:**

If you change targetSdkVersion to 23, the `red box` which React Native used to display errors in development mode requires permission `Draw Over Other Apps`.  
So in **development mode**, you should manually grant permission in `app settings` on your device or declare `android.permission.SYSTEM_ALERT_WINDOW` in your manifest.  
You don't have to do this in **release mode** since there are no red box.  


checkout this awesome project: [react-native-android-permissions](https://github.com/lucasferreira/react-native-android-permissions) by @lucasferreira for more information.


## Automatic Basic Behavior:

**on start:**  
* store current settings, set KeepScreenOn flag = true, and register some event listeners.
* if media type is `audio`, route voice to earpiece, otherwise route to speaker.
* audio will enable proximity sensor which is disabled by default if media=video
* when proximity detect user closed to screen, turn off screen to avoid accident touch and route voice to earpiece.
* when newly external device plugged, such as wired-headset, route audio to external device.
* optional play ringback

**on stop:**  

* set KeepScreenOn flag = false, remote event listeners, restore original user settings.
* optional play busytone

## Custom Behavior:  

you can custom behavior use API/events exposed by this module. see `API` section.

note: ios only supports `auto` currently.

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
| async checkRecordPermission()   | :smile: | :smile: | check record permission without promt. return Promise. see **about permission** section above |
| async requestRecordPermission()   | :smile: | :smile: | request record permission to user. return Promise. see **about permission** section above |
| async getAudioUriJS()   | :smile: | :smile: | get audio Uri path. this would be useful when you want to pass Uri into another module. |

**Events**

|  Event      |  android |   ios   |  description |
|  :---  |   :---:  |  :---:  |     :---    |
| 'Proximity'   | :smile: | :smile: | proximity sensor detected changes.<br>data: `{'isNear': boolean}` |
| 'WiredHeadset'| :smile: | :smile:  | fire when wired headset plug/unplug<br>data: `{'isPlugged': boolean, 'hasMic': boolean, 'deviceName': string }` |
| 'NoisyAudio'  | :smile: | :rage: | see [andriod doc](http://developer.android.com/reference/android/media/AudioManager.html#ACTION_AUDIO_BECOMING_NOISY).<br>data: `null` |
| 'MediaButton' | :smile: | :rage: | when external device controler pressed button. see [android doc](http://developer.android.com/reference/android/content/Intent.html#ACTION_MEDIA_BUTTON) <br>data: `{'eventText': string, 'eventCode': number }` |
| 'onAudioFocusChange' | :smile: | :rage: | see [andriod doc](http://developer.android.com/reference/android/media/AudioManager.OnAudioFocusChangeListener.html#onAudioFocusChange(int)) <br>data: `{'eventText': string, 'eventCode': number }` |

**NOTE: platform OS always has the final decision, so some toggle api may not work in some case
be care when customize your own behavior**

## LICENSE:

**[ISC License](https://opensource.org/licenses/ISC)** ( functionality equivalent to **MIT License** )

## Contributing:

I'm not expert neither on ios nor android, any suggestions, pull request, corrections are really appreciated and welcome.
