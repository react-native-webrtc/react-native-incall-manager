export type AudioRoute = "EARPIECE" | "SPEAKER_PHONE" | "WIRED_HEADSET" | "BLUETOOTH";

/** What `chooseAudioRoute()` resolves with and `onAudioDeviceChanged` carries. */
export interface AudioDeviceStatus {
  /** JSON array of the available routes, e.g. '["EARPIECE","SPEAKER_PHONE","BLUETOOTH"]'. */
  availableAudioDeviceList: string;
  /** The route in use, or "" when none is (Android may also send "NONE"). */
  selectedAudioDevice: AudioRoute | "NONE" | "";
  /** iOS only: the name of the output in use, e.g. "AirPods Pro". */
  selectedAudioDeviceName?: string;
}

declare class InCallManager {
  vibrate: boolean;
  audioUriMap: {
    ringtone: { _BUNDLE_: null; _DEFAULT_: null };
    ringback: { _BUNDLE_: null; _DEFAULT_: null };
    busytone: { _BUNDLE_: null; _DEFAULT_: null };
  };

  constructor();

  start(setup?: {
    auto?: boolean;
    media?: "video" | "audio";
    ringback?: string;
  }): void;

  stop(setup?: { busytone?: string }): void;

  turnScreenOff(): void;

  turnScreenOn(): void;

  getIsWiredHeadsetPluggedIn(): Promise<{ isWiredHeadsetPluggedIn: boolean }>;

  setFlashOn(enable: boolean, brightness: number): void;

  setKeepScreenOn(enable: boolean): void;

  setSpeakerphoneOn(enable: boolean): void;

  /**
   * true forces the speaker on; null uses the media type's default (the speaker for video).
   * false, on iOS: speaker off (the receiver, or a connected headset or car keeps the call).
   * false, on Android: the earpiece, also over a connected Bluetooth or wired headset; on a device
   * without one (no telephony) it changes nothing.
   */
  setForceSpeakerphoneOn(flag: boolean | null): void;

  setMicrophoneMute(enable: boolean): void;

  startRingtone(
    ringtone: string,
    vibrate_pattern: number | number[],
    ios_category: string,
    seconds: number
  ): void;

  stopRingtone(): void;

  startProximitySensor(): void;

  stopProximitySensor(): void;

  startRingback(ringback: string): void;

  stopRingback(): void;

  pokeScreen(timeout: number): void;

  getAudioUri(audioType: string, fileType: string): Promise<string | null>;

  /**
   * A route name; a plain string is accepted too, as before routes were typed. Resolves with an
   * `AudioDeviceStatus` (typed `any`, as in 4.3.0, so existing callers keep compiling).
   * iOS: rejects E_AUDIO_ROUTE_INACTIVE outside start()...stop(). Resolves once the output is on
   * the chosen route, or after a second with the route iOS settled on. Rejects
   * E_AUDIO_ROUTE_UNAVAILABLE (route unchanged) for a route that isn't there, EARPIECE included
   * while a wired or USB headset is plugged in, and E_AUDIO_ROUTE when AVAudioSession refuses the
   * choice (the speaker setting is put back as it was).
   */
  chooseAudioRoute(route: AudioRoute | (string & {})): Promise<any>;

  /** iOS only. */
  getAudioDeviceStatus(): Promise<AudioDeviceStatus>;

  /**
   * Subscribes to one of this module's events. Use this rather than
   * DeviceEventEmitter: on iOS the native side sends only to a NativeEventEmitter.
   * On iOS the Android-only events get a subscription that never fires.
   */
  addListener(event: "onAudioDeviceChanged", listener: (status: AudioDeviceStatus) => void): { remove(): void };
  addListener(event: "Proximity", listener: (data: { isNear: boolean }) => void): { remove(): void };
  addListener(
    event: "WiredHeadset",
    listener: (data: { isPlugged: boolean; hasMic: boolean; deviceName: string }) => void
  ): { remove(): void };
  addListener(event: string, listener: (data: any) => void): { remove(): void };

  requestAudioFocus(): Promise<any>;

  abandonAudioFocus(): Promise<any>;
}

declare const inCallManager: InCallManager;
export default inCallManager;
