# cordova-wtto00-filepath

## Installation

```bash
cordova plugin add cordova-wtto00-filepath
```

## Supported Platforms

- Android

## Usage

Once installed the plugin defines the `window.FilePath` object. To resolve a
file path:

```js
window.FilePath.resolveNativePath("content://...", successCallback, errorCallback);
```

### successCallback

Returns the `file://` file path.

### errorCallback

Returns the following object:

```js
{ code: <integer>, message: <string> }
```

Possible error codes are:

- `-1` - describes an invalid action
- `0` - `file://` path could not be resolved
- `1` - the native path links to a cloud file (e.g: from Google Drive app)
