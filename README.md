# react-native-incall-manager
Handling media-routes/sensors/events during a audio/video chat on React Native

## Purpose:
The purpose of this module is to handle actions/events during a phone call (audio/video) on `react-native`, ex:
* manage devices events like wired-headset plugged, proximity sensors and expose to javascript.
* automatically route audio to proper device based on events and platform API.
* ( not implemented yet ) toggle flash light on/off, force microphone mute

This module is desinged to work with [react-native-webrtc](https://github.com/oney/react-native-webrtc)
you can find demo here: https://github.com/oney/RCTWebRTCDemo

## Installation:

**from npm package**: `npm install react-native-incall-manager`  
**from git package**: `npm install git://github.com/zxcpoiu/react-native-incall-manager.git`  

===================================================
####android:

After install, you can use `rnpm` (`npm install rnpm -g`) to link android.  
use `rnpm link react-native-incall-manager` to link or manually if you like.

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


and interact with events if you want:
see API section.

```javascript
import { DeviceEventEmitter } from 'react-native';

DeviceEventEmitter.addListener('Proximity', function (data) {
    // --- do something with events
});

```

## Automatic Basic Behavior:

**on start:**  
* store current settings, set KeepScreenOn flag = true, and register some event listeners.
* if media type is `audio`, route voice to earpiece, otherwise route to speaker.
* when proximity detect user closed to screen, turn off screen to avoid accident touch and route voice to earpiece.
* when newly external device plugged, such as wired-headset, route audio to external device.

**on stop:**  

* set KeepScreenOn flag = false, remote event listeners, restore original user settings.

## Custom Behavior:  

you can custom behavior use API/events exposed by this module. see `API` section.

note: ios only supports `auto` currently.

## API:

**Methods**

|  Method      |  android |   ios   |  description |
|  :---  |   :---:  |  :---:  |     :---    |
| start(`{media: ?string, auto: ?boolean}`)   | :smile: | :smile: | start incall manager.</br>default: `{media:'audio', auto: true}`  |
| stop()   | :smile: | :smile: | stop incall manager |
| turnScreenOn()   | :smile: | :rage: | force turn screen on |
| turnScreenOff()   | :smile: | :rage: | force turn screen off |
| setKeepScreenOn(`enable: ?boolean`)   | :smile: | :smile: | set KeepScreenOn flag = true or false</br>default: false |
| setSpeakerphoneOn(`enable: ?boolean`)   | :smile: | :rage: | toggle speaker ON/OFF once. but not force</br>default: false |
| setForceSpeakerphoneOn(`enable: ?boolean`)   | :smile: | :smile: | if set to true, will ignore all logic and force audio route to speaker</br>default: false  |
| setMicrophoneMute(`enable: ?boolean`)   | :smile: | :rage: | mute/unmute micophone</br>default: false |

**Events**

|  Event      |  android |   ios   |  description |
|  :---  |   :---:  |  :---:  |     :---    |
| 'Proximity'   | :smile: | :smile: | proximity sensor detected changes.<br>data: `{'isNear': boolean}` |
| 'WiredHeadset'| :smile: | :rage:  | fire when wired headset plug/unplug<br>data: `{'isPlugged': boolean, 'hasMic': boolean, 'deviceName': string }` |
| 'NoisyAudio'  | :smile: | :rage: | see [andriod doc](http://developer.android.com/reference/android/media/AudioManager.html#ACTION_AUDIO_BECOMING_NOISY).<br>data: `null` |
| 'MediaButton' | :smile: | :rage: | when external device controler pressed button. see [android doc](http://developer.android.com/reference/android/content/Intent.html#ACTION_MEDIA_BUTTON) <br>data: `{'eventText': string, 'eventCode': number }` |
| 'onAudioFocusChange' | :smile: | :rage: | see [andriod doc](http://developer.android.com/reference/android/media/AudioManager.OnAudioFocusChangeListener.html#onAudioFocusChange(int)) <br>data: `{'eventText': string, 'eventCode': number }` |

**NOTE: platform OS always has the final decision, so some toggle api may not work in some case
be care when customize your own behavior**

## LICENSE:

**[ICS License](https://opensource.org/licenses/ISC)** ( functionality equivalent to **MIT License** )

## Contributing:

I'm not expert neither on ios nor android, any suggestions, pull request, corrections are really appreciated and welcome.
