# DeliveryApp: Complaint Chatbot

Group ITMDA3-B33, Deliverable 4: Implementation.

An Android app where customers report problems with a food order to an AI chatbot. Gemini
understands the message, and **our code checks the rules and decides the outcome**. Small, valid
complaints are resolved automatically. Everything else goes to a human agent, and "I want a human"
always works straight away.

## Architecture

Four layers, each in its own package under `app/src/main/java/com/itmda3b33/deliveryapp/`:

| Package | Job | Rule it follows |
|---|---|---|
| `ui` | Compose screens, navigation, view models | Shows screens. Holds no business rules. |
| `ai` | Talks to Gemini through Firebase AI Logic | No database access. Only proposes actions. |
| `validation` | `ActionValidator`, `ActionGateway`, `RefundPolicy`, `LoopGuard`, `EditRules` | Every action passes here before it runs. |
| `data` | Firebase Auth and Firestore repositories, data models | The only code that reads and writes Firestore. |

`AppContainer` wires the layers together. `MainActivity` and `DeliveryApp` start the app.

### How one message flows

1. `ChatbotService` (ai) sends the customer's message and the system prompt to Gemini.
2. Gemini replies with a function call, for example `lookupOrder`.
3. `FunctionCallParser` turns it into a `ProposedAction` and hands it to `ActionGateway` (validation).
4. The gateway loads what the rules need from the repositories (data) and asks `ActionValidator` for a `Decision`.
5. The action runs, is blocked or is escalated. The result goes back to Gemini.
6. Gemini turns the result into a reply for the customer.

### Chatbot functions

`lookupOrder`, `createComplaint`, `issueRefund`, `escalateToAgent`, declared in `ai/ChatFunctions.kt`.
`issueRefund` has **no amount parameter**: `RefundPolicy` works out the amount.

### Validation rules (`validation/ActionValidator.kt`)

Checked in this order. The first failure blocks or escalates the action.

| Check | Rule |
|---|---|
| Ownership | The order (and complaint) must belong to the logged-in customer. |
| Delivery | Missing-item claims need an order with status `delivered`. |
| Duplicate | An item with an approved, pending or completed refund cannot be refunded again. A rejected refund does not count. |
| Well-formed | Category, description, item and quantity are present and match the order. |
| Amount and escalation | Missing item worth **under R15**: refund 50% automatically, `approvedBy = system`. **R15 and above**: escalate (see decisions below). Safety/health issues and every other kind of complaint: escalate. |

### Never stuck in a loop

- The chat screen has a **Talk to a person** button. It calls `escalateToAgent` directly, without Gemini, so it works even if Gemini is down.
- Messages like "I want a human" or "let me speak to someone" are caught by `HumanRequest` before they reach Gemini.
- `LoopGuard`: after **2 blocked or failed actions in a row**, the gateway hands the chat to an agent automatically.
- If Gemini keeps calling functions (more than 5 rounds for one message), the chat is handed over.
- Once escalated, the chat closes and the customer is pointed to **My complaints**.

## Decisions on the [Confirm] items

| Item | Decision in this code | Where to change it |
|---|---|---|
| R15 to R50 missing items | Escalated to an agent (safest choice). | `RefundPolicy.autoRefundMiddleBand = true` to auto-refund instead. |
| Exactly R15 | Escalated, because the brief says "under R15". | `RefundPolicy.autoRefundBelowCents` |
| Status values | Exactly the lists in brief section 7, as enums in `data/model/Statuses.kt`. | That file |
| Refund approval | Inside the limit: `approved`, `approvedBy = "system"`. Above it: `requested`, then an agent approves or rejects and their user ID is saved as `approvedBy`. | `ActionGateway`, `AgentViewModel` |
| Amount suggested to agents | The same 50% of the missing item's value. | `RefundPolicy.refundFor` |

Other choices the group should know about:

- **Money is stored in cents** (`priceCents`, `unitPriceCents`, `amountCents`) so refund maths has no rounding errors. Update Deliverable 3's field list if it uses rands.
- Complaint types: `missing_item`, `wrong_item`, `late_delivery`, `food_quality`, `safety_health`, `other`. Only `missing_item` can be resolved without a person.
- Customers can edit or cancel a complaint only while it is `logged`.

## Build scope (matches brief section 8)

| Built | Screen / file |
|---|---|
| Sign-up, login, logout, password reset | `ui/auth` |
| Role-based screens | `ui/home/HomeScreen.kt`, `RoleGate` in `ui/AppNavHost.kt` |
| Chat screen with Gemini | `ui/chat`, `ai` |
| Module A: Complaints (create through chat, read, update, delete) | `ui/complaints` |
| Module B: Restaurant menu (create, read, update, delete) | `ui/menu` |
| Order tracking (read) | `ui/orders` |
| Refunds, escalation, agent screen | `validation/ActionGateway.kt`, `ui/agent` |
| Stats screen | `ui/stats` |

Not built (build if time allows): placing orders from the app, courier screens, admin override of bot
decisions. Drivers can sign in but have no screens yet. Out of scope: real payments, route
optimisation, driver payroll, inventory.

## Setup

1. **Firebase project.** In the [Firebase console](https://console.firebase.google.com), create a
   project and add an Android app with package name `com.itmda3b33.deliveryapp`.
   - Download `google-services.json` into `app/`. It is in `.gitignore`. **Never commit it.**
   - Authentication: enable **Email/Password**.
   - Firestore: create a database.
   - AI Logic: open **AI Logic**, choose **Gemini Developer API** and follow the steps. The API key
     stays in Firebase, not in this repository.
2. **Security rules.** `npm install -g firebase-tools`, then `firebase login`,
   `firebase use <project-id>` and `firebase deploy --only firestore:rules`.
3. **Test data.** In the console, go to Project settings > Service accounts > Generate new private
   key. Save the key **outside** the repository, then run:
   ```bash
   cd seed
   npm install
   GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json SEED_PASSWORD='choose-one' npm run seed
   ```
   This creates `customer@example.com`, `restaurant@example.com`, `agent@example.com`,
   `admin@example.com`, `driver@example.com` and `customer2@example.com`, all with your
   `SEED_PASSWORD`. It also adds two restaurants and orders `ORD1001` to `ORD1005`.
4. **Run.** Open the project in Android Studio and run the `app` configuration. If Gradle sync
   reports a version that cannot be found, accept Android Studio's suggested update in
   `gradle/libs.versions.toml`.

## Test cases to try in the app

Sign in as `customer@example.com`:

| Message | Expected result |
|---|---|
| "my chips r missing lol, order ORD1001" | 1 chips (R12) missing: auto-refund of **R6.00**, complaint resolved. |
| Same again | Blocked as a duplicate. |
| "the burger from ORD1001 is missing" | Burger (R65) is above R50: escalated. A refund of R32.50 waits for an agent. |
| "2 samoosas missing from ORD1002" | R18, in the R15 to R50 band: escalated. |
| "chips missing from ORD1003" | Blocked: the order is still out for delivery. |
| "chips missing from ORD2001" | Blocked: not your order. |
| "I found glass in my food, ORD1002" | Safety issue: escalated. |
| "I want a human", or tap **Talk to a person** | Escalated straight away, without Gemini. |

Then sign in as `agent@example.com` to approve or reject the waiting refunds, and as `admin@example.com` to see the stats.

## Unit tests

`app/src/test` covers every validation rule and the order they run in, the refund limits, the
gateway (with in-memory fake repositories), the loop guard, "I want a human" detection and Gemini
argument parsing. Run them with `./gradlew test` or from Android Studio.

## Security notes

- No API keys or passwords are in the repository: `google-services.json`, service-account keys and
  `.env` files are ignored, and the seed script reads its password from an environment variable.
- `firestore.rules` gives each role only the access it needs. New sign-ups are always customers.
- Known limitation: refunds are written by the app, not by a server. The rules only let a customer
  create an `approved` refund of R7.50 or less with `approvedBy = "system"`. Anything larger must be
  `requested`, and only an agent can approve it. Moving refunds to Cloud Functions would close the gap.
