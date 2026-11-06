# TaskQuest - corrected Android Studio project

## Included
- Firebase email/password registration and login
- Home screen after successful login
- Add quests, view quests, mark quests complete, and delete quests
- Firestore live updates per signed-in user
- Basic completed-quest XP counter

## Open and run
1. Extract this ZIP to a normal folder (do not open the ZIP itself).
2. Open Android Studio.
3. Choose **Open** and select the `TaskQuest` folder (the folder containing `settings.gradle.kts`).
4. Wait for Gradle Sync. If prompted, install the Android SDK Platform 36 and JDK 17.
5. In Firebase Console, open project `taskquest-19639`.
6. Authentication > Sign-in method > enable **Email/Password**.
7. Firestore Database > Create database. For a class demo, create it in test mode first; before real use, apply secure rules.
8. Confirm `app/google-services.json` belongs to Firebase project `taskquest-19639` and package `com.example.taskquest`. The supplied file matches the package.
9. Press the green **Run ▶** button and select your Android phone (USB debugging enabled) or an emulator.

## Firestore security rules
For a basic authenticated-user-only setup, use these rules in Firestore > Rules:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/quests/{questId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

## Important
- Never upload `google-services.json` to a public repository if you do not intend to expose project configuration. Firebase client config is not a server secret, but keep your project access and Firestore rules secure.
- This is a functional starter app: registration/login and quest CRUD are implemented. Focus mode, habits, challenges, rewards and achievements are not yet included in this Android version.
- If Gradle reports an SDK or plugin download issue, connect to the internet and use Android Studio's **Tools > SDK Manager** to install the SDK requested.
