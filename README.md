# WifiTrace 2.0

Android app (Kotlin) that logs Wi-Fi RSSI for the Advanced Mobile Computing mini-project (Group 12, Option C).

## Open and run
1. Android Studio: File > Open > select the folder that directly contains `settings.gradle.kts` (the inner `WifiTrace` folder).
2. Wait for Gradle sync, then press Run on the Redmi (USB debugging on). Allow Location when asked.

## The 4 tabs
| Tab | What it does |
|---|---|
| Measure | Live RSSI + signal quality. Pick distance and obstacle chips, press Start. 10 readings, 1 per second, row appended to `wifi_logs.csv`. |
| History | Every logged trial (newest first) with its 10 readings. "Undo last" removes a bad trial. |
| Summary | Line chart (RSSI vs distance for No wall / Wall / Body blocked), progress through the 33 planned trials, table with change vs the 1 m baseline. |
| Settings | Theme (System / Light / Dark), share or save the CSV, delete all data, device info. |

## CSV format
`timestamp,label,trial,R1..R10,average` where label is like `5m_wall` (`no_wall`, `wall`, `body_blocked`). The trial number counts up automatically per label.

## Privacy
No SSID, BSSID or MAC address is read or stored.
