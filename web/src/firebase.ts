import { initializeApp } from 'firebase/app';
import { getFirestore } from 'firebase/firestore';
import { getAuth, signInAnonymously, onAuthStateChanged } from 'firebase/auth';

// Firebase configuration for the Stumpd project.
// The web app must be registered in Firebase Console:
//   Console -> Project Settings -> General -> Your apps -> Add app -> Web
// Then copy the firebaseConfig snippet here.
const firebaseConfig = {
  apiKey: "AIzaSyBNZlP9HyGLKp_PXaqN19MQ7lz1cSVm2j0",
  authDomain: "stumpd-887ff.firebaseapp.com",
  projectId: "stumpd-887ff",
  storageBucket: "stumpd-887ff.firebasestorage.app",
  messagingSenderId: "915746159299",
  // TODO: Replace with your web app ID from Firebase Console
  // Go to: Firebase Console -> Project Settings -> General -> Web apps
  appId: "1:915746159299:web:ab05b35116607ca3dc8076",
};

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
export const auth = getAuth(app);

/**
 * Sign in anonymously. Required by Firestore security rules
 * (all reads require `request.auth != null`).
 * This is invisible to the user -- no login UI needed.
 */
export async function ensureAuth(): Promise<string | null> {
  return new Promise((resolve) => {
    const unsubscribe = onAuthStateChanged(auth, async (user) => {
      unsubscribe();
      if (user) {
        resolve(user.uid);
      } else {
        try {
          const result = await signInAnonymously(auth);
          resolve(result.user.uid);
        } catch (error) {
          console.error('Anonymous auth failed:', error);
          resolve(null);
        }
      }
    });
  });
}
