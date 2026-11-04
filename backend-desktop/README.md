# :backend-desktop

JVM desktop playback backends for the workbench: `JvmAudioBackend` (16-bit PCM over `javax.sound.sampled`)
and `JvmPatternPlayer` (the `PatternPlayer` the desktop window hands to the editor). Pure JVM, depends only
on `:core`. Audible output has not been verified in this repo's CI (no audio device); the unit test uses a
fake `AudioBackend`.
