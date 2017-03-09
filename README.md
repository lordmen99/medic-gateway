`medic-gateway`
===============

<a href="https://travis-ci.org/medic/medic-gateway"><img src="https://travis-ci.org/medic/medic-gateway.svg?branch=master"/></a>

Download APKs from: https://github.com/medic/medic-gateway/releases

-----

An SMS gateway for Android.  Send and receive SMS from your webapp via an Android phone.

	+--------+                 +-----------+
	|  web   |                 |  medic-   | <-------- SMS
	| server | <---- HTTP ---- |  gateway  |
	|        |                 | (android) | --------> SMS
	+--------+                 +-----------+

# Use

## Installation

Download the latest APK from https://github.com/medic/medic-gateway/releases

## Configuration

### Medic Mobile

If you're configuring `medic-gateway` for use with hosted [`medic-webapp`](https://github.com/medic/medic-webapp), with a URL of e.g. `https://myproject.some-subdomain.medicmobile.org` and a username of `my_gateway_user` and a password of `topSecret`, fill in the settings as follows:

#### Medic-branded gateway

```
Instance name: myproject.some-subdomain
Username: my_gateway_user
Password: topSecret
```

#### Generic-branded gateway

```
WebappUrl: https://my_gateway_user:topSecret@myproject.some-subdomain.medicmobile.org
```

### Other

If you're configuring `medic-gateway` for use with other services, you will need to use the _generic_ build of `medic-gateway`, and find out the value for _webapp URL_ from your tech support.

# API

This is the API specification for communications between `medic-gateway` and a web server.  Messages in both directions are `application/json`.

Where a list of values is expected but there are no values provided, it is acceptable to:

* provide a `null` value; or
* provide an empty array (`[]`); or
* omit the field completely

Bar array behaviour specified above, `medic-gateway` _must_ include fields specified in this document, and the web server _must_ include all expected fields in its responses.  Either party _may_ include extra fields as they see fit.

## Idempotence

N.B. messages are cosidered duplicate by `medic-gateway` if they have identical values for `id`.  The webapp is expected to do the same.

`medic-gateway` will not re-process duplicate webapp-originating messages.

`medic-gateway` may forward a webapp-terminating message to the webapp multiple times.

`medic-gateway` may forward a delivery status report to the webapp multiple times for the same message.  This should indicate a change of state, but duplicate delivery reports may be delivered in some circumstances, including:

* the phone receives multiple delivery status reports from the mobile network for the same message
* `medic-gateway` failed to process the webapp's response when the delivery report was last forwarded from `medic-gateway` to webapp

## Authorisation

`medic-gateway` supports [HTTP Basic Auth](https://en.wikipedia.org/wiki/Basic_access_authentication).  Just include the username and password for your web endpoint when configuring `medic-gateway`, e.g.:

	https://username:password@example.com/medic-gateway-api-endpoint

## Messages

THe entire API should be implemented by a webapp at a single endpoint, e.g. https://exmaple.com/medic-gateway-api-endpoint

### GET

Expected response:

	{
		"medic-gateway": true
	}

### POST

`medic-gateway` will accept and process any relevant data received in a response.  However, it may choose to only send certain types of information in a particular request (e.g. only provide a webapp-terminating SMS), and will also poll the web service periodically for webapp-originating messages, even if it has no new data to pass to the web service.

### Request

#### Headers

The following headers will be set by requests:

header           | value
-----------------|-------------------
`Accept`         | `application/json`
`Accept-Charset` | `utf-8`
`Accept-Encoding`| `gzip`
`Cache-Control`  | `no-cache`
`Content-Type`   | `application/json`

Requests and responses may be sent with `Content-Encoding` set to `gzip`.

#### Content

	{
		"messages": [
			{
				"id": <String: uuid, generated by `medic-gateway`>,
				"from": <String: international phone number>,
				"content": <String: message content>
			},
			...
		],
		"updates": [
			{
				"id": <String: uuid, generated by webapp>,
				"status": <String: PENDING|SENT|DELIVERED|FAILED>,
				"reason": <String: failure reason (optional - only present for status:FAILED)>
			},
			...
		],
	}

The status field is defined as follows.

Status           | Description
-----------------|-------------------
PENDING | The message has been sent to the gateway's network
SENT | The message has been sent to the recipient's network
DELIVERED | The message has been received by the recipient's phone
FAILED | The delivery has failed and will not be retried

### Response

#### Success

##### HTTP Status: `2xx`

Clients may respond with any status code in the `200`-`299` range, as they feel is
appropriate.  `medic-gateway` will treat all of these statuses the same.

##### Content

	{
		"messages": [
			{
				"id": <String: uuid, generated by webapp>,
				"to": <String: local or international phone number>,
				"content": <String: message content>
			},
			...
		],
	}

#### HTTP Status `400`+

Response codes of `400` and above will be treated as errors.

If the response's `Content-Type` header is set to `application/json`, `medic-gateway` will attempt to parse the body as JSON.  The following structure is expected:

	{
		"error": true,
		"message": <String: error message>
	}

The `message` property may be logged and/or displayed to users in the `medic-gateway` UI.

#### Other response codes

Treatment of response codes below `200` and between `300` and `399` will _probably_ be handled sensibly by Android.


# Development

## Requirements

### `medic-gateway`

* JDK

### `demo-server`

* npm

## Building locally

To build locally and install to an attached android device:

	make

To run tests and static analysis tools locally:

	make test

## `demo-server`

There is a demonstration implementation of a server included for `medic-gateway` to communicate with.  You can add messages to this server, and query it to see the interactions that it has with `medic-gateway`.

### Local

To start the demo server locally:

	make demo-server

To list the data stored on the server:

	curl http://localhost:8000

To make the next good request to `/app` return an error:

	curl --data '"Something failed!"' http://localhost:8000/error

To add a webapp-originating message (to be send by `medic-gateway`):

	curl -vvv --data '{ "id": "3E105262-070C-4913-949B-E7ACA4F42B71", "to": "+447123555888", "content": "hi" }' http://localhost:8000

To simulate a request from `medic-gateway`:

	curl http://localhost:8000/app -H "Accept: application/json" -H "Accept-Charset: utf-8" -H "Accept-Encoding: gzip" -H "Cache-Control: no-cache" -H "Content-Type: application/json" --data '{}'

To clear the data stored on the server:

	curl -X DELETE http://localhost:8000

To set a username and password on the demo server:

	curl --data '{"username":"alice", "password":"secret"}' http://localhost:8000/auth

### Remote

It's simple to deploy the demo server to remote NodeJS hosts.

#### Heroku

TODO walkthrough

#### Modulus

TODO walkthrough

# Android Version Support

## "Default SMS/Messaging app"

Some changes were made to the Android SMS APIs in 4.4 (Kitkat®).  The significant change was this:

> from android 4.4 onwards, apps cannot delete messages from the device inbox _unless they are set, by the user, as the default messageing app for the device_

Some reading on this can be found at:

* http://android-developers.blogspot.com.es/2013/10/getting-your-sms-apps-ready-for-kitkat.html
* https://www.addhen.org/blog/2014/02/15/android-4-4-api-changes/

Adding support for kitkat® means that there is some extra code in `medic-gateway` whose purpose is not obvious:

### Non-existent activities in `AndroidManifest.xml`

Activities `HeadlessSmsSendService` and `ComposeSmsActivity` are declared in `AndroidManifest.xml`, but are not implemented in the code.

### Unwanted permissions

The `BROADCAST_WAP_PUSH` permission is requested in `AndroidManifest.xml`, and an extra `BroadcastReceiver`, `MmsIntentProcessor` is declared.  When `medic-gateway` is the default messaging app on a device, incoming MMS messages will be ignored.  Actual WAP Push messages are probably ignored too.

### Extra intents

To support being the default messaging app, `medic-gateway` listens for `SMS_DELIVER` as well as `SMS_RECEIVED`.  If the default SMS app, we need to ignore `SMS_RECEIVED`.

## Runtime Permissions

Since Android 6.0 (marshmallow), permissions for sending and receiving SMS must be requested both in `AndroidManifest.xml` and also at runtime.  Read more at https://developer.android.com/intl/ru/about/versions/marshmallow/android-6.0-changes.html#behavior-runtime-permissions
