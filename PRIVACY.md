# Privacy

Translate translates on the phone. The text you give it, typed, shared, selected in another
app or read from a picture, is never sent anywhere.

The app goes online for one thing only, and only when you ask: to download a language. That is
a download; nothing goes up, and once a language is on the phone, translating it never opens a
connection.

That is the whole policy. The rest of this page is the evidence for it, because a privacy
policy that cannot be checked is just a promise.

## One permission

`app/src/main/AndroidManifest.xml` declares exactly one:

```
android.permission.INTERNET
```

**It is there for downloading languages alone.** Pressing a language on the languages screen
fetches its files from two places, at addresses written in the app
(`app/src/main/assets/catalog.json`):

- the translation models from Mozilla's store, the one Firefox itself downloads its
  translation models from, `storage.googleapis.com/moz-fx-translations-data--303e-prod-translations-data`;
- the files for reading pictures from Tesseract's own repository on GitHub,
  `raw.githubusercontent.com/tesseract-ocr/tessdata_fast`, pinned to one commit.

The requests carry nothing of yours: they name a file and nothing else. Those servers, like any
server, see the phone's IP address when it asks. Mozilla (whose store runs on Google Cloud
Storage) and GitHub are separate from this app and from its developer; what they see of a
download is covered by their own privacy policies:
[Mozilla](https://www.mozilla.org/privacy/), [Google](https://policies.google.com/privacy) and
[GitHub](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).

The only network code in the app is `engine/Fetch.kt`; search the source for `URL(` or `HttpURLConnection` and it is the one place.
Nothing is downloaded until a language is pressed, and nothing in the background checks for
updates.

There is no camera permission. The camera button asks the phone's camera app to take the photo
and hand it back; the app sees only the photo you took. There is no storage permission either:
a shared picture comes with Android's permission to read that one picture.

## What happens to the text

It goes to Bergamot, the translator compiled into the app, and to CLD2, which guesses the
language; both run on the phone's own processor. A picture goes to Tesseract, also inside the
app, which reads its words.

## What is stored, and where

- **The text on the main screen**, in `files/draft.txt`, so it survives the app being closed.
  Clearing the field empties it. Nothing else you translate is kept: no history.
- **The last way translated**, the two language codes, in the app's `SharedPreferences`.
- **A picture being read**, briefly, as `files/picture-pending`, deleted as soon as it has been
  read, whether or not words were found in it. A photo taken with the camera button passes
  through the app's cache on the way.
- **The languages you download**, under the app's own folder: on the SD card when there is one,
  otherwise in the phone's storage, in a folder left out of backups since anything in it can be
  downloaded again. The files for reading pictures are always in the phone's storage.

`android:allowBackup="false"` is set, so none of it goes to a cloud backup. Uninstalling the app
deletes all of it, on the phone and on the card.

## Selected text and Replace

When another app hands over selected text, it hands only that text. "Replace with translation"
gives the translation back to that app, which puts it in place of the selection; nothing else
is written anywhere.

## Copy and Share

These do what they say and only when pressed: Copy puts the translation on the clipboard, and
Share opens Android's share sheet.

<!-- shared-about privacy section -->
## Who we are

Translate is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. What you put in Translate stays on your phone. The only things that go out are the language downloads described above: each is a request to Mozilla's store or to GitHub for one language file, and it carries the name of that file and nothing you have written. Nothing is sent to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Translate is sold or shared.

Since Translate keeps what it stores only on your phone, you can exercise most of these rights yourself: clear the text on the main screen, delete a language on the languages screen, clear the app's data in Android's settings, or uninstall the app, which deletes all of it. For anything you cannot do yourself, write to the address above.
<!-- /shared-about privacy section -->
