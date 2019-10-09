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
  
note: you might need `android.permission.BLUETOOTH` permisions for Bluetooth to work.
  
After install, you can use `rnpm` (`npm install rnpm -g`) to link android.  
use `react-native link react-native-incall-manager` to link or manually if you like.

We use android support library v4 to check/request permissions.  
You should add `compile "com.android.support:support-v4:$YOUR_VERSION"` in `$YOUR_PROJECT/android/app/build.gradle` dependencies on android.  

#### Manually Linking

If `react-native link` doesn't work, ( see: https://github.com/zxcpoiu/react-native-incall-manager/issues/21#issuecomment-279575516 ) please add it manually in your main project:

1. In `android/app/build.gradle`  
    Should have a line `compile(project(':react-native-incall-manager'))` in `dependencies {}` section

2. In `android/settings.gradle`  
    Should have: 
    ```
    include ':react-native-incall-manager'
    project(':react-native-incall-manager').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-incall-manager/android')
    ```
    
3. In `MainApplication.java`

    ```java
    import com.zxcpoiu.incallmanager.InCallManagerPackage;
    private static List<ReactPackage> getPackages() {
        return Arrays.<ReactPackage>asList(
            new MainReactPackage(),
            new InCallManagerPackage(),

        );
    }
    ```
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
// ... it will also remote event listeners ...
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

Also can interact with events if you want:
See API section.

```javascript
import { DeviceEventEmitter } from 'react-native';

DeviceEventEmitter.addListener('Proximity', function (data) {
    // --- do something with events
});

```

## About Permission:


Since version 1.2.0, two functions and a property were added:

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

Then you can test it on android 6 now.

**Another thing you should know is:**

If you change targetSdkVersion to 23, the `red box` which React Native used to display errors in development mode requires permission `Draw Over Other Apps`.  
So in **development mode**, you should manually grant permission in `app settings` on your device or declare `android.permission.SYSTEM_ALERT_WINDOW` in your manifest.  
You don't have to do this in **release mode** since there is no red box.  


Check out this awesome project: [react-native-android-permissions](https://github.com/lucasferreira/react-native-android-permissions) by @lucasferreira for more information.


## Automatic Basic Behavior:

**On start:**  
* Store current settings, set KeepScreenOn flag = true, and register some event listeners.
* If media type is `audio`, route voice to earpiece, otherwise route to speaker.
* Audio will enable proximity sensor which is disabled by default if media=video
* When proximity detects user close to screen, turn off screen to avoid accident touch and route voice to the earpiece.
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
| async checkRecordPermission()   | :smile: | :smile: | check record permission without promt. return Promise. see **about permission** section above |
| async requestRecordPermission()   | :smile: | :smile: | request record permission to user. return Promise. see **about permission** section above |
| async getAudioUriJS()   | :smile: | :smile: | get audio Uri path. this would be useful when you want to pass Uri into another module. |
| startRingtone(`ringtone: string, ?vibrate_pattern: array, ?ios_category: string, ?seconds: number`)   | :smile: | :smile: | play ringtone. </br>`ringtone`: '_DEFAULT_' or '_BUNDLE_'</br>`vibrate_pattern`: same as RN, but does not support repeat</br>`ios_category`: ios only, if you want to use specific audio category</br>`seconds`: android only, specify how long do you want to play rather than play once nor repeat. in sec.|
| stopRingtone()   | :smile: | :smile: | stop play ringtone if previous started via `startRingtone()` |
| stopRingback()   | :smile: | :smile: | stop play ringback if previous started via `start()` |
| setFlashOn(`enable: ?boolean, brightness: ?number`)  | :rage: | :smile: | set flash light on/off |
| async getIsWiredHeadsetPluggedIn()  | :rage: | :smile: | return wired headset plugged in state |



**Events**

|  Event      |  android |   ios   |  description |
|  :---  |   :---:  |  :---:  |     :---    |
| 'Proximity'   | :smile: | :smile: | proximity sensor detected changes.<br>data: `{'isNear': boolean}` |
| 'WiredHeadset'| :smile: | :smile:  | fire when wired headset plug/unplug<br>data: `{'isPlugged': boolean, 'hasMic': boolean, 'deviceName': string }` |
| 'NoisyAudio'  | :smile: | :rage: | see [andriod doc](http://developer.android.com/reference/android/media/AudioManager.html#ACTION_AUDIO_BECOMING_NOISY).<br>data: `null` |
| 'MediaButton' | :smile: | :rage: | when external device controler pressed button. see [android doc](http://developer.android.com/reference/android/content/Intent.html#ACTION_MEDIA_BUTTON) <br>data: `{'eventText': string, 'eventCode': number }` |
| 'onAudioFocusChange' | :smile: | :rage: | see [andriod doc](http://developer.android.com/reference/android/media/AudioManager.OnAudioFocusChangeListener.html#onAudioFocusChange(int)) <br>data: `{'eventText': string, 'eventCode': number }` |

**NOTE: platform OS always has the final decision, so some toggle API may not work in some cases
be careful when customizing your own behavior**

## LICENSE:

**[ISC License](https://opensource.org/licenses/ISC)** ( functionality equivalent to **MIT License** )

## Original Author:
[![zxcpoiu](https://github.com/zxcpoiu.png)](https://github.com/zxcpoiu)
