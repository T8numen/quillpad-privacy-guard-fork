# History

## 1.5.32 (74)

- Ignored local Gradle cache files so build output does not pollute Git status or commits.
- Kept Markdown image picker insertion anchored to the selection used when opening the picker.
- Preserved coroutine cancellation while copying Markdown images into private media storage.

## 1.5.31 (73)

- Copied Markdown images selected from the system image picker into the app's private media storage before inserting them into notes.
- Preserved private Markdown image files during media storage cleanup.

## 1.5.30 (72)

- Enabled secure window protection for private note screens so Android screenshots and recent-app thumbnails do not expose private content.

## 1.5.29 (71)

- Added an "Edit bottom toolbar" setting under Other to reorder editor toolbar buttons and show or hide individual tools.

## 1.5.28 (70)

- Added an image picker option for Markdown image insertion, including a Choose button in the insert image dialog.
- Added an "Insert image default" setting under Other so image insertion can default to either the image picker or manual path input.

## 1.5.27 (69)

- Changed privacy return after backgrounding to run immediately instead of waiting 15 seconds.
- Changed the hidden "Other" drawer entry so it stays visually unchanged, opens the About website as a decoy after a short delay, and still accepts the hidden entry code briefly to enter the privacy page.

## 1.5.26 (68)

- Tightened the privacy return cover so it is removed only after the app is back on the default notes page with privacy mode disabled.

## 1.5.25 (67)

- Fixed privacy page return cleanup so lock-screen and background timeout recovery no longer visibly steps through the private editor or private home page.
- Added a temporary window cover while private content is being saved and the app returns to the default notes page.
