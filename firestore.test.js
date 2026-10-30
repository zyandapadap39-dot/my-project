const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_angler_1";
const BOB_UID = "bob_angler_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read catches, sessions, or spots", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("catches").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(unauthDb.collection("spots").where("isShared", "==", true).get());
});

test("Authenticated user can create and read their own catch record", async () => {
  const aliceContext = testEnv.authenticatedContext(ALICE_UID);
  const aliceDb = aliceContext.firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("catches").doc("catch_1").set({
      userId: ALICE_UID,
      species: "Rainbow Trout",
      weightKg: 2.4,
      lengthCm: 54.0,
      caughtAtEpochMillis: Date.now(),
      locationName: "Emerald River Bend",
      baitOrLure: "Copper John Nymph",
      fishingMethod: "Fly Fishing",
      waterConditions: "Clear, cold",
      released: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("catches").doc("catch_1").get()
  );
});

test("Cross-user isolation: Bob cannot read or delete Alice's catches or sessions", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("catches").doc("catch_1").set({
      userId: ALICE_UID,
      species: "Largemouth Bass",
      weightKg: 3.1,
      lengthCm: 58.0,
      caughtAtEpochMillis: Date.now(),
      locationName: "Hidden Cove",
      baitOrLure: "Spinnerbait",
      fishingMethod: "Baitcasting",
      waterConditions: "Stained",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("catches").doc("catch_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("catches").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("catches").doc("catch_1").delete());
});

test("Shadow update test: Ghost field injection is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("catches").doc("catch_ghost").set({
      userId: ALICE_UID,
      species: "Northern Pike",
      weightKg: 5.0,
      lengthCm: 85.0,
      caughtAtEpochMillis: Date.now(),
      locationName: "North Bay",
      baitOrLure: "Jerkbait",
      fishingMethod: "Spinning",
      waterConditions: "Clear",
      isVerifiedAdmin: true, // Ghost field!
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Private spots are strictly hidden from other users; shared spots are readable", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("spots").doc("private_spot").set({
      userId: ALICE_UID,
      name: "Secret Honey Hole",
      description: "Deep submerged timber",
      waterBodyType: "Lake",
      latitude: 45.123,
      longitude: -121.456,
      isShared: false,
      isSuccessful: true,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("spots").doc("shared_spot").set({
      userId: ALICE_UID,
      name: "Public Boat Ramp",
      description: "Easy shore access",
      waterBodyType: "Lake",
      latitude: 45.2,
      longitude: -121.5,
      isShared: true,
      isSuccessful: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Bob CANNOT get or query Alice's private spot
  await assertFails(bobDb.collection("spots").doc("private_spot").get());
  await assertFails(bobDb.collection("spots").get()); // unfiltered list fails

  // Bob CAN query shared spots and get shared_spot, CANNOT update or delete it
  await assertSucceeds(bobDb.collection("spots").doc("shared_spot").get());
  await assertSucceeds(bobDb.collection("spots").where("isShared", "==", true).get());
  await assertFails(bobDb.collection("spots").doc("shared_spot").delete());

  // Alice CAN query her own spots (including private)
  await assertSucceeds(aliceDb.collection("spots").where("userId", "==", ALICE_UID).get());
});
