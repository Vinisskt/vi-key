# vi-key

A Vim-flavored fork of [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard),
"the keyboard that takes the keyboard to another place" — bringing modal editing,
a command line, and scripting to a mobile coding keyboard.

Built for Termux and everyday Android use. No ads, open source.

## What makes vi-key different

Unexpected Keyboard is already the best keyboard for Termux programmers. vi-key
keeps that DNA and adds a whole Vim engine on top:

- **Vim mode** built-in: `NORMAL` / `INSERT` / `SEARCH` / `CMD` driven by the
  keyboard itself, not by the target app.
  - Modal colors: NORMAL (green), INSERT (blue), SEARCH (orange), CMD (red).
  - `esc` or `jk` gets you back to NORMAL mode from INSERT.
  - `h j k l w b e 0 $ gg G x d u ctrl+r n N /` — movement, delete, undo/redo,
    search. Counts work too (`3j`, `2dd`).
  - `o` / `O` opens a new line below/above and enters INSERT.
- **Command line `:`** — `:copy`, `:paste`, `:goto N`, `:upper`, `:lower`,
  `:title`, `:undo`, `:redo`.
- **Lua scripting** — `:addlua <name>` saves a Lua macro from the clipboard,
  `:ls` lists them, any Lua script becomes a `:` command. A `vim.*` API lets
  scripts read and edit the buffer.
- **Help built-in** — `?` in NORMAL mode opens a help page with every shortcut
  and command.
- **Floating browser** — `:float <url>` (or `:browser` / `:br`) opens a
  full-screen WebView above the keyboard, so you can Google/moderate while
  staying in the keyboard. `esc` closes it and drops you back where you were.
- **Quick double-tap** — a fast double-tap types the key's corner symbol, great
  for programmers on a phone.
- **Gruvbox dark theme** with the Cascadia Mono NF icon font.
- Native dictionary disabled (stub), keeping Termux aarch64 builds simple;
  the suggestion bar stays empty, which is fine for a coding keyboard.

## Install / build

- Install the debug APK (package `juloo.keyboard2`) or build it yourself:

  ```sh
  ./gradlew assembleDebug
  ```

- On Termux (aarch64), set a Java/Android SDK home before building, e.g.
  `JAVA_HOME=/data/data/com.termux/files/usr ANDROID_HOME=$HOME/android-sdk`.

## Credit

vi-key is a fork of
**[Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard)** by
[Julow](https://github.com/Julow) — the lightweight, privacy-conscious keyboard
for programmers. All the underlying keyboard machinery, layouts, swipe
symbols and engineering are theirs; vi-key layers a Vim engine, Lua scripting,
a floating browser and a gruvbox theme on top, and shuts off the bits a coding
keyboard does not need.

The dictionary suggestion engine is based on
[cdict](https://github.com/Julow/cdict) by the same author.

License: GPLv3, same as the original. See [LICENSE](LICENSE).