<p align="center">
  <img  src="https://dasa7d6hxd0bp.cloudfront.net/images/mirrorfly.webp" data-canonical-src="https://dasa7d6hxd0bp.cloudfront.net/images/mirrorfly.webp" width="400"  alt=""/>
</p>

# **MirrorFly Android SDK For Video Chat & Calls**

MirrorFly provides the chat and call API & SDK for your Android apps, allowing you to add real-time messaging and calling capabilities to your platform. The development kit comes with 1000+ custom CPaaS features along with 500+ AI-powered features to automate the conversational flow across your app. 

Every component in the kit is customizable letting developers build custom communication workflow with the freedom to host the solution on any server. Along with this, the security features can also be customized to add additional layers of data encryption. The entire platform can be white-labeled, without mention of the provider, enabling a fully branded experience to users. 

# **🤹 Key Product Offerings** 

MirrorFly helps build omni-channel communication apps for any kind of business

**💬 [In-app Messaging](https://www.mirrorfly.com/chat-api-solution.php)** \- Connect users individually or as groups via instant messaging features.  
**🎯 [HD Video Calling](https://www.mirrorfly.com/video-call-solution.php)**\- Engage users over face-to-face conversations anytime, and from anywhere.   
**🦾 [HQ Voice Calling](https://www.mirrorfly.com/voice-call-solution.php)** \- Deliver crystal clear audio calling experiences with latency as low as 3ms.   
🤖 [**AI Voice Agent**](https://www.mirrorfly.com/conversational-ai/voice-agent/) \- Build custom  AI voicebots that can understand, act and respond to user questions.   
**🤖 [AI Chatbot](https://www.mirrorfly.com/conversational-ai/chatbot/)** \- Deploy white-label AI chatbots that drive autonomous conversations across any web or mobile app.  
**🦾 [Live Streaming](https://www.mirrorfly.com/live-streaming-sdk.php)** \- Broadcast video content to millions of viewers around the world, within your own enterprise app. 

### [**Chat SDKs for Android**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#chat-sdks-for-android)

With CONTUS MirrorFly Chat SDK for Android, you can easily add real-time chat features to your client app within 30 minutes.

Through our client SDK, you can initialize and configure chat into your app with minimal efforts.

Note : If you're looking for the fastest way to build your app’s UI with MirrorFly Chat SDKs, you can use our sample apps. To get our sample apps, [click here](https://github.com/MirrorFly/MirrorFly-Android-Sample-V2)

### [**Requirements**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#requirements)

The requirements for chat SDK for Android are:

* Android Lollipop 5.0 (API Level 21) or above  
* Java 7 or higher  
* Gradle 8.6.0 or higher  
* Kotlin 2.0.20 or higher  
* targetSdkVersion, compileSdk 35

Note : If you're utilizing Chat SDK version 7.13.27 or higher, it's must to set the target SDK version to 35. This is due to the migration of Chat SDK to Android 15.

### [**Getting Started**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#things-to-be-noted-before-you-get-started)

#### **SDK License Key**

To integrate MirrorFly Chat SDK into your app, you will need a SDK License Key. The MirrorFly server will use this license key to authenticate the SDK in your application.

##### [**To get the License Key**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#to-get-the-license-key)**,**

Step 1: Register [here](https://www.mirrorfly.com/contact-sales.php) to get a MirrorFly User account.  
Step 2: [Login](https://console.mirrorfly.com/) to your Account  
Step 3: Get the License key from the application Info’ section  


<img  src="https://www.mirrorfly.com/docs/assets/images/license-key-a1173e922ebff14b6ae1a2428f822eec.png" data-canonical-src="https://www.mirrorfly.com/docs/assets/images/license-key-a1173e922ebff14b6ae1a2428f822eec.png" width="100%"  alt=""/>

![chat messaging sdk for android][image2]

### [**Integrate Chat SDK for Android App**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#integrate-chat-sdk-for-android-app)

**Step 1:** Create a new project or open an existing project in Android Studio.  
**Step 2:**

* If you are using Gradle 6.8 or higher, add the following code to your `settings.gradle` file.  
* If you are using Gradle 6.7 or lower, add the code to your root `build.gradle` file.

```gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        jcenter()
        maven {
            url "https://repo.mirrorfly.com/release"
        }
    }
}
```

![Jcenter][image3]

**Step 3:** Add the following dependencies in the `app/build.gradle` file.

```gradle
dependencies {
    implementation 'com.mirrorfly.sdk:mirrorflysdk:7.13.31'
}
```

**Step 4:** Add the following line to your `gradle.properties` file to prevent conflicts with imported libraries:

```properties
android.enableJetifier=true
```

**Step 5:** Open the `AndroidManifest.xml` file and add the following permissions:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## [**Initialize Chat SDK**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#initialize-chat-sdk)

Before you start using the SDK, ensure you meet some basic requirements. In your `Application` class, within the `onCreate()` method, use the following `ChatManager` method to provide the necessary initialization data:

![configscyAar][image4]

```java
ChatManager.initializeSDK("LICENSE_KEY", (isSuccess, throwable, data) -> {
    if (isSuccess) {
        Log.d("TAG", "initializeSDK success ");
    } else {
        Log.d("TAG", "initializeSDK failed with reason " + data.get("message"));
    }
});
```

### [**Add MyApplication**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#add-myapplication)

Add the created `MyApplication` to `AndroidManifest.xml`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.uikitapplication">

    <application
        android:name=".MyApplication"  <!-- Add this line. -->
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@style/AppTheme">

        <activity android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                ...
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

## [**Registration**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#registration)

The following method registers a user in sandbox or live mode based on the `setIsTrialLicenceKey` setting.

```java
FlyCore.registerUser(USER_IDENTIFIER, (isSuccess, throwable, data) -> {
    if (isSuccess) {
        Boolean isNewUser = (Boolean) data.get("is_new_user"); // true - if the current user is different from the previous session's logged-in user, false - if the same user is logging in again
        String userJid = (String) data.get("userJid"); // Ex. 12345678@xmpp-preprod-sandbox.mirrorfly.com (USER_IDENTIFIER + @ + domain of the chat server)
        JSONObject responseObject = (JSONObject) data.get("data");
        String username = responseObject.getString("username");
    } else {
        // Register user failed print throwable to find the exception details.
    }
});
```

## [**Connect to the Chat Server**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#connect-to-the-chat-server)

Once registration is successful, the Chat SDK automatically attempts to connect to the chat server. It also monitors application lifecycle changes and connects or disconnects from the chat server accordingly.

## [**Observe Connection Events**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#observe-connection-events)

Once the `ChatConnectionListener` is set, you can receive connection status updates through the callback method shown below.

```java
ChatManager.setConnectionListener(new ChatConnectionListener() {
    @Override
    public void onConnected() {
        // Write your success logic here to navigate Profile Page or
        // To Start your one-one chat with your friends
    }

    @Override
    public void onDisconnected() {
        // Connection disconnected
    }

    @Override
    public void onConnectionFailed(@NonNull FlyException e) {
        // Connection Not authorized or Unable to establish connection with server
    }

    @Override
    public void onReconnecting() {
        // Automatic reconnection enabled
    }
});
```

### [**Preparing user jid**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#preparing-user-jid)

To generate a JID for any user, use the below method.

```java
FlyUtils.getJid(USER_NAME);
```

## [**Send a One-to-One Message**](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#send-a-one-to-one-message)

Use the below method to send a text message to other user,

```java
TextMessage textMessage = new TextMessage();
textMessage.setToId(TO_JID);
textMessage.setMessageText(TEXT);
```

```java
FlyMessenger.sendTextMessage(textMessage, (isSuccess, error, chatMessage) -> {
    if (isSuccess) {
        // you will get the message sent success response
    }
});
```

## [Receive a One-to-One Message](https://www.mirrorfly.com/docs/chat/android/v3/quick-start#receive-a-one-to-one-message)

You need to initialize the `MessageEventsListener` observer to receive and monitor all incoming messages sent to you.

```java
ChatEventsManager.setupMessageEventListener(new MessageEventsListener() {
    @Override
    public void onMessageReceived(@NotNull ChatMessage message) {
        // called when the new message is received
    }
});
```

These listeners are triggered only when a new message is received from another user. For more details, refer to the callback listeners documentation.

```java
@Override
public void onMessageReceived(@NonNull ChatMessage message) {
    super.onMessageReceived(message);
    // received message object
}
```

# **Making a call**

## [**Make a call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#make-a-call)

The call feature is essential for modern communication. The Call SDK enables users to make one-to-one audio or video calls with other SDK users.

### [**Required runtime permissions**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#required-runtime-permissions)

For audio call, we need a below permissions:

```java
Manifest.permission.RECORD_AUDIO
Manifest.permission.READ_PHONE_STATE
```

You can use the below method to check audio call permissions:

```java
CallManager.isAudioCallPermissionsGranted();
```

For video call, we need below permissions:

```java
Manifest.permission.RECORD_AUDIO
Manifest.permission.CAMERA
Manifest.permission.READ_PHONE_STATE
```

You can use the below method to check video call permissions:

```java
CallManager.isVideoCallPermissionsGranted();
```

You can use the below method to check call notification permission:

```java
Manifest.permission.POST_NOTIFICATIONS
```

```java
CallManager.isNotificationPermissionsGranted();
```

### [**Call Metadata**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#call-metadata)

To send custom data when making a call, use the optional `metaData` parameter in all `makeCall` methods. This parameter accepts an array of type `CallMetadata`.

### [**Make a voice call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#make-a-voice-call)

The voice call feature allows users to make a one-to-one audio call with another SDK user, including optional call metadata. Use the following method to initiate a voice call:

```java
CallManager.makeVoiceCall("TO_JID", CALL_METADATA, (isSuccess, flyException) -> {
    if (isSuccess) {
        // SDK will take care of presenting the Call UI. It will present the activity
        // that is passed using the method `CallManager.setCallActivityClass()`
        Log.d("MakeCall", "call success");
    } else {
        if (flyException != null) {
            String errorMessage = flyException.getMessage();
            Log.d("MakeCall", "Call failed with error: " + errorMessage);
            // toast error message
        }
    }
});
```

### [**Make a video call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#make-a-video-call)

The video call feature allows users to make a one-to-one video call with another SDK user, including optional call metadata. Use the following method to initiate a video call:

```java
CallManager.makeVideoCall("TO_JID", CALL_METADATA, (isSuccess, flyException) -> {
    if (isSuccess) {
        // SDK will take care of presenting the Call UI. It will present the activity
        // that is passed using the method `CallManager.setCallActivityClass()`
        Log.d("MakeCall", "call success");
    } else {
        if (flyException != null) {
            String errorMessage = flyException.getMessage();
            Log.d("MakeCall", "Call failed with error: " + errorMessage);
            // toast error message
        }
    }
});
```

### [**Make a group voice call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#make-a-group-voice-call)

The group voice call feature allows users to make a voice call with multiple SDK users, including optional call metadata. Use the following method to initiate a group voice call:

```java
CallManager.makeGroupVoiceCall(JID_LIST, GROUP_ID, CALL_METADATA, new CallActionListener() {
    @Override
    public void onResponse(boolean isSuccess, @Nullable FlyException flyException) {
        if (isSuccess) {
            // SDK will take care of presenting the Call UI. It will present the activity
            // that is passed using the method `CallManager.setCallActivityClass()`
            Log.d("MakeCall", "call success");
        } else {
            if (flyException != null) {
                String errorMessage = flyException.getMessage();
                Log.d("MakeCall", "Call failed with error: " + errorMessage);
                // toast error message
            }
        }
    }
});
```

### [**Make a group video call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#make-a-group-video-call)

The group video call feature allows users to make a video call with multiple SDK users, including optional call metadata. Use the following method to initiate a group video call:

```java
CallManager.makeGroupVideoCall(JID_LIST, GROUP_ID, CALL_METADATA, new CallActionListener() {
    @Override
    public void onResponse(boolean isSuccess, @Nullable FlyException flyException) {
        if (isSuccess) {
            // SDK will take care of presenting the Call UI. It will present the activity
            // that is passed using the method `CallManager.setCallActivityClass()`
            Log.d("MakeCall", "call success");
        } else {
            if (flyException != null) {
                String errorMessage = flyException.getMessage();
                Log.d("MakeCall", "Call failed with error: " + errorMessage);
                // toast error message
            }
        }
    }
});
```

### [**Add participants to the call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#add-participants-to-the-call)

After a call is connected, you can add users to the ongoing call. The SDK provides methods to invite users, and once they accept the incoming call, they will join the active call.

```java
CallManager.inviteUsersToOngoingCall(JID_LIST, new CallActionListener() {
    @Override
    public void onResponse(boolean isSuccess, @Nullable FlyException flyException) {
        // handle invite result
    }
});
```

### [**Receiving a incoming audio/video call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#receiving-a-incoming-audiovideo-call)

When you receive an audio or video call from another SDK user, the Call SDK will display a notification if the device is running Android 10 (API level 29) or higher. For lower Android versions, the activity set via `CallManager.setCallActivityClass()` during SDK initialization will launch with the call details. A sample call UI is also provided for quick integration.

### [**Answer the incoming call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#answer-the-incoming-call)

When you receive an audio or video call from another SDK user, your activity may be launched depending on the Android version. When the user presses the accept button in your call UI, call the following SDK method to answer the call and notify the caller.

```java
CallManager.answerCall((isSuccess, flyException) -> {
    if (isSuccess) {
        Log.d("AnswerCall", "call answered success");
    } else {
        if (flyException != null) {
            String errorMessage = flyException.getMessage();
            Log.d("AnswerCall", "Call answered failed with error: " + errorMessage);
            // toast error message
        }
    }
});
```

### [**Decline the incoming call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#decline-the-incoming-call)

When you receive an audio or video call from another SDK user, your activity may be launched depending on the Android version. When the user presses the decline button in your call UI, call the following SDK method to decline the call and notify the caller.

```java
CallManager.declineCall();
```

### [**Disconnect the ongoing call**](https://www.mirrorfly.com/docs/audio-video/android/v2/feature-make-a-call/#disconnect-the-ongoing-call)

Whenever you make an audio or video call to another SDK user and want to disconnect either before the call connects or after the conversation ends, call the following SDK method when the user presses the disconnect button in your call UI. This will end the call and notify the other participant.

```java
CallManager.disconnectCall();
```

# **☁️ Deployment Models \- Self-hosted and Cloud**

MirrorFly offers full freedom with the hosting options:  
**Self-hosted:** Deploy your client on your own data centers, private cloud or third-party servers.  
[Check out our multi-tenant cloud hosting](https://www.mirrorfly.com/self-hosted-chat-solution.php)  
**Cloud:** Host your client on MirrorFly’s multi-tenant cloud servers.   
[Check out our multi-tenant cloud hosting](https://www.mirrorfly.com/multi-tenant-chat-for-saas.php)

## Mobile Client

MirrorFly offers a fully-built client SafeTalk that is available in:
- iOS
- Android

# **📚 Learn More**

* [Developer Documentation](https://www.mirrorfly.com/docs/)  
* [Product Tutorials](https://www.mirrorfly.com/tutorials/)  
* [Dart Documentation](https://pub.dev/packages/mirrorfly_plugin)  
* [Pubdev Documentation](https://pub.dev/packages/mirrorfly_plugin)  
* [Npmjs Documentation](https://www.npmjs.com/~contus)  
* [On-premise Deployment](https://www.mirrorfly.com/on-premises-chat-server.php)   
* [See who's using MirrorFly](https://www.mirrorfly.com/chat-use-cases.php)

# **🧑‍💻 Hire Experts**

Need a tech team to build your enterprise app? [Hire a full team of experts](https://www.mirrorfly.com/hire-video-chat-developer.php). From concept to launch, we handle every step of the development process. Get a high-quality, fully-built app ready to launch, carefully built by industry experts

# **⏱️ Round-the-clock Support**

If you’d like to take help when working with our solution, feel free to [contact our experts](https://www.mirrorfly.com/contact-sales.php) who will be available to help you anytime of the day or night. 

## **💼 Become a Part of our amazing team**

We're always on the lookout for talented developers, support specialists, and product managers. Visit our [careers page](https://www.contus.com/careers.php) to explore current opportunities.

## **🗞️ Get the Latest Updates**

* [Blog](https://www.mirrorfly.com/blog/)  
* [Facebook](https://www.facebook.com/MirrorFlyofficial/)  
* [Twitter](https://twitter.com/mirrorflyteam)  
* [LinkedIn](https://www.linkedin.com/showcase/mirrorfly-official/)  
* [Youtube](https://www.youtube.com/@mirrorflyofficial)  
* [Instagram](https://www.instagram.com/mirrorflyofficial/)

