/**
 * Loads test data into Firebase for the complaint chatbot app.
 *
 * Creates one login per role, two restaurants with menus, and orders in every status.
 * Safe to run again: it overwrites the same document IDs instead of adding duplicates.
 *
 * Needs two environment variables, so no secrets live in the repository:
 *   GOOGLE_APPLICATION_CREDENTIALS  path to a service-account key file (Firebase console >
 *                                   Project settings > Service accounts > Generate new private key)
 *   SEED_PASSWORD                   the password to give every test account (at least 6 characters)
 *
 * Run:  cd seed && npm install && npm run seed
 */
const admin = require('firebase-admin');

const password = process.env.SEED_PASSWORD;
if (!process.env.GOOGLE_APPLICATION_CREDENTIALS || !password || password.length < 6) {
  console.error('Set GOOGLE_APPLICATION_CREDENTIALS and SEED_PASSWORD (6+ characters) first. See the comment at the top of this file.');
  process.exit(1);
}

admin.initializeApp({ credential: admin.credential.applicationDefault() });
const db = admin.firestore();
const auth = admin.auth();

// One test account per role. Emails use the reserved example.com domain.
const accounts = [
  { key: 'customer', name: 'Thandi Customer', email: 'customer@example.com', role: 'customer' },
  { key: 'customer2', name: 'Sipho Customer', email: 'customer2@example.com', role: 'customer' },
  { key: 'restaurant', name: 'Chicken Shack Owner', email: 'restaurant@example.com', role: 'restaurant' },
  { key: 'driver', name: 'Lebo Driver', email: 'driver@example.com', role: 'driver' },
  { key: 'agent', name: 'Ayesha Agent', email: 'agent@example.com', role: 'agent' },
  { key: 'admin', name: 'Admin User', email: 'admin@example.com', role: 'admin' },
];

/** Creates the login if it does not exist yet and returns its UID. */
async function ensureAccount({ name, email }) {
  try {
    const existing = await auth.getUserByEmail(email);
    await auth.updateUser(existing.uid, { password, displayName: name });
    return existing.uid;
  } catch (e) {
    if (e.code !== 'auth/user-not-found') throw e;
    const created = await auth.createUser({ email, password, displayName: name });
    return created.uid;
  }
}

// Prices are in cents. Chips (R12) are under the R15 auto-refund limit; the burger (R65) is above R50.
const menus = {
  'chicken-shack': {
    name: 'Chicken Shack',
    address: '12 Long Street, Cape Town',
    items: {
      chips: { name: 'Chips', description: 'Regular slap chips', priceCents: 1200 },
      coke: { name: 'Coke 330ml', description: 'Cold can', priceCents: 1500 },
      wings: { name: 'Wings (6)', description: 'Peri-peri wings', priceCents: 4500 },
      burger: { name: 'Chicken burger', description: 'Grilled fillet burger', priceCents: 6500 },
    },
  },
  'curry-corner': {
    name: 'Curry Corner',
    address: '8 Florida Road, Durban',
    items: {
      bunny: { name: 'Quarter bunny chow', description: 'Mutton', priceCents: 7500 },
      samoosa: { name: 'Samoosa', description: 'Beef, each', priceCents: 900 },
      roti: { name: 'Roti', description: 'Plain', priceCents: 1400 },
    },
  },
};

function line(restaurantId, itemId, quantity) {
  const item = menus[restaurantId].items[itemId];
  return { itemId, name: item.name, unitPriceCents: item.priceCents, quantity };
}

function order(id, customerId, restaurantId, status, items, daysAgo) {
  return {
    id,
    data: {
      customerId,
      restaurantId,
      restaurantName: menus[restaurantId].name,
      driverId: 'driver-1',
      status,
      items,
      totalCents: items.reduce((sum, i) => sum + i.unitPriceCents * i.quantity, 0),
      createdAt: Date.now() - daysAgo * 24 * 60 * 60 * 1000,
    },
  };
}

async function main() {
  const uid = {};
  for (const account of accounts) {
    uid[account.key] = await ensureAccount(account);
    await db.collection('users').doc(uid[account.key]).set({ name: account.name, email: account.email, role: account.role });
    console.log(`user   ${account.role.padEnd(10)} ${account.email}`);
  }

  for (const [restaurantId, menu] of Object.entries(menus)) {
    const ownerId = restaurantId === 'chicken-shack' ? uid.restaurant : '';
    await db.collection('restaurants').doc(restaurantId).set({ name: menu.name, address: menu.address, ownerId });
    for (const [itemId, item] of Object.entries(menu.items)) {
      await db.collection('restaurants').doc(restaurantId).collection('menuItems').doc(itemId).set({ ...item, available: true });
    }
    console.log(`menu   ${menu.name} (${Object.keys(menu.items).length} items)`);
  }

  await db.collection('drivers').doc('driver-1').set({ userId: uid.driver, name: 'Lebo Driver', vehicle: 'Scooter' });

  // Order IDs are short so they are easy to type in the chat during testing.
  const orders = [
    order('ORD1001', uid.customer, 'chicken-shack', 'delivered', [line('chicken-shack', 'chips', 1), line('chicken-shack', 'burger', 1), line('chicken-shack', 'coke', 1)], 0),
    order('ORD1002', uid.customer, 'curry-corner', 'delivered', [line('curry-corner', 'bunny', 1), line('curry-corner', 'samoosa', 3)], 2),
    order('ORD1003', uid.customer, 'chicken-shack', 'out_for_delivery', [line('chicken-shack', 'wings', 1), line('chicken-shack', 'chips', 1)], 0),
    order('ORD1004', uid.customer, 'curry-corner', 'preparing', [line('curry-corner', 'roti', 2)], 0),
    order('ORD1005', uid.customer, 'chicken-shack', 'cancelled', [line('chicken-shack', 'burger', 2)], 5),
    order('ORD2001', uid.customer2, 'chicken-shack', 'delivered', [line('chicken-shack', 'chips', 1)], 1),
  ];
  for (const o of orders) {
    await db.collection('orders').doc(o.id).set(o.data);
    console.log(`order  ${o.id} ${o.data.status}`);
  }

  console.log('\nDone. Sign in with any email above and the SEED_PASSWORD you set.');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
