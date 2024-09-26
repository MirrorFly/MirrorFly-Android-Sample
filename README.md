<p align="center">
  <img  src="https://dasa7d6hxd0bp.cloudfront.net/images/mirrorfly.webp" data-canonical-src="https://dasa7d6hxd0bp.cloudfront.net/images/mirrorfly.webp" width="400"  alt=""/>
</p>

<h1 align="center">
  The 100% Customizable CPaaS Solution For Enterprise Communication
</h1>


[MirrorFly](https://www.mirrorfly.com/) is a secure, fully customizable CPaaS (Communication as a Service) solution. We offer APIs and SDKs for businesses looking to build custom enterprise chat apps for internal team communication, and collaboration. MirrorFly is carefully designed to tailor highly customized solutions, enabling custom chat app development at the quickest possible time without compromising security, features and functionality.


1000s of world-class brands like Ejada and Etisalat trust MirrorFly to build their cloud communication system and infrastructure. The solution powers over millions of end-user communication with real-time video, voice and messaging features.


# 🤹 Key Product Offerings 

MirrorFly helps build omni-channel communication apps for any kind of business


 💬 [In-app Messaging](https://www.mirrorfly.com/chat-api-solution.php) - Connect users individually or as groups via instant messaging features.

 🎯 [HD Video Calling](https://www.mirrorfly.com/video-call-solution.php)- Engage users over face-to-face conversations anytime, and from anywhere.

 🦾 [HQ Voice Calling](https://www.mirrorfly.com/voice-call-solution.php) - Deliver crystal clear audio calling experiences with latency as low as 3ms.
 
 🦾 [Live Streaming](https://www.mirrorfly.com/live-streaming-sdk.php) - Broadcast video content to millions of viewers around the world, within your own enterprise app. 


# ⚒️ UI-KIT SDKs For Android

With CONTUS MirrorFly [Chat SDK for Android](https://www.mirrorfly.com/docs/uikit/android/quick-start-version-2/), you can efficiently integrate the desired real-time chat features into a client app.

When it comes to the client-side implementation, you can initialize and configure the uikitsdk with minimal efforts. With the server-side, MirrorFly ensures reliable infra-management services for the chat within the app. This page will let you know how to install the UI-KIT SDK in your app.

Note : If you're looking for the fastest way in action with CONTUS MirrorFly [Chat SDKs](https://www.mirrorfly.com/chat-api-solution.php), then you need to build your app on top of our sample version. Simply download the sample app and commence your app development. To download sample app [click here](https://github.com/MirrorFly/MirrorFly-Android-Sample-V2)


# ✅Requirements
The requirements for UI-KIT SDK for Android are:
- Android Marshmallow 6.0 (API Level 23) or above
- Java 8 or higher
- Gradle 4.1.0 or higher


# ⚙️Integrate the Chat SDK

**Step 1:** Create a new project or Open an existing project in Android Studio

**Step 2:** If using `Gradle 6.8` or higher, add the following code to your settings.gradle file. If using `Gradle 6.7` or lower, add the following code to your root build.gradle file. See <a href="https://docs.gradle.org/6.8/release-notes.html#dm-features" target="_self">this release note</a> to learn more about updates to Gradle.

```gradle
dependencyResolutionManagement {
        repositories {
            mavenCentral()
               maven {
                 url "https://repo.mirrorfly.com/release"
                 }
       }
}
   ```

**Step 3:** Add the following dependencies in the `app/build.gradle` file.
   ```gradle
dependencies {
       implementation 'com.mirrorfly.uikitsdk:mf-uikitsdk:1.0.37'
 }
```

**Step 4:** Add the below dependencies required by the SDK in the app `module/build.gradle` file.
   ```gradle
   buildscript {
    dependencies {
      classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21'
      def nav_version = "2.3.5"
      classpath "androidx.navigation:navigation-safe-args-gradle-plugin:$nav_version"
  }
}
```

**Step 5:** Add the below line in the `gradle.properties` file, to avoid imported library conflicts.
   ```gradle
  android.enableJetifier=true
```

**Step 6:** Open the `AndroidManifest.xml` and add below permissions.
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```

# ▶️Initialization

To integrate and run Mirrorfly UIKit in your app, you need to initialize it first. You can initialize the MirrorFlyUIKit instance by passing the MirrorFlyUIKitAdapter instance as an argument to a parameter in the MirrorFlyUIKit.init() method. The MirrorFlyUIKit.init() must be called once in the onCreate() method of your app’s Application instance.

Note : While registration, the below `registerUser` method will accept the `FCM_TOKEN` as an optional param and pass it across.

**Step 1:** Add the below line in the application class file.

```gradle
package com.example.mfuikittest

import android.app.Application
import com.mirrorflyuikitsdk.MirrorFlyUIKit
import com.mirrorflyuikitsdk.adapter.MirrorFlyUIKitAdapter

class BaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        MirrorFlyUIKit.initFlySDK(applicationContext,object : MirrorFlyUIKitAdapter {
           
            override fun setAppName(): String? {
                return "YOUR_APP_NAME"
            }
            
            override fun setApplicationID(): String? {
                return "YOUR_APPLICATION_ID"
            }
            
            //Below override methods are optional used for customization
            
            override fun isCallEnabled(): Boolean? {
                return true
            }

            override fun isGroupEnable(): Boolean? {
                return true
            }

            override fun isContactEnable(): Boolean? {
                return true
            }

            override fun isLogoutEnable(): Boolean? {
                return true
            }

            override fun isOtherProfileEnable(): Boolean? {
                return true
            }

            override fun isOwnProfileEnable(): Boolean? {
                return true
            }

            override fun setGoogleTranslationKey(): String? {
                return getString(R.string.google_key)
            }

            override fun onlyOnetoOneChat(): Boolean? {
                return false
            }
        })
        
        MirrorFlyUIKit.defaultThemeMode = MirrorFlyUIKit.ThemeMode.Light
        MirrorFlyUIKit.loginActivity = "LoginActivity"::class.java
    }
}
```

**Step 2:** Add the below line in the Launcher class file.

```gradle
class SplashTestActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        MirrorFlyUIKit.initializeSDK(this@SplashTestActivity,SplashTestActivity::class.java,"YOUR_LICENCE_KEY",object : FlyInitializeSDKCallback{
            
            override fun flyError(
                isSuccess: Boolean,
                throwable: Throwable?,
                data: HashMap<String, Any>
            ) {
                //TODO Error Handling 
            }

            override fun redirectToDashBoard(isSuccess: Boolean) {
                startActivity(Intent(this@SplashTestActivity, MFUIDemoActivity::class.java))
                finish()
            }

            override fun redirectToLogin(isSuccess: Boolean) {
                startActivity(Intent(this@SplashTestActivity, MainActivity::class.java))
                finish()
            }
        })
        
    }

}
```


# 🧑‍💻 Registration

```gradle
  MirrorFlyUIKit.initUser("USER_IDENTIFIER", "FIREBASE TOKEN", object : InitResultHandler {
        
        override fun onInitResponse(isSuccess: Boolean, e: String) {
            if (isSuccess) {
                Log.d("TAG", "onInitResponse called with: isSuccess = $isSuccess")
            } else {
                Log.e("TAG", "onInitResponse called with: Failure, e = $e")
            }
        }
})
```

# 🗃️ Display Recent Chat and Call list

DashBoardActivity is the starting point for launching UIKit in your application. By implementing the code below, you will see a complete list of recent chats that you're made with single and group conversation.

```gradle
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.mirrorflyuikitsdk.activities.DashBoardActivity


class MainActivity : DashBoardActivity() { // Add this line.


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) 
    // If you’re going to inherit `DashBoardActivity`, don’t implement `setContentView()`
    }
}
```

# ☁️ Deployment Models - Self-hosted and Cloud

MirrorFly offers full freedom with the hosting options:
Self-hosted: Deploy your client on your own data centers, private cloud or third-party servers.
[Check out our multi-tenant cloud hosting](https://www.mirrorfly.com/self-hosted-chat-solution.php)
Cloud: Host your client on MirrorFly’s multi-tenant cloud servers.
[Check out our multi-tenant cloud hosting](https://www.mirrorfly.com/multi-tenant-chat-for-saas.php)


# 📱 Mobile Client

MirrorFly offers a fully-built client SafeTalk that is available in:
- iOS
- Android

You can use this client as a messaging app, or customize, rebrand & white-label it as your chat client. 


# 📚 Learn More

- Developer Documentation
- Product Tutorials
- Dart Documentation
- Pubdev Documentation
- Npmjs Documentation
- On-premise Deployment
- See who's using MirrorFly


# 🧑‍💻 Hire Experts

Need a tech team to build your enterprise app? [Hire a full team of experts](https://www.mirrorfly.com/hire-video-chat-developer.php). From concept to launch, we handle every step of the development process. Get a high-quality, fully-built app ready to launch, carefully built by industry experts


# ⏱️ Round-the-clock Support

If you’d like to take help when working with our solution, feel free to [contact our experts](https://www.mirrorfly.com/contact-sales.php) who will be available to help you anytime of the day or night. 


# 💼 Become a Part of our amazing team

We're always on the lookout for talented developers, support specialists, and product managers. Visit our [careers page](https://www.contus.com/careers.php) to explore current opportunities.


# 🗞️ Get the Latest Updates

- [Blog](https://www.mirrorfly.com/blog/)
- [Facebook](https://www.facebook.com/MirrorFlyofficial/)
- [Twitter](https://twitter.com/mirrorflyteam)
- [LinkedIn](https://www.linkedin.com/showcase/mirrorfly-official/)
- [Youtube](https://www.youtube.com/@mirrorflyofficial)
- [Instagram](https://www.instagram.com/mirrorflyofficial/)
