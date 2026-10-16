/** URIs getAudioUri() has resolved, by fileType ('_BUNDLE_', '_DEFAULT_' or a file name). */
type AudioUriCache = {
  _BUNDLE_: string | null;
  _DEFAULT_: string | null;
  [fileType: string]: string | null;
};

declare class InCallManager {
  vibrate: boolean;
  audioUriMap: {
    ringtone: AudioUriCache;
    ringback: AudioUriCache;
    busytone: AudioUriCache;
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

  setForceSpeakerphoneOn(flag: boolean): void;

  setMicrophoneMute(enable: boolean): void;

  /**
   * @param ringtone '_DEFAULT_' (default), '_BUNDLE_' or a system file name with extension
   * @param vibrate_pattern a pattern in ms, as for RN's Vibration (no repeat); a single number does not vibrate
   * @param ios_category iOS only: 'playback' plays in the Playback category, anything else in SoloAmbient
   * @param seconds Android only: stop after this many seconds (default: loop until stopRingtone())
   */
  startRingtone(
    ringtone?: string,
    vibrate_pattern?: number[],
    ios_category?: string,
    seconds?: number
  ): void;

  stopRingtone(): void;

  startProximitySensor(): void;

  stopProximitySensor(): void;

  startRingback(ringback: string): void;

  stopRingback(): void;

  pokeScreen(timeout: number): void;

  getAudioUri(audioType: string, fileType: string): Promise<string | null>;

  chooseAudioRoute(route: string): Promise<any>;

  requestAudioFocus(): Promise<any>;

  abandonAudioFocus(): Promise<any>;
}

declare const inCallManager: InCallManager;
export default inCallManager;
