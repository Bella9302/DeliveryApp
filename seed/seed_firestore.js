/*
 * Seed script for the eats Firestore database. Loads fake users, restaurants, menus, drivers and orders.
 * No real payments: amounts are only numbers in cents.
 *
 * Usage (from this folder):
 *   npm install
 *   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json   # never commit this file
 *   export SEED_PASSWORD='choose-a-test-password'                        # used for every test account
 *   node seed_firestore.js                      # create or refresh all test data
 *   node seed_firestore.js --reset-complaints   # also delete all complaints, messages and refunds
 */
const admin = require('firebase-admin');

admin.initializeApp({ credential: admin.credential.applicationDefault() });
const db = admin.firestore();
const auth = admin.auth();
const { Timestamp } = admin.firestore;

const PASSWORD = process.env.SEED_PASSWORD;
if (!PASSWORD || PASSWORD.length < 8) {
  console.error('Set SEED_PASSWORD to a test password of at least 8 characters.');
  process.exit(1);
}

// Test accounts. The email domain is reserved for testing, so no real inbox receives mail.
const USERS = [
  { key: 'thandi', email: 'thandi@eats.test', firstName: 'Thandi', lastName: 'Mokoena', mobile: '+27821234567', role: 'customer' },
  { key: 'sipho', email: 'sipho@eats.test', firstName: 'Sipho', lastName: 'Dlamini', mobile: '+27831234567', role: 'customer' },
  { key: 'burgerOwner', email: 'burgerspot@eats.test', firstName: 'Lerato', lastName: 'Nkosi', mobile: '+27841234567', role: 'restaurant', restaurantId: 'burger-spot' },
  { key: 'agent', email: 'agent@eats.test', firstName: 'Ayesha', lastName: 'Patel', mobile: '+27851234567', role: 'agent' },
  { key: 'admin', email: 'admin@eats.test', firstName: 'Johan', lastName: 'van Wyk', mobile: '+27861234567', role: 'admin' },
  { key: 'driver', email: 'driver@eats.test', firstName: 'Musa', lastName: 'Zulu', mobile: '+27871234567', role: 'driver' },
];

// Prices in cents. Cheap items (under R15) show the automatic refund; the rest show escalation.
const RESTAURANTS = [
  {
    id: 'burger-spot', name: 'Burger Spot', cuisine: 'Burgers', etaMinMinutes: 20, etaMaxMinutes: 30, rating: 4.5, owner: 'burgerOwner',
    menu: [
      { id: 'classic-burger', name: 'Classic Burger', description: 'Beef patty, lettuce, tomato', priceCents: 7900 },
      { id: 'chips', name: 'Chips', description: 'Large portion', priceCents: 3500 },
      { id: 'milkshake', name: 'Milkshake', description: 'Vanilla, chocolate or strawberry', priceCents: 4500 },
      { id: 'garlic-dip', name: 'Garlic dip', description: 'Creamy garlic sauce', priceCents: 1200 },
      { id: 'extra-sauce', name: 'Extra sauce', description: 'Tomato or BBQ', priceCents: 800 },
    ],
  },
  {
    id: 'pizza-corner', name: 'Pizza Corner', cuisine: 'Pizza', etaMinMinutes: 25, etaMaxMinutes: 35, rating: 4.3,
    menu: [
      { id: 'margherita', name: 'Margherita', description: 'Tomato, mozzarella, basil', priceCents: 8900 },
      { id: 'garlic-bread', name: 'Garlic bread', description: 'Four slices', priceCents: 3200 },
      { id: 'chilli-oil', name: 'Chilli oil', description: 'Small tub', priceCents: 900 },
    ],
  },
  {
    id: 'sushi-house', name: 'Sushi House', cuisine: 'Sushi', etaMinMinutes: 30, etaMaxMinutes: 40, rating: 4.7,
    menu: [
      { id: 'salmon-platter', name: 'Salmon platter', description: '24 pieces', priceCents: 18900 },
      { id: 'miso-soup', name: 'Miso soup', description: 'With tofu', priceCents: 2800 },
      { id: 'edamame', name: 'Edamame', description: 'Salted', priceCents: 1400 },
    ],
  },
  {
    id: 'spice-route', name: 'Spice Route', cuisine: 'Indian', etaMinMinutes: 35, etaMaxMinutes: 45, rating: 4.4,
    menu: [
      { id: 'butter-chicken', name: 'Butter chicken', description: 'With basmati rice', priceCents: 11500 },
      { id: 'naan', name: 'Garlic naan', description: 'One piece', priceCents: 2500 },
      { id: 'raita', name: 'Raita', description: 'Cucumber yoghurt', priceCents: 1300 },
    ],
  },
];

const hoursAgo = (h) => Timestamp.fromDate(new Date(Date.now() - h * 3600 * 1000));

function line(restaurantId, itemId, quantity) {
  const item = RESTAURANTS.find((r) => r.id === restaurantId).menu.find((m) => m.id === itemId);
  return { menuItemId: item.id, name: item.name, priceCents: item.priceCents, quantity };
}

// Orders cover every status so tracking, the delivery check and ownership can be tested.
function orders(uids) {
  return [
    { id: 'order-1001', customer: 'thandi', restaurantId: 'burger-spot', status: 'delivered', placed: 3, delivered: 2.3,
      items: [line('burger-spot', 'classic-burger', 1), line('burger-spot', 'chips', 1), line('burger-spot', 'garlic-dip', 2), line('burger-spot', 'milkshake', 1)] },
    { id: 'order-1002', customer: 'thandi', restaurantId: 'pizza-corner', status: 'out_for_delivery', placed: 0.5,
      items: [line('pizza-corner', 'margherita', 1), line('pizza-corner', 'chilli-oil', 1)] },
    { id: 'order-1003', customer: 'thandi', restaurantId: 'sushi-house', status: 'delivered', placed: 26, delivered: 25.2,
      items: [line('sushi-house', 'salmon-platter', 1), line('sushi-house', 'edamame', 1)] },
    { id: 'order-1004', customer: 'thandi', restaurantId: 'spice-route', status: 'cancelled', placed: 50,
      items: [line('spice-route', 'butter-chicken', 1)] },
    { id: 'order-1005', customer: 'thandi', restaurantId: 'burger-spot', status: 'preparing', placed: 0.2,
      items: [line('burger-spot', 'classic-burger', 2), line('burger-spot', 'extra-sauce', 1)] },
    { id: 'order-2001', customer: 'sipho', restaurantId: 'spice-route', status: 'delivered', placed: 5, delivered: 4.2,
      items: [line('spice-route', 'butter-chicken', 1), line('spice-route', 'naan', 2), line('spice-route', 'raita', 1)] },
  ].map((o) => ({
    id: o.id,
    data: {
      customerId: uids[o.customer],
      restaurantId: o.restaurantId,
      restaurantName: RESTAURANTS.find((r) => r.id === o.restaurantId).name,
      driverId: o.status === 'placed' ? null : uids.driver,
      status: o.status,
      items: o.items,
      totalCents: o.items.reduce((sum, i) => sum + i.priceCents * i.quantity, 0),
      createdAt: hoursAgo(o.placed),
      deliveredAt: o.delivered ? hoursAgo(o.delivered) : null,
    },
  }));
}

/** Creates the Auth account if needed (or resets its password) and returns the user ID. */
async function ensureAuthUser(u) {
  try {
    const existing = await auth.getUserByEmail(u.email);
    await auth.updateUser(existing.uid, { password: PASSWORD, displayName: `${u.firstName} ${u.lastName}` });
    return existing.uid;
  } catch (e) {
    if (e.code !== 'auth/user-not-found') throw e;
    const created = await auth.createUser({ email: u.email, password: PASSWORD, displayName: `${u.firstName} ${u.lastName}` });
    return created.uid;
  }
}

async function deleteCollection(path) {
  const snap = await db.collection(path).get();
  for (const doc of snap.docs) {
    await db.recursiveDelete(doc.ref);
  }
  return snap.size;
}

async function main() {
  const uids = {};
  for (const u of USERS) {
    uids[u.key] = await ensureAuthUser(u);
    const profile = { firstName: u.firstName, lastName: u.lastName, email: u.email, mobile: u.mobile, role: u.role, createdAt: Timestamp.now() };
    if (u.restaurantId) profile.restaurantId = u.restaurantId;
    await db.collection('users').doc(uids[u.key]).set(profile);
  }
  console.log(`Users: ${USERS.length}`);

  for (const r of RESTAURANTS) {
    const ref = db.collection('restaurants').doc(r.id);
    await ref.set({
      name: r.name, cuisine: r.cuisine, etaMinMinutes: r.etaMinMinutes, etaMaxMinutes: r.etaMaxMinutes, rating: r.rating,
      ownerId: r.owner ? uids[r.owner] : null,
    });
    for (const m of r.menu) {
      await ref.collection('menuItems').doc(m.id).set({ name: m.name, description: m.description, priceCents: m.priceCents, available: true });
    }
  }
  console.log(`Restaurants: ${RESTAURANTS.length}`);

  await db.collection('drivers').doc(uids.driver).set({ name: 'Musa Zulu', phone: '+27871234567', vehicle: 'Scooter' });

  const list = orders(uids);
  for (const o of list) await db.collection('orders').doc(o.id).set(o.data);
  console.log(`Orders: ${list.length}`);

  if (process.argv.includes('--reset-complaints')) {
    const complaints = await deleteCollection('complaints');
    const refunds = await deleteCollection('refunds');
    console.log(`Deleted ${complaints} complaints and ${refunds} refunds.`);
  }

  console.log('Done. Sign in with any *@eats.test account and SEED_PASSWORD.');
}

main().then(() => process.exit(0)).catch((e) => {
  console.error(e);
  process.exit(1);
});
