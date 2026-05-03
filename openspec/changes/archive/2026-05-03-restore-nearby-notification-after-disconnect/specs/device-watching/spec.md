## Note

Task 4.1 (cancel notification for alert-enabled devices on disappear) was implemented then reverted due to a regression — it caused "Nearby" notifications to be cancelled when devices oscillated at the edge of range. The `device-watching` spec is unchanged by this change; no sync needed.
