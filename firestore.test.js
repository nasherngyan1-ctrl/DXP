const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_1_UID = "admin_1_uid";
const ADMIN_2_UID = "admin_2_uid";
const DRIVER_1_UID = "driver_1_uid";
const DRIVER_2_UID = "driver_2_uid";
const STRANGER_UID = "stranger_uid";

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

async function seedAdminAndDrivers() {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("staff").doc(ADMIN_1_UID).set({
      ownerUid: ADMIN_1_UID,
      companyCode: "DXP_POST",
      name: "Admin 1",
      phone: "07501112233",
      email: "admin1@dxppost.com",
      role: "OWNER",
      vehicleInfo: "HQ",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("staff").doc(ADMIN_2_UID).set({
      ownerUid: ADMIN_1_UID,
      companyCode: "DXP_POST",
      name: "Admin 2",
      phone: "07501112244",
      email: "admin2@dxppost.com",
      role: "OWNER",
      vehicleInfo: "HQ",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("staff").doc(DRIVER_1_UID).set({
      ownerUid: DRIVER_1_UID,
      companyCode: "DXP_POST",
      name: "Driver 1",
      phone: "07701112233",
      email: "driver1@dxppost.com",
      role: "DRIVER",
      vehicleInfo: "Motorcycle - Erbil",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated user: cannot read or list orders, staff, or settlements", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("staff").get());
  await assertFails(unauthDb.collection("settlements").get());
});

test("Role security: Driver cannot self-register as OWNER or escalate role to OWNER", async () => {
  await seedAdminAndDrivers();
  const now = new Date();
  const newDriverDb = testEnv.authenticatedContext(DRIVER_2_UID).firestore();

  // Driver cannot self-register as OWNER
  await assertFails(
    newDriverDb.collection("staff").doc(DRIVER_2_UID).set({
      ownerUid: DRIVER_2_UID,
      companyCode: "DXP_POST",
      name: "Fake Owner",
      phone: "07709998877",
      email: "driver2@dxppost.com",
      role: "OWNER",
      vehicleInfo: "Car",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Driver can self-register as DRIVER
  await assertSucceeds(
    newDriverDb.collection("staff").doc(DRIVER_2_UID).set({
      ownerUid: DRIVER_2_UID,
      companyCode: "DXP_POST",
      name: "Real Driver 2",
      phone: "07709998877",
      email: "driver2@dxppost.com",
      role: "DRIVER",
      vehicleInfo: "Car",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Driver cannot escalate own role to OWNER on update
  await assertFails(
    newDriverDb.collection("staff").doc(DRIVER_2_UID).update({
      role: "OWNER",
      updatedAt: new Date(),
    })
  );
});

test("Order & Driver Assignment Security: Only Admins can create orders, assign drivers, and manage accounting", async () => {
  await seedAdminAndDrivers();
  const now = new Date();
  const admin1Db = testEnv.authenticatedContext(ADMIN_1_UID).firestore();
  const admin2Db = testEnv.authenticatedContext(ADMIN_2_UID).firestore();
  const driver1Db = testEnv.authenticatedContext(DRIVER_1_UID).firestore();

  const orderPayload = {
    ownerUid: ADMIN_1_UID,
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
    status: "PENDING",
    returnReason: "",
    assignedDriverId: "",
    assignedDriverName: "",
    isSettled: false,
    createdAt: now,
    updatedAt: now,
  };

  // Driver cannot create an order
  await assertFails(
    driver1Db.collection("orders").doc("ord_driver_attempt").set({
      ...orderPayload,
      ownerUid: DRIVER_1_UID,
    })
  );

  // Admin 1 can create an order
  await assertSucceeds(
    admin1Db.collection("orders").doc("order_1").set(orderPayload)
  );

  // Driver 1 cannot assign themselves to an unassigned order
  await assertFails(
    driver1Db.collection("orders").doc("order_1").update({
      assignedDriverId: DRIVER_1_UID,
      assignedDriverName: "Driver 1",
      status: "ASSIGNED",
      updatedAt: new Date(),
    })
  );

  // Admin 2 can assign Driver 1 to the order
  await assertSucceeds(
    admin2Db.collection("orders").doc("order_1").update({
      assignedDriverId: DRIVER_1_UID,
      assignedDriverName: "Driver 1",
      status: "ASSIGNED",
      updatedAt: new Date(),
    })
  );
});

test("Assigned Driver: can only access own assigned orders and update status, cannot tamper with prices or settlements", async () => {
  await seedAdminAndDrivers();
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("orders").doc("order_drv1").set({
      ownerUid: ADMIN_1_UID,
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
      assignedDriverId: DRIVER_1_UID,
      assignedDriverName: "Driver 1",
      isSettled: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("orders").doc("order_drv2").set({
      ownerUid: ADMIN_1_UID,
      companyCode: "DXP_POST",
      trackingCode: "DXP-1003",
      senderName: "Online Store",
      customerName: "Karwan",
      customerPhone: "07509998877",
      cityArea: "دهۆک",
      addressDetails: "ناوەند",
      notes: "",
      itemPriceIqd: 30000,
      deliveryFeeIqd: 4000,
      totalAmountIqd: 34000,
      status: "ASSIGNED",
      returnReason: "",
      assignedDriverId: DRIVER_2_UID,
      assignedDriverName: "Driver 2",
      isSettled: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const admin1Db = testEnv.authenticatedContext(ADMIN_1_UID).firestore();
  const driver1Db = testEnv.authenticatedContext(DRIVER_1_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();

  // Stranger cannot read order_drv1
  await assertFails(strangerDb.collection("orders").doc("order_drv1").get());

  // Driver 1 cannot read Driver 2's order
  await assertFails(driver1Db.collection("orders").doc("order_drv2").get());

  // Driver 1 cannot list all company orders without filtering by assignedDriverId
  await assertFails(
    driver1Db.collection("orders").where("companyCode", "==", "DXP_POST").get()
  );

  // Driver 1 can list ONLY orders assigned to Driver 1
  await assertSucceeds(
    driver1Db
      .collection("orders")
      .where("assignedDriverId", "==", DRIVER_1_UID)
      .get()
  );

  // Admin 1 can list all company orders
  await assertSucceeds(
    admin1Db.collection("orders").where("companyCode", "==", "DXP_POST").get()
  );

  // Driver 1 can update status to DELIVERED on own assigned order
  await assertSucceeds(
    driver1Db.collection("orders").doc("order_drv1").update({
      status: "DELIVERED",
      returnReason: "",
      updatedAt: new Date(),
    })
  );

  // Driver 1 cannot mark order as settled or alter itemPriceIqd
  await assertFails(
    driver1Db.collection("orders").doc("order_drv1").update({
      isSettled: true,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    driver1Db.collection("orders").doc("order_drv1").update({
      itemPriceIqd: 0,
      totalAmountIqd: 5000,
      updatedAt: new Date(),
    })
  );

  // Driver 1 cannot create a cash settlement record
  await assertFails(
    driver1Db.collection("settlements").doc("stl_1").set({
      ownerUid: DRIVER_1_UID,
      companyCode: "DXP_POST",
      driverId: DRIVER_1_UID,
      driverName: "Driver 1",
      ordersCount: 1,
      itemPriceTotalIqd: 50000,
      deliveryFeeTotalIqd: 5000,
      totalSettledCashIqd: 55000,
      notes: "Fake settlement",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Admin 1 can settle the order and record a CashSettlement
  await assertSucceeds(
    admin1Db.collection("orders").doc("order_drv1").update({
      isSettled: true,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    admin1Db.collection("settlements").doc("stl_1").set({
      ownerUid: ADMIN_1_UID,
      companyCode: "DXP_POST",
      driverId: DRIVER_1_UID,
      driverName: "Driver 1",
      ordersCount: 1,
      itemPriceTotalIqd: 50000,
      deliveryFeeTotalIqd: 5000,
      totalSettledCashIqd: 55000,
      notes: "Settled cash with Driver 1",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Once settled, Driver 1 cannot revert or modify the order status
  await assertFails(
    driver1Db.collection("orders").doc("order_drv1").update({
      status: "RETURNED",
      returnReason: "Late change attempt",
      updatedAt: new Date(),
    })
  );
});
