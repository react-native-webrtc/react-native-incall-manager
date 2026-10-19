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
import InCallManager from 'react-native-incall-manager';

const subscription = InCallManager.addListener('Proximity', function (data) {
    // --- do something with events
});

// --- later
subscription.remove();
```

Use `InCallManager.addListener()` rather than `DeviceEventEmitter.addListener()`. On iOS the
module sends events only while a `NativeEventEmitter` built on it has listeners, so a
`DeviceEventEmitter` listener there hears nothing unless some other code happens to hold such a
listener. `addListener()` does the right thing on each platform, and on iOS it returns a
subscription that never fires for the Android-only events (`NoisyAudio`, `MediaButton`,
`onAudioFocusChange`).



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
| async getAudioUriJS()   | :smile: | :smile: | get audio Uri path. this would be useful when you want to pass Uri into another module. |
| startRingtone(`ringtone: string, ?vibrate_pattern: array, ?ios_category: string, ?seconds: number`)   | :smile: | :smile: | play ringtone. </br>`ringtone`: '_DEFAULT_' or '_BUNDLE_'</br>`vibrate_pattern`: same as RN, but does not support repeat</br>`ios_category`: ios only, if you want to use specific audio category</br>`seconds`: android only, specify how long do you want to play rather than play once nor repeat. in sec.|
| stopRingtone()   | :smile: | :smile: | stop play ringtone if previous started via `startRingtone()` |
| stopRingback()   | :smile: | :smile: | stop play ringback if previous started via `start()` |
| setFlashOn(`enable: ?boolean, brightness: ?number`)  | :rage: | :smile: | set flash light on/off |
| async getIsWiredHeadsetPluggedIn()  | :rage: | :smile: | return wired headset plugged in state (a USB-C headset counts; a USB-C microphone alone doesn't) |
| async chooseAudioRoute(`route: string`)  | :smile: | :smile: | route the call audio to `'EARPIECE'`, `'SPEAKER_PHONE'`, `'WIRED_HEADSET'` or `'BLUETOOTH'`.</br>resolves with the same data as `'onAudioDeviceChanged'`</br>ios: between `start()` and `stop()` only (rejects `E_AUDIO_ROUTE_INACTIVE` otherwise); resolves once the output is on the chosen route, or after a second with the route iOS settled on; rejects `E_AUDIO_ROUTE_UNAVAILABLE`, leaving the route as it was, when the route is not available (with the speaker forced on and carrying the call, looking for mic-less headphones or an A2DP device lifts the speaker override for a moment); rejects `E_AUDIO_ROUTE` when AVAudioSession refuses the choice, with the speaker setting put back as it was.</br>ios: `'EARPIECE'` is not available while a wired or USB headset is plugged in, mic-less headphones under a forced speaker included (iOS keeps the output on the headset). With two devices of one route (a car and AirPods), the one carrying the call is kept. `'BLUETOOTH'` includes car audio and A2DP devices, but an A2DP-only device (no microphone) can carry the call only while the session's category allows A2DP output (`AllowBluetoothA2DP`, which callkeep sets and `start()` doesn't); a USB-C headset counts as `'WIRED_HEADSET'`. `start()` clears any preferred input, so a device chosen earlier doesn't hold the call, and `stop()` drops the choice and puts back the preferred input the app had before `start()`.</br>android: `'EARPIECE'` stays listed, and a choice of it is reported as made, while a wired headset is plugged in (the audio stays on the headset); `start()` clears a choice, `stop()` doesn't (it lasts until the next `start()`). |
| async getAudioDeviceStatus()  | :rage: | :smile: | return the current audio routes, same data as `'onAudioDeviceChanged'` |
| addListener(`event: string, listener: function`)  | :smile: | :smile: | subscribe to one of the events below; returns a subscription with `remove()` (on ios, one that never fires for the android-only events) |



**Events**

|  Event      |  android |   ios   |  description |
|  :---  |   :---:  |  :---:  |     :---    |
| 'Proximity'   | :smile: | :smile: | proximity sensor detected changes.<br>data: `{'isNear': boolean}` |
| 'WiredHeadset'| :smile: | :smile:  | fire when wired headset plug/unplug<br>data: `{'isPlugged': boolean, 'hasMic': boolean, 'deviceName': string }`<br>ios: a USB-C headset counts; `isPlugged: false` is sent only when a wired headset leaves (4.3.0 also sent it whenever another device, such as AirPods or a car, left with no wired headset plugged in) |
| 'NoisyAudio'  | :smile: | :rage: | see [android doc](http://developer.android.com/reference/android/media/AudioManager.html#ACTION_AUDIO_BECOMING_NOISY).<br>data: `null` |
| 'MediaButton' | :smile: | :rage: | when external device controler pressed button. see [android doc](http://developer.android.com/reference/android/content/Intent.html#ACTION_MEDIA_BUTTON) <br>data: `{'eventText': string, 'eventCode': number }` |
| 'onAudioDeviceChanged' | :smile: | :smile: | fire when the available audio routes or the selected one change<br>data: `{'availableAudioDeviceList': string, 'selectedAudioDevice': string, 'selectedAudioDeviceName': string }`<br>`availableAudioDeviceList` is a JSON array of route names, as `chooseAudioRoute()` takes<br>`selectedAudioDeviceName`: ios only, the name of the output in use<br>ios: fires between `start()` and `stop()`, once per settled change (after the module re-applies a forced speaker), to listeners added with `addListener()`; `EARPIECE` is not listed while a wired or USB headset is plugged in |
| 'onAudioFocusChange' | :smile: | :rage: | see [android doc](http://developer.android.com/reference/android/media/AudioManager.OnAudioFocusChangeListener.html#onAudioFocusChange(int)) <br>data: `{'eventText': string, 'eventCode': number }` |

**NOTE: platform OS always has the final decision, so some toggle API may not work in some cases
be careful when customizing your own behavior**

**iOS: following route changes made elsewhere.** The module isn't the only writer of the audio
session: CallKit (through react-native-callkeep) and WebRTC set its category and mode too. While
started, the module re-applies its own setup after each of their category changes (the
category, the call's mode, voice chat for an audio call, and a forced speaker), and keeps its
forced speaker setting (`setForceSpeakerphoneOn` / `chooseAudioRoute`) in step with route
changes it didn't make, so the re-apply keeps them instead of undoing them: a headset, USB
headset or car taking the call puts the setting back to the media's default and drops an
earlier choice's preferred input, and so does an output outside the four routes (HDMI, line
out) taking the call from a forced speaker; the system route picker (`AVRoutePickerView`) or CallKit's
speaker button choosing the speaker, the receiver or a device sets it when iOS reports the change
with reason `Override`, `Unknown`, `RouteConfigurationChange` or `CategoryChange`. On iOS 27 the
picker's "iPhone" and CallKit's speaker-off arrive as a `CategoryChange` that leaves the session's
category, mode and options as they were, so the module follows it as the user's choice; a change
that finds the session set up differently (someone else configured it) has the call's route
re-applied instead. The `RNInCallManager.AudioRouteChange.Reason` log line shows which reason iOS
posted. While the call's
session is interrupted (on hold for another call), route changes aren't followed: they are the
other call's. While CallKit has the session deactivated between two calls (End & Accept), iOS 27
reports a route with no input, on the speaker: it belongs to neither call, so it is neither followed
nor reported (`onAudioDeviceChanged`), and the change that brings an input back has the call's
route re-applied; an available-inputs change that finds the session set up by someone else is
reported only after the route change that comes with it. After a media services reset the module sets up the call's session, chosen device
and speaker setting again, and restarts a ringback or busy tone that was playing.

## LICENSE:

**[ISC License](https://opensource.org/licenses/ISC)** ( functionality equivalent to **MIT License** )

## Original Author:
[![zxcpoiu](https://github.com/zxcpoiu.png)](https://github.com/zxcpoiu)
