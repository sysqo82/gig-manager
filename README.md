# Suede Tour Manager - Android App

This Android app displays the Suede Feb-Mar 2026 tour information in an easy-to-use mobile format.

## Features

- **Dropdown Selection**: Select any city & venue from the tour
- **Complete Information**: View all details including:
  - Date of the gig
  - Ticket information and location
  - Accommodation details and dates
  - Cost breakdown and payment status
  - Travel details and arrangements

## How to Build and Run

### Prerequisites
- Android Studio (latest version recommended)
- Android SDK with minimum API level 24 (Android 7.0)
- Java 8 or higher

### Steps to Build

1. **Open the project in Android Studio**:
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the `gig-manager` folder and select it

2. **Sync Gradle**:
   - Android Studio should automatically start syncing Gradle
   - If not, go to File > Sync Project with Gradle Files

3. **Run on an Emulator**:
   - Click the "Run" button (green play icon) in the toolbar
   - Select an emulator or create a new one (recommended: Pixel 5 with API 34)
   - The app will build and launch on the emulator

4. **Run on a Physical Device**:
   - Enable Developer Options on your Android phone:
     - Go to Settings > About Phone
     - Tap "Build Number" 7 times
   - Enable USB Debugging in Developer Options
   - Connect your phone via USB
   - Click "Run" and select your device

### Building an APK

To install on your phone without Android Studio:

1. In Android Studio, go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**
2. Once complete, click "locate" in the notification
3. Transfer the APK to your phone
4. Install it (you may need to allow installation from unknown sources)

## Usage

1. Launch the app
2. Tap the dropdown at the top
3. Select a city & venue from the list
4. All the gig information will be displayed below
5. Scroll to see all details

## Data Structure

The app includes all 14 gigs from the tour with complete information about:
- Tickets
- Accommodation
- Travel arrangements
- Payment status
- Comments and special notes

## Updating the Data

To add or modify gig information:
1. Edit the file: `app/src/main/res/raw/gigs_data.json`
2. Follow the existing JSON format
3. Rebuild the app

## Technical Details

- **Language**: Kotlin
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Libraries**: 
  - AndroidX Core, AppCompat, Material Design
  - Gson for JSON parsing
