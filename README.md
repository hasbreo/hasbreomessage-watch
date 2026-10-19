# Hasbreo Message · Watch

A Wear OS companion for [Hasbreo Message Android](https://github.com/hasbreo/hasbreomessage-phone).

**[Downloads and setup](https://hasbreo.com/message)** · **[Releases](https://github.com/hasbreo/hasbreomessage-watch/releases)**

Recent chats, readable threads, dictation, keyboard replies, editable quick replies, long-press reactions, read controls, chat muting and open-on-phone requests. Saved chat data and the reply outbox use Android Keystore-backed encryption. Replies stay labelled as waiting until the phone acknowledges them, and stable transaction IDs prevent retries from creating duplicate messages.

## Install and pair

Wear OS 3+ with Google Play services and a paired Android phone. Install the phone app first and sign in. Install the watch APK using Wear Installer 2 or ADB, then approve the watch in phone Settings → Connect your watch. Open the watch app and tap Connect/Refresh. Enable Connected mode on the phone for background replies.

The watch has **no independent Matrix login or server connection**. Recent saved messages can be read offline and replies can wait in the outbox; new messages and sending require the phone. Passwords, access tokens and Matrix encryption keys never leave the phone. Only approved, same-package, same-certificate companions can request chat data. The watch is an authorized endpoint for its displayed, decrypted messages.

A phone account change clears connected-watch data and revokes its approval. An offline watch can retain its old encrypted cache until reconnection; use Quick replies & privacy → Clear saved chats or Forget phone to remove it locally. Disconnect approved watches from the phone to revoke access.

## Build

JDK 17 and Android SDK platform 36 / build tools 36.0.0:

```bash
./gradlew :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

Both phone and watch APKs deliberately use `com.hasbreo.message` and **must be signed with the same release certificate** for Google's Wear Data Layer. Do not install the phone APK on the watch or the watch APK on the phone.

The first signed release is published from a compressed bootstrap artifact with a pinned checksum/certificate. Its private key is backed up separately and is never committed. CI validates unsigned builds unless optional secrets `MESSAGE_KEYSTORE_BASE64`, `MESSAGE_KEYSTORE_PASSWORD` and `MESSAGE_KEY_PASSWORD` are configured; alias `hasbreo-message`. Tagged releases require a signed artifact. The downloads page updates from GitHub releases and keeps older versions.

The UI uses native Android views and a charcoal/sage palette. No WebView, server credential store, microphone recorder, continuous polling service or location permission is used on the watch. Dictation is delegated to the watch's speech-input app. Google Play services and the phone handle the paired-device transport.

Initial releases are previews. Automated Android checks and account/retry-policy tests pass, but physical watch/phone pairing, dictation, round-screen layout, battery use and real-device notification behaviour still need hardware verification. No standalone watch networking, calls or media streaming are included.

Project code: MIT. AndroidX: Apache-2.0. Google Play services uses Google's SDK terms.
