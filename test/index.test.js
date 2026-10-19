'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

function loadManager(platform, nativeModule, vibration, emitters = {}) {
    const filename = path.join(__dirname, '..', 'index.js');
    let source = fs.readFileSync(filename, 'utf8');
    source = source.replace(
        /import \{([^}]*)\} from 'react-native';/,
        (_, names) => `const {${names}} = require('react-native');`
    );
    source = source.replace(
        'export default new InCallManager();',
        'module.exports = new InCallManager();'
    );

    const module = { exports: {} };
    const context = {
        console,
        module,
        exports: module.exports,
        require(name) {
            assert.strictEqual(name, 'react-native');
            return {
                NativeModules: { InCallManager: nativeModule },
                Platform: { OS: platform },
                Vibration: vibration,
                NativeEventEmitter: emitters.NativeEventEmitter,
                DeviceEventEmitter: emitters.DeviceEventEmitter,
            };
        },
    };
    vm.runInNewContext(source, context, { filename });
    return module.exports;
}

function run() {
    {
        const calls = [];
        const nativeModule = {
            startRingtoneWithVibration(...args) { calls.push(args); },
            stopRingtone() { calls.push(['stop']); },
        };
        const vibration = {
            vibrate() { throw new Error('new Android native must own vibration'); },
            cancel() { throw new Error('new Android native must own cancellation'); },
        };
        const manager = loadManager('android', nativeModule, vibration);

        manager.startRingtone('_DEFAULT_', [0, 250], 'default', 3);
        manager.stopRingtone();

        assert.deepStrictEqual(calls[0], ['_DEFAULT_', 3, [0, 250]]);
        assert.deepStrictEqual(calls[1], ['stop']);
    }

    {
        const calls = [];
        const nativeModule = {
            startRingtone(...args) { calls.push(['start', ...args]); },
            stopRingtone() { calls.push(['stop']); },
        };
        const vibration = {
            vibrate(...args) { calls.push(['vibrate', ...args]); },
            cancel() { calls.push(['cancel']); },
        };
        const manager = loadManager('android', nativeModule, vibration);

        manager.startRingtone('_BUNDLE_', [0, 100], 'default', 2);
        manager.stopRingtone();

        assert.deepStrictEqual(calls, [
            ['start', '_BUNDLE_', 2],
            ['vibrate', [0, 100], false],
            ['cancel'],
            ['stop'],
        ]);
    }

    {
        const calls = [];
        const nativeModule = {
            startRingtone(...args) { calls.push(['start', ...args]); },
            stopRingtone() { calls.push(['stop']); },
        };
        const vibration = {
            vibrate(...args) { calls.push(['vibrate', ...args]); },
            cancel() { calls.push(['cancel']); },
        };
        const manager = loadManager('ios', nativeModule, vibration);

        manager.startRingtone('_DEFAULT_', [0, 150], 'playback', 9);
        manager.stopRingtone();

        assert.deepStrictEqual(calls, [
            ['start', '_DEFAULT_', 'playback'],
            ['vibrate', [0, 150], false],
            ['cancel'],
            ['stop'],
        ]);
    }

}

async function runAudioRoutes() {
    const status = {
        availableAudioDeviceList: '["EARPIECE","SPEAKER_PHONE","BLUETOOTH"]',
        selectedAudioDevice: 'BLUETOOTH',
        selectedAudioDeviceName: 'Headset',
    };
    const vibration = { vibrate() {}, cancel() {} };

    {
        const calls = [];
        const nativeModule = {
            chooseAudioRoute(...args) { calls.push(['choose', ...args]); return Promise.resolve(status); },
            getAudioDeviceStatus() { calls.push(['status']); return Promise.resolve(status); },
        };
        const manager = loadManager('ios', nativeModule, vibration);

        assert.deepStrictEqual(await manager.chooseAudioRoute('BLUETOOTH'), status);
        assert.deepStrictEqual(await manager.getAudioDeviceStatus(), status);
        assert.deepStrictEqual(calls, [['choose', 'BLUETOOTH'], ['status']]);
    }

    {
        const nativeModule = {
            chooseAudioRoute() { return Promise.reject(new Error('EARPIECE is not available')); },
        };
        const manager = loadManager('ios', nativeModule, vibration);

        await assert.rejects(manager.chooseAudioRoute('EARPIECE'), /EARPIECE is not available/);
    }

    {
        const nativeModule = {
            getAudioDeviceStatus() { throw new Error('android native has no getAudioDeviceStatus'); },
        };
        const manager = loadManager('android', nativeModule, vibration);

        await assert.rejects(manager.getAudioDeviceStatus(), /android doesn't support getAudioDeviceStatus/);
    }
}

// What RNInCallManager.m's supportedEvents returns (and exports as the supportedEvents constant).
const IOS_NATIVE_EVENTS = ['Proximity', 'WiredHeadset', 'onAudioDeviceChanged'];

async function runListeners() {
    const vibration = { vibrate() {}, cancel() {} };

    {
        // iOS: RCTEventEmitter only sends while a NativeEventEmitter built on this module has a
        // listener; DeviceEventEmitter.addListener alone never reaches the native side.
        const made = [];
        class NativeEventEmitter {
            constructor(module) { this.module = module; made.push(this); this.added = []; }
            addListener(event, listener) { this.added.push(event); return { remove() {}, event, listener }; }
        }
        const nativeModule = { supportedEvents: IOS_NATIVE_EVENTS };
        const deviceEventEmitter = { addListener() { throw new Error('ios must not use DeviceEventEmitter'); } };
        const manager = loadManager('ios', nativeModule, vibration, { NativeEventEmitter, DeviceEventEmitter: deviceEventEmitter });

        const listener = () => {};
        const first = manager.addListener('onAudioDeviceChanged', listener);
        manager.addListener('Proximity', listener);

        assert.strictEqual(made.length, 1);
        assert.strictEqual(made[0].module, nativeModule);
        assert.deepStrictEqual(made[0].added, ['onAudioDeviceChanged', 'Proximity']);
        assert.strictEqual(first.listener, listener);
        assert.strictEqual(typeof first.remove, 'function');
    }

    {
        // iOS: an event the iOS module never sends (Android-only: NoisyAudio, MediaButton,
        // onAudioFocusChange) must not reach NativeEventEmitter, whose native addListener:
        // RCTLogError's (a RedBox in debug) for a name outside supportedEvents.
        const made = [];
        class NativeEventEmitter {
            constructor() { this.added = []; made.push(this); }
            addListener(event) { this.added.push(event); return { remove() {} }; }
        }
        const manager = loadManager('ios', { supportedEvents: IOS_NATIVE_EVENTS }, vibration, { NativeEventEmitter, DeviceEventEmitter: {} });

        for (const event of ['NoisyAudio', 'MediaButton', 'onAudioFocusChange']) {
            const subscription = manager.addListener(event, () => {});
            assert.strictEqual(typeof subscription.remove, 'function');
            subscription.remove();
        }
        manager.addListener('WiredHeadset', () => {});
        assert.deepStrictEqual(made.flatMap((emitter) => emitter.added), ['WiredHeadset']);
    }

    {
        // iOS: which events exist comes from the native module (its supportedEvents, exported as a
        // constant), not a copy in index.js: an event added natively is subscribed, one the module
        // doesn't send isn't.
        const made = [];
        class NativeEventEmitter {
            constructor() { this.added = []; made.push(this); }
            addListener(event) { this.added.push(event); return { remove() {} }; }
        }
        const nativeModule = { supportedEvents: ['Proximity', 'onRouteNews'] };
        const manager = loadManager('ios', nativeModule, vibration, { NativeEventEmitter, DeviceEventEmitter: {} });

        manager.addListener('onRouteNews', () => {});
        manager.addListener('WiredHeadset', () => {});
        assert.deepStrictEqual(made.flatMap((emitter) => emitter.added), ['onRouteNews']);
    }

    {
        // Android sends through RCTDeviceEventEmitter, which DeviceEventEmitter listens to.
        const added = [];
        const deviceEventEmitter = { addListener(event) { added.push(event); return { remove() {} }; } };
        class NativeEventEmitter { constructor() { throw new Error('android uses DeviceEventEmitter'); } }
        const manager = loadManager('android', {}, vibration, { NativeEventEmitter, DeviceEventEmitter: deviceEventEmitter });

        manager.addListener('onAudioDeviceChanged', () => {});
        assert.deepStrictEqual(added, ['onAudioDeviceChanged']);
    }
}

// The typings and README: 4.3.0 declared chooseAudioRoute(): Promise<any>, and narrowing it in a
// minor release breaks callers that assign the result loosely; selectedAudioDevice is "" on both
// platforms when nothing is selected (Android sends "" for a null device); a DeviceEventEmitter
// listener on iOS does hear events while any NativeEventEmitter listener exists.
function runTypings() {
    const dts = fs.readFileSync(path.join(__dirname, '..', 'index.d.ts'), 'utf8');
    assert.match(dts, /chooseAudioRoute\(route: AudioRoute \| \(string & \{\}\)\): Promise<any>;/);
    assert.doesNotMatch(dts, /"NONE" on Android when none is selected/);
    const readme = fs.readFileSync(path.join(__dirname, '..', 'README.md'), 'utf8');
    assert.doesNotMatch(readme, /never hears anything/);
}

// chooseAudioRoute's iOS rejection codes: the native method rejects E_AUDIO_ROUTE_INACTIVE outside
// start()...stop(), E_AUDIO_ROUTE_UNAVAILABLE for a route that isn't there and E_AUDIO_ROUTE when
// AVAudioSession refuses; the typings and the README name all three.
function runChooseAudioRouteErrorDocs() {
    const dts = fs.readFileSync(path.join(__dirname, '..', 'index.d.ts'), 'utf8');
    const readme = fs.readFileSync(path.join(__dirname, '..', 'README.md'), 'utf8');
    const chooseDoc = dts.slice(dts.lastIndexOf('/**', dts.indexOf('chooseAudioRoute(route')), dts.indexOf('chooseAudioRoute(route'));
    const chooseRow = readme.split('\n').find((line) => line.startsWith('| async chooseAudioRoute('));
    for (const code of ['E_AUDIO_ROUTE_INACTIVE', 'E_AUDIO_ROUTE_UNAVAILABLE', 'E_AUDIO_ROUTE']) {
        const word = new RegExp(code + '(?![_A-Z])');
        assert.match(chooseDoc, word, `index.d.ts chooseAudioRoute names ${code}`);
        assert.match(chooseRow, word, `README chooseAudioRoute row names ${code}`);
    }
}

// The iOS route rules are scoped to iOS: on Android EARPIECE stays listed with a wired headset,
// stop() keeps a choice, and setForceSpeakerphoneOn(false) picks the earpiece over a headset.
function runPlatformScopedDocs() {
    const dts = fs.readFileSync(path.join(__dirname, '..', 'index.d.ts'), 'utf8');
    const readme = fs.readFileSync(path.join(__dirname, '..', 'README.md'), 'utf8');
    const forceDoc = dts.slice(dts.lastIndexOf('/**', dts.indexOf('setForceSpeakerphoneOn(flag')), dts.indexOf('setForceSpeakerphoneOn(flag'));
    assert.match(forceDoc, /iOS/, 'index.d.ts setForceSpeakerphoneOn says what false does on iOS');
    assert.match(forceDoc, /Android/, 'index.d.ts setForceSpeakerphoneOn says what false does on Android');
    const chooseRow = readme.split('\n').find((line) => line.startsWith('| async chooseAudioRoute('));
    assert.match(chooseRow, /android: /, 'README chooseAudioRoute row has an android: part');
    assert.doesNotMatch(chooseRow, /<\/br>[^<]*\. `'EARPIECE'` is not available/, "the EARPIECE rule isn't a general sentence");
}

run();
runTypings();
runChooseAudioRouteErrorDocs();
runPlatformScopedDocs();
Promise.all([runAudioRoutes(), runListeners()]).then(() => {
    console.log('index.js wrapper tests passed');
}, (err) => {
    console.error(err);
    process.exitCode = 1;
});
