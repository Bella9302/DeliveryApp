# eats: Complaint Chatbot App

ITMDA3-B33, Deliverable 4: Implementation.

An Android app where customers report problems with a food order to an AI chatbot. The bot understands the message; **our code checks the rules and decides the outcome**. Small, valid complaints are resolved automatically. Everything else goes to a human agent, and a customer can always reach a person.

## Stack

| Part | Technology |
|---|---|
| App | Kotlin, Jetpack Compose, Material 3. One app for all roles. |
| Navigation | Navigation Compose |
| AI | Gemini (`gemini-3.8-flash`) through Firebase AI Logic (`firebase-ai`) |
| Validation | Plain Kotlin (`core` module, unit tested) |
| Login | Firebase Authentication (email and password) |
| Database | Cloud Firestore |
| Test data | `seed/seed_firestore.js` (Node, `firebase-admin`) |

## Project layout

```
core/      Plain Kotlin, no Android: data models and the validation rules, with unit tests
app/       The Android app
seed/      Seed script for test data
firestore.rules, firestore.indexes.json, firebase.json
```

The four layers each live in their own package under `com.itmda3b33.eats`:

| Layer | Package | What is in it |
|---|---|---|
| User interface | `ui` | Screens and view models. No business rules. |
| Chatbot service | `ai` | `ChatbotService`, `SystemPrompt`, `ChatFunctions`, `FunctionCallParser`. No database access: it only proposes actions. |
| Validation | `validation` | Rules in `core` (`RefundRules`, `ComplaintRules`, `StatusRules`, `MenuRules`, `AuthRules`, `ConversationGuard`) and services in `app` (`ActionGateway`, `ComplaintService`, `StaffService`, `MenuService`). Every action passes here before it runs. |
| Data | `data` | Repositories. The only code that reads and writes Firestore and Firebase Auth. Models are in `core` under `data.model`. |

`AppContainer` wires the layers together.

## How one message flows

1. `ChatViewModel` sends the customer's text to `ChatbotSession` (the `ai` layer).
2. Gemini answers with a function call, for example `lookupOrder`.
3. `FunctionCallParser` turns it into a `ProposedAction` and hands it to `ActionGateway`.
4. The gateway loads the records, runs the rules, and only then writes through the data layer. It returns an `ActionOutcome`: done, blocked, escalated or failed.
5. The outcome goes back to Gemini as the function response, and Gemini writes the reply.
6. The chat shows the reply and a tag for every outcome, for example "Refund of R6,00 approved" or "Not allowed: …".

## Chatbot functions

Gemini may request only these four (`ai/ChatFunctions.kt`):

| Function | What our code does |
|---|---|
| `lookupOrder(orderId?)` | Ownership check, then returns the order, its items and already-refunded items. Without an ID it lists recent orders. |
| `createComplaint(orderId, category, description, items?)` | `ComplaintRules.validateCreate`, then saves it. Safety or health complaints start escalated. |
| `issueRefund(complaintId, menuItemId, quantity)` | `RefundRules.evaluate`. **There is no amount parameter**: the amount is worked out in code. |
| `escalateToAgent(complaintId?, orderId?, reason)` | Always succeeds. Creates a complaint if none exists, sets it to escalated and saves the chat history. |

## Validation rules

Run in the brief's order by `RefundRules.evaluate` (`core/.../validation/RefundRules.kt`):

1. **Ownership**: the order and complaint belong to the signed-in customer.
2. **Delivery**: missing-item claims need an order with status `delivered`.
3. **Duplicate**: an item with a refund that is not rejected cannot be refunded again.
4. **Well-formed**: item present in the order, quantity 1 to the ordered quantity, complaint still `logged`.
5. **Amount and escalation** (`RefundPolicy`), on the claim value (unit price × quantity):
   - under R15: automatic 50% refund, `approvedBy = "system"`, complaint resolved
   - above R50: escalate; the refund is saved as `requested` for an agent
   - R15 to R50: **[Confirm]** the group has not decided. The default is to escalate; set `MidRangeDecision.AUTO_REFUND` in `RefundPolicy` to change it.
   - safety or health issue: always escalate
   - other categories (wrong item, late, quality): escalate, since the brief only defines automatic refunds for missing items

Record IDs that are missing entirely (no complaint or order to load) are reported as well-formed failures before the other checks, because nothing can be checked without them.

### Never stuck in a loop

- The **Talk to a person** button is always on the chat screen.
- Messages like "I want a human" or "can I talk to someone" escalate straight away without asking Gemini (`ConversationGuard.isHumanRequest`).
- Two stuck turns in a row (a blocked action, an error, or Gemini unreachable) escalate automatically.
- Gemini gets at most six function rounds per message.
- After escalation, further messages go to the agent in the complaint thread.

## Screens by role

| Role | Screens |
|---|---|
| customer | Restaurants, menu (read), orders, order tracking, complaints (create, read, update, delete), complaint thread, chat |
| restaurant | Menu management (create, read, update, delete, sold out), restaurant orders (read) |
| agent, admin | Complaint queue, complaint detail (take, resolve, approve, reject or complete refunds, reply), stats |
| driver | Placeholder. Courier screens are in the "if time allows" scope. |

Each route is wrapped in a role gate, and `firestore.rules` enforces the same on the data. Sign-up always creates a `customer`; other roles come from the seed script or an admin.

## Data (Firestore)

Status values come from the brief and are defined once in `core/.../data/model/Statuses.kt`.

| Collection | Fields |
|---|---|
| `users/{uid}` | firstName, lastName, email, mobile, role, restaurantId (restaurant users), createdAt |
| `restaurants/{id}` | name, cuisine, etaMinMinutes, etaMaxMinutes, rating, ownerId |
| `restaurants/{id}/menuItems/{id}` | name, description, priceCents, available |
| `drivers/{uid}` | name, phone, vehicle |
| `orders/{id}` | customerId, restaurantId, restaurantName, driverId, status, items[{menuItemId, name, priceCents, quantity}], totalCents, createdAt, deliveredAt |
| `complaints/{id}` | customerId, orderId, restaurantId, category, description, items[{menuItemId, name, quantity}], status, resolution, assignedAgentId, source (chat or form), createdAt, updatedAt |
| `complaints/{id}/messages/{id}` | sender (customer, bot, agent, system), text, createdAt |
| `refunds/{id}` | complaintId, orderId, customerId, menuItemId, itemName, quantity, amountCents, status, approvedBy, reason, createdAt, decidedAt |

Money is stored in cents as whole numbers so amounts are exact. Check these names against the field list in Deliverable 3 and change whichever document is out of date.

## Setup

You need Android Studio (Ladybug or newer), a Firebase project, and Node 18+ for the seed script.

1. **Firebase project.** In the Firebase console:
   - add an Android app with package name `com.itmda3b33.eats`
   - download `google-services.json` into `app/` (it is git-ignored; never commit it)
   - enable **Authentication → Email/Password**
   - create a **Cloud Firestore** database
   - open **AI Logic**, choose the **Gemini Developer API** and enable it. The API key stays in Firebase, not in this repository.
2. **Rules and indexes.** With the Firebase CLI: `firebase deploy --only firestore`. Or paste `firestore.rules` into the console and create the indexes in `firestore.indexes.json`.
3. **Seed data.** In the console: Project settings → Service accounts → Generate new private key. Keep the file outside the repository.
   ```bash
   cd seed
   npm install
   export GOOGLE_APPLICATION_CREDENTIALS=/path/outside/repo/service-account.json
   export SEED_PASSWORD='a-test-password'
   node seed_firestore.js
   ```
4. **Run.** Open the project in Android Studio, let Gradle sync, and run the `app` configuration.

Test accounts (all use `SEED_PASSWORD`): `thandi@eats.test` and `sipho@eats.test` (customers), `burgerspot@eats.test` (restaurant), `agent@eats.test`, `admin@eats.test`, `driver@eats.test`.

`node seed_firestore.js --reset-complaints` also clears complaints, messages and refunds so a demo can be repeated.

## Tests

The validation rules have JVM unit tests in `core/src/test`:

```bash
./gradlew :core:test
```

The tests cover each refund check and the order they run in, the R15 and R50 limits, rounding, complaint create, edit and delete rules, status changes, menu and sign-up fields, rand formatting, the "I want a human" detection, the stuck-turn guard and the stats figures.

### Manual test cases (signed in as thandi@eats.test)

| # | Do this | Expected |
|---|---|---|
| 1 | Open order 1001 → Chat → "my garlic dip is missing lol" | Complaint logged; R6,00 (50% of R12) refunded automatically; complaint resolved |
| 2 | Same order → "the chips were missing" | Chips are R35 (R15 to R50): escalated to an agent with a refund request |
| 3 | Order 1003 → "the salmon platter never came" | R189 is above R50: escalated |
| 4 | Order 1002 (out for delivery) → "my chilli oil is missing" | Blocked by the delivery check; the bot explains |
| 5 | Ask for the garlic dip refund again | Blocked by the duplicate check |
| 6 | "I found a hair in my food and feel sick" | safety_health: escalated immediately |
| 7 | Type "I want a human" at any point | Escalated at once, without Gemini |
| 8 | Ask about order-2001 (Sipho's) | Blocked by the ownership check |
| 9 | Turn off Wi-Fi and send a message | Error shown, "Talk to a person" offered |
| 10 | As agent@eats.test: take the chips complaint, approve the refund, resolve it | Refund approved with the agent's ID; complaint resolved |
| 11 | As burgerspot@eats.test: add, edit, mark sold out and delete an item | Each step shows feedback; customers see the change live |
| 12 | Try saving a menu item with price "abc" | Field error, nothing saved |

## Security notes

- No keys or passwords are in the repository: `google-services.json`, service account files and `SEED_PASSWORD` stay local.
- Gemini never writes to the database and never sets a refund amount.
- Automatic refunds are written by the customer's app after the checks pass, so `firestore.rules` caps their size at R7,50. A Cloud Function would remove the need for that cap, but it is outside this deliverable.

## Decisions still to confirm with the group

- The R15 to R50 range (escalating for now).
- Login uses **email** only. Mobile-number login needs Firebase phone auth (SMS) and is not built.
- Google and Apple sign-in from the earlier designs are not in the brief's scope, so they were left out.
- Update the scope statement in the final report to match the brief's build-scope table.
