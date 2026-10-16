'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

function loadManager(platform, nativeModule, vibration) {
    const filename = path.join(__dirname, '..', 'index.js');
    let source = fs.readFileSync(filename, 'utf8');
    source = source.replace(
        /import \{\s*Platform,\s*Vibration,\s*\} from 'react-native';/,
        "const { Platform, Vibration } = require('react-native');"
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

    {
        // A vibration pattern with a non-number element is not forwarded:
        // ReadableArray.getDouble() throws on it on the native-modules thread.
        const calls = [];
        const nativeModule = {
            startRingtoneWithVibration(...args) { calls.push(args); },
            stopRingtone() {},
        };
        const vibration = {
            vibrate() { throw new Error('new Android native must own vibration'); },
            cancel() {},
        };
        const manager = loadManager('android', nativeModule, vibration);

        manager.startRingtone('_DEFAULT_', ['200', 300], 'default', 3);
        manager.startRingtone('_DEFAULT_', [0, NaN], 'default', 3);
        // A fraction of a second must not become 0 (= no timeout, ring forever).
        manager.startRingtone('_DEFAULT_', [0, 100], 'default', 0.5);

        assert.deepStrictEqual(calls, [
            ['_DEFAULT_', 3, null],
            ['_DEFAULT_', 3, null],
            ['_DEFAULT_', 1, [0, 100]],
        ]);
    }

    {
        // Legacy native path: the same patterns are not handed to Vibration either.
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

        manager.startRingtone('_DEFAULT_', [0, '100'], 'default');
        manager.stopRingtone();

        assert.deepStrictEqual(calls, [
            ['start', '_DEFAULT_', 'default'],
            ['stop'],
        ]);
    }

    console.log('index.js wrapper tests passed');
}

async function runAsync() {
    {
        // chooseAudioRoute() needs a route name; native would get null.
        const calls = [];
        const nativeModule = {
            chooseAudioRoute(route) { calls.push(route); return Promise.resolve({}); },
        };
        const manager = loadManager('android', nativeModule, {});

        await assert.rejects(manager.chooseAudioRoute(), { name: 'TypeError' });
        await assert.rejects(manager.chooseAudioRoute(null), { name: 'TypeError' });
        await manager.chooseAudioRoute('SPEAKER_PHONE');
        assert.deepStrictEqual(calls, ['SPEAKER_PHONE']);
    }

    {
        // getAudioUri(audioType) without a fileType asks for the default sound.
        const calls = [];
        const nativeModule = {
            getAudioUriJS(audioType, fileType) {
                calls.push([audioType, fileType]);
                return Promise.resolve('file:///default');
            },
        };
        const manager = loadManager('android', nativeModule, {});

        assert.strictEqual(await manager.getAudioUri('ringtone'), 'file:///default');
        assert.strictEqual(await manager.getAudioUri('ringtone', '_DEFAULT_'), 'file:///default');
        assert.strictEqual(await manager.getAudioUri('nope'), null);
        assert.deepStrictEqual(calls, [['ringtone', '_DEFAULT_']]);
    }

    console.log('index.js async wrapper tests passed');
}

run();
runAsync().catch((err) => {
    console.error(err);
    process.exit(1);
});
