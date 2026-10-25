const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_123";
const DRIVER_UID = "driver_456";
const STRANGER_UID = "stranger_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user: cannot read or list orders or staff", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("staff").get());
});

test("Authenticated user: cannot get another user's private order if not owner or assigned driver", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("orders").doc("order_1").set({
      ownerUid: OWNER_UID,
      companyCode: "DXP_POST",
      trackingCode: "DXP-1001",
      senderName: "Bazari Slemani",
      customerName: "Ako Ahmad",
      customerPhone: "07701234567",
      cityArea: "سلێمانی - بەختیاری",
      addressDetails: "نزیک قوتابخانەکە",
      notes: "",
      itemPriceIqd: 25000,
      deliveryFeeIqd: 4000,
      totalAmountIqd: 29000,
      status: "ASSIGNED",
      returnReason: "",
      assignedDriverId: DRIVER_UID,
      assignedDriverName: "Soran Driver",
      isSettled: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  await assertFails(strangerDb.collection("orders").doc("order_1").get());

  const driverDb = testEnv.authenticatedContext(DRIVER_UID).firestore();
  await assertSucceeds(driverDb.collection("orders").doc("order_1").get());
});

test("Assigned driver: can update status and returnReason, but cannot tamper with itemPriceIqd or ghost fields", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("orders").doc("order_2").set({
      ownerUid: OWNER_UID,
      companyCode: "DXP_POST",
      trackingCode: "DXP-1002",
      senderName: "Online Store",
      customerName: "Darya",
      customerPhone: "07501112233",
      cityArea: "هەولێر - عەنکاوە",
      addressDetails: "شەقامی سەرەکی",
      notes: "",
      itemPriceIqd: 50000,
      deliveryFeeIqd: 5000,
      totalAmountIqd: 55000,
      status: "ASSIGNED",
      returnReason: "",
      assignedDriverId: DRIVER_UID,
      assignedDriverName: "Soran Driver",
      isSettled: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const driverDb = testEnv.authenticatedContext(DRIVER_UID).firestore();
  // Allowed status update by assigned driver
  await assertSucceeds(
    driverDb.collection("orders").doc("order_2").update({
      status: "DELIVERED",
      returnReason: "",
      updatedAt: new Date(),
    })
  );

  // Disallowed price modification by driver
  await assertFails(
    driverDb.collection("orders").doc("order_2").update({
      itemPriceIqd: 0,
      updatedAt: new Date(),
    })
  );

  // Disallowed ghost field injection (Shadow Update Test)
  await assertFails(
    driverDb.collection("orders").doc("order_2").update({
      status: "DELIVERED",
      isVerifiedGhost: true,
      updatedAt: new Date(),
    })
  );
});

test("Client query alignment: filtered queries succeed while unfiltered list fails", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("orders").doc("order_3").set({
      ownerUid: OWNER_UID,
      companyCode: "DXP_POST",
      trackingCode: "DXP-1003",
      senderName: "Store",
      customerName: "Karwan",
      customerPhone: "07709998877",
      cityArea: "دهۆک",
      addressDetails: "ناوەند",
      notes: "",
      itemPriceIqd: 15000,
      deliveryFeeIqd: 3000,
      totalAmountIqd: 18000,
      status: "PENDING",
      returnReason: "",
      assignedDriverId: "",
      assignedDriverName: "",
      isSettled: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(
    ownerDb.collection("orders").where("companyCode", "==", "DXP_POST").get()
  );
  await assertSucceeds(
    ownerDb.collection("orders").where("ownerUid", "==", OWNER_UID).get()
  );
  await assertFails(ownerDb.collection("orders").get());
});
