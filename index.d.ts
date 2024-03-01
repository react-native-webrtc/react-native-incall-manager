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

  setForceSpeakerphoneOn(flag: boolean): void;

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

  chooseAudioRoute(route: string): Promise<any>;

  requestAudioFocus(): Promise<any>;

  abandonAudioFocus(): Promise<any>;
}

declare const inCallManager: InCallManager;
export default inCallManager;
