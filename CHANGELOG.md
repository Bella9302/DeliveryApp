# Change log

## 1.0.0: Deliverable 4 build

### Added
- Project structure with the four layers (`ui`, `ai`, `validation`, `data`) and a plain Kotlin `core` module for models and rules.
- Sign-up, login, logout and password reset with Firebase Authentication. Role-based navigation for customer, restaurant, driver, agent and admin.
- Gemini chat through Firebase AI Logic with the four allowed functions. Every function call goes through `ActionGateway`.
- Validation rules in the brief's order (ownership, delivery, duplicate, well-formed, amount and escalation) with unit tests.
- "Talk to a person" button, "I want a human" detection and automatic escalation after two stuck turns.
- Module A: complaints (create from chat or form, read, update description, delete while logged), complaint message thread.
- Module B: restaurant menu (create, read, update, mark sold out, delete).
- Order tracking with a live status timeline.
- Agent screens: queue, take, resolve, approve, reject or complete refunds, reply to customers.
- Stats screen: complaints by status and category, bot resolution rate, refunds.
- Firestore security rules and indexes, and the `seed/seed_firestore.js` test data script.

### Fixed (from the earlier screen designs)
- Headings were black on black in dark mode. All text now uses theme colours (`onBackground`, `onSurface`).
- The splash screen was blank. It now shows the logo and a loading indicator.
- The sign-up terms checkbox text wrapped badly in dark mode. The label now takes the remaining row width.
