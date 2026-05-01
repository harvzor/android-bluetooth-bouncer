## ADDED Requirements

### Requirement: Suppress repeated alert noise for an already-visible nearby notification
When a watched blocked device notification is already visible in the notification drawer, re-posting that notification SHALL NOT trigger sound, vibration, or a heads-up pop-up. The first post SHALL always produce a full alert. Subsequent re-posts while the notification remains visible SHALL update silently.

#### Scenario: Device oscillates at range boundary — notification already visible
- **WHEN** `onDeviceAppeared()` fires for a watched blocked device
- **AND** a notification for that device is already present in the notification drawer (not dismissed)
- **THEN** the notification content is updated but no sound, vibration, or heads-up is triggered

#### Scenario: First appearance — no notification exists
- **WHEN** `onDeviceAppeared()` fires for a watched blocked device
- **AND** no notification for that device is currently in the notification drawer
- **THEN** a full heads-up alert is posted with sound and vibration

#### Scenario: Device reappears after user dismissed the notification
- **WHEN** `onDeviceAppeared()` fires for a watched blocked device
- **AND** the user previously dismissed the notification for that device
- **THEN** a full heads-up alert is posted (treated as a new appearance)
