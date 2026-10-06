# Change log

## 0.1.0: first build from the Deliverable 4 Systems Brief

- Kotlin and Jetpack Compose project with the four packages: `ui`, `ai`, `validation`, `data`.
- Firebase Authentication: sign-up, login, logout and password reset.
- Gemini chat through Firebase AI Logic, with the four allowed functions.
- Validation of every proposed action in the brief's order: ownership, delivery, duplicate,
  well-formed, amount and escalation.
- Missing items under R15 are refunded 50% automatically. R15 and above, safety issues and all other
  complaint types go to an agent.
- Hand-over to a person: a button, detection of "I want a human", and automatic escalation after
  2 failures in a row.
- Screens: orders and tracking, chat, my complaints (Module A), restaurant menu (Module B), agent
  queue and case view, stats.
- Firestore security rules, seed script with test accounts and orders, and unit tests.
