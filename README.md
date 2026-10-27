# Template Mod

The Ultimate Music Disc plays a local audio file from the jukebox. Put audio files in
`config/template-mod/music/`, right-click the disc, and rename it to the file name
(for example, `test.mp3`). It only plays in the colorful Disco Jukebox summoned by
throwing a Disco Ball; vanilla jukeboxes reject it. Right-click the Disco Jukebox with
the disc to play it, then use an empty hand to eject it. Breaking the Disco Jukebox
stops playback. Each player needs the same file in their own music folder, and the
server needs the file in its music folder for beat detection.

While a song plays, beat detections are logged in the server console. Nearby entities
squash, stretch, and settle back to normal size visually on each beat for clients using
the mod; their actual size and movement are unchanged. Non-player entities also rotate
while the song plays. Hostile mobs are temporarily pacified. These effects end when the
song finishes, the disc is removed, or the jukebox is broken.

Supported extensions: MP3, WAV, AIFF, MP4, M4A, AAC, and OGG. MP4/M4A, AAC, and OGG
files may require FFmpeg to be installed.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
