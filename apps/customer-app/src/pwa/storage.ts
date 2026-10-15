import { z } from "zod";

import {
  canApplyStatus,
  isActiveOrderStatus,
  orderStatusSchema,
  type OrderStatus,
} from "@/src/orders/order-status";

const DATABASE_NAME = "kairos-customer";
const DATABASE_VERSION = 2;
const REVIEW_STORE = "reviews";
const ORDER_STORE = "orders";
const METADATA_STORE = "metadata";
const EVENT_STORE = "push-events";
const TOMBSTONE_STORE = "tombstones";
const PUSH_FRESHNESS_MILLISECONDS = 10 * 60 * 1000;

const storedOrderSchema = z
  .object({
    trackingReference: z.string().min(1),
    number: z.number().int().positive(),
    label: z.string().nullable(),
    status: orderStatusSchema,
    updatedAt: z.iso.datetime({ offset: true }),
    rememberedAt: z.number().finite(),
  })
  .strict();

const tombstoneSchema = z
  .object({
    trackingReference: z.string().min(1),
    mode: z.enum(["terminal", "suppressed"]),
    status: orderStatusSchema.optional(),
    transitionedAt: z.iso.datetime({ offset: true }).optional(),
    notificationPending: z.boolean().optional(),
    expiresAt: z.number().finite(),
  })
  .strict();

const pushEventSchema = z
  .object({
    eventId: z.string().min(1),
    trackingReference: z.string().min(1),
    status: orderStatusSchema,
    transitionedAt: z.iso.datetime({ offset: true }),
    expiresAt: z.number().finite(),
  })
  .strict();

const stableCustomerDestinationSchema = z.discriminatedUnion("kind", [
  z.object({ kind: z.literal("home") }).strict(),
  z
    .object({
      kind: z.literal("order"),
      trackingReference: z.string().min(1),
    })
    .strict(),
]);

export type StoredTrackedOrder = z.infer<typeof storedOrderSchema>;

export type StableCustomerDestination = z.infer<
  typeof stableCustomerDestinationSchema
>;

export type NotificationMetadata = {
  enrolledTrackingReferences?: string[];
  notificationsEnabled?: boolean;
  pendingSubscriptionReplacement?: {
    current: SerializedPushSubscription;
    previous: SerializedPushSubscription;
  };
  registeredEndpoint?: string;
};

export type SerializedPushSubscription = {
  endpoint: string;
  expirationTime: string | null;
  keys: {
    auth: string;
    p256dh: string;
  };
};

type PushTransition = {
  eventId: string;
  status: OrderStatus;
  trackingReference: string;
  transitionedAt: string;
};

type PushTransitionResult = {
  activeOrderCount: number;
  shouldDisplay: boolean;
};

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function transactionComplete(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onabort = () =>
      reject(transaction.error ?? new Error("IndexedDB transaction aborted."));
    transaction.onerror = () =>
      reject(transaction.error ?? new Error("IndexedDB transaction failed."));
  });
}

function openDatabase(): Promise<IDBDatabase> {
  if (!("indexedDB" in globalThis)) {
    return Promise.reject(new Error("IndexedDB is unavailable."));
  }

  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);

    request.onupgradeneeded = () => {
      const database = request.result;

      if (!database.objectStoreNames.contains(REVIEW_STORE)) {
        database.createObjectStore(REVIEW_STORE, {
          keyPath: "trackingReference",
        });
      }
      if (!database.objectStoreNames.contains(ORDER_STORE)) {
        database.createObjectStore(ORDER_STORE, {
          keyPath: "trackingReference",
        });
      }
      if (!database.objectStoreNames.contains(METADATA_STORE)) {
        database.createObjectStore(METADATA_STORE);
      }
      if (!database.objectStoreNames.contains(EVENT_STORE)) {
        database.createObjectStore(EVENT_STORE, { keyPath: "eventId" });
      }
      if (!database.objectStoreNames.contains(TOMBSTONE_STORE)) {
        database.createObjectStore(TOMBSTONE_STORE, {
          keyPath: "trackingReference",
        });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    request.onblocked = () =>
      reject(new Error("IndexedDB upgrade is blocked by another Kairos page."));
  });
}

async function withDatabase<T>(
  work: (database: IDBDatabase) => Promise<T>,
): Promise<T> {
  const database = await openDatabase();

  try {
    return await work(database);
  } finally {
    database.close();
  }
}

function parseAll<T>(schema: z.ZodType<T>, values: unknown[]): T[] {
  return values.flatMap((value) => {
    const result = schema.safeParse(value);

    return result.success ? [result.data] : [];
  });
}

function putTerminalTombstone(
  transaction: IDBTransaction,
  tombstone: Omit<z.infer<typeof tombstoneSchema>, "mode" | "expiresAt">,
): void {
  transaction.objectStore(TOMBSTONE_STORE).put({
    ...tombstone,
    mode: "terminal",
    expiresAt: Date.now() + PUSH_FRESHNESS_MILLISECONDS,
  });
}

export async function readTrackedOrder(
  trackingReference: string,
): Promise<StoredTrackedOrder | null> {
  try {
    return await withDatabase(async (database) => {
      const transaction = database.transaction(ORDER_STORE, "readonly");
      const value = await requestResult(
        transaction.objectStore(ORDER_STORE).get(trackingReference),
      );
      const result = storedOrderSchema.safeParse(value);

      return result.success ? result.data : null;
    });
  } catch {
    return null;
  }
}

export async function readNotificationTrackingReferences(): Promise<string[]> {
  return withDatabase(async (database) => {
    const transaction = database.transaction(
      [ORDER_STORE, TOMBSTONE_STORE],
      "readonly",
    );
    const [orders, tombstones] = await Promise.all([
      requestResult(transaction.objectStore(ORDER_STORE).getAll()),
      requestResult(transaction.objectStore(TOMBSTONE_STORE).getAll()),
    ]);

    return [
      ...parseAll(storedOrderSchema, orders)
        .filter((order) => isActiveOrderStatus(order.status))
        .map((order) => order.trackingReference),
      ...parseAll(tombstoneSchema, tombstones)
        .filter(
          (tombstone) =>
            tombstone.mode === "terminal" &&
            tombstone.notificationPending === true &&
            tombstone.expiresAt > Date.now(),
        )
        .map((tombstone) => tombstone.trackingReference),
    ];
  });
}

export async function readLastStableDestination(): Promise<StableCustomerDestination | null> {
  try {
    return await withDatabase(async (database) => {
      const transaction = database.transaction(METADATA_STORE, "readonly");
      const value = await requestResult(
        transaction.objectStore(METADATA_STORE).get("last-destination"),
      );
      const result = stableCustomerDestinationSchema.safeParse(value);

      return result.success ? result.data : null;
    });
  } catch {
    return null;
  }
}

export async function rememberLastStableDestination(
  destination: StableCustomerDestination,
): Promise<void> {
  const parsed = stableCustomerDestinationSchema.safeParse(destination);

  if (!parsed.success) {
    return;
  }

  try {
    await withDatabase(async (database) => {
      const transaction = database.transaction(METADATA_STORE, "readwrite");

      transaction
        .objectStore(METADATA_STORE)
        .put(parsed.data, "last-destination");
      await transactionComplete(transaction);
    });
  } catch {
    // Launch restoration is best effort and never blocks order tracking.
  }
}

export async function rememberTrackedOrder(
  order: Omit<StoredTrackedOrder, "rememberedAt">,
): Promise<void> {
  const parsed = storedOrderSchema.safeParse({
    ...order,
    rememberedAt: Date.now(),
  });

  if (!parsed.success) {
    return;
  }

  try {
    await withDatabase(async (database) => {
      const transaction = database.transaction(
        [ORDER_STORE, TOMBSTONE_STORE, METADATA_STORE, REVIEW_STORE],
        "readwrite",
      );
      const orderStore = transaction.objectStore(ORDER_STORE);
      const tombstoneStore = transaction.objectStore(TOMBSTONE_STORE);
      const tombstoneValue = await requestResult(
        tombstoneStore.get(parsed.data.trackingReference),
      );
      const tombstoneResult = tombstoneSchema.safeParse(tombstoneValue);
      const terminalTombstoneIsCurrent =
        tombstoneResult.success &&
        tombstoneResult.data.mode === "terminal" &&
        tombstoneResult.data.expiresAt > Date.now();

      const currentResult = storedOrderSchema.safeParse(
        await requestResult(orderStore.get(parsed.data.trackingReference)),
      );

      if (
        (currentResult.success &&
          !canApplyStatus(currentResult.data.status, parsed.data.status)) ||
        (terminalTombstoneIsCurrent &&
          tombstoneResult.data.status &&
          !canApplyStatus(tombstoneResult.data.status, parsed.data.status))
      ) {
        await transactionComplete(transaction);

        return;
      }

      if (isActiveOrderStatus(parsed.data.status)) {
        if (!terminalTombstoneIsCurrent) {
          orderStore.put(parsed.data);
          tombstoneStore.delete(parsed.data.trackingReference);
        }
      } else {
        const metadata = await readMetadataFromTransaction(transaction);

        if (parsed.data.status === "COMPLETED") {
          await rememberReviewInTransaction(
            transaction,
            parsed.data.trackingReference,
          );
        }
        orderStore.delete(parsed.data.trackingReference);
        putTerminalTombstone(transaction, {
          trackingReference: parsed.data.trackingReference,
          status: parsed.data.status,
          transitionedAt: parsed.data.updatedAt,
          // REST can finish tracking before the final Push notification arrives.
          notificationPending:
            (metadata.notificationsEnabled === true &&
              (metadata.enrolledTrackingReferences ?? []).includes(
                parsed.data.trackingReference,
              )) ||
            (terminalTombstoneIsCurrent &&
              tombstoneResult.data.status === parsed.data.status &&
              tombstoneResult.data.notificationPending === true),
        });

        await putMetadataInTransaction(transaction, {
          enrolledTrackingReferences: (
            metadata.enrolledTrackingReferences ?? []
          ).filter((reference) => reference !== parsed.data.trackingReference),
        });
      }
      await transactionComplete(transaction);
    });
  } catch {
    // Explicit offline state is best effort and never blocks live tracking.
  }
  notifyReviewStorageChange();
}

export async function pruneTerminalTrackedOrders(): Promise<void> {
  try {
    await withDatabase(async (database) => {
      const transaction = database.transaction(
        [ORDER_STORE, TOMBSTONE_STORE],
        "readwrite",
      );
      const orders = await requestResult(
        transaction.objectStore(ORDER_STORE).getAll(),
      );

      for (const order of parseAll(storedOrderSchema, orders)) {
        if (!isActiveOrderStatus(order.status)) {
          transaction.objectStore(ORDER_STORE).delete(order.trackingReference);
          putTerminalTombstone(transaction, {
            trackingReference: order.trackingReference,
            status: order.status,
            transitionedAt: order.updatedAt,
          });
        }
      }
      await transactionComplete(transaction);
    });
  } catch {
    // Pruning is best effort; stale terminal orders are pruned on a later start.
  }
}

export async function readNotificationMetadata(): Promise<NotificationMetadata> {
  try {
    return await withDatabase(async (database) => {
      const transaction = database.transaction(METADATA_STORE, "readonly");
      const value = await requestResult(
        transaction.objectStore(METADATA_STORE).get("notification"),
      );

      return isNotificationMetadata(value) ? value : {};
    });
  } catch {
    return {};
  }
}

export async function updateNotificationMetadata(
  update: Partial<NotificationMetadata>,
): Promise<NotificationMetadata> {
  return withDatabase(async (database) => {
    const transaction = database.transaction(
      [METADATA_STORE, TOMBSTONE_STORE],
      "readwrite",
    );
    const current = await readMetadataFromTransaction(transaction);
    const next = { ...current, ...update };

    transaction.objectStore(METADATA_STORE).put(next, "notification");
    if (update.notificationsEnabled === false) {
      const tombstones = transaction.objectStore(TOMBSTONE_STORE);
      const values = await requestResult(tombstones.getAll());

      for (const value of values) {
        const result = tombstoneSchema.safeParse(value);

        if (result.success && result.data.notificationPending === true) {
          tombstones.put({ ...result.data, notificationPending: false });
        }
      }
    }
    await transactionComplete(transaction);

    return next;
  });
}

export async function applyPushTransition(
  transition: PushTransition,
): Promise<PushTransitionResult> {
  return withDatabase(async (database) => {
    const transaction = database.transaction(
      [ORDER_STORE, METADATA_STORE, EVENT_STORE, TOMBSTONE_STORE, REVIEW_STORE],
      "readwrite",
    );
    const shouldDisplay = await applyPushTransitionInTransaction(
      transaction,
      transition,
    );

    await transactionComplete(transaction);

    return {
      activeOrderCount: await countActiveOrders(database),
      shouldDisplay,
    };
  });
}

async function applyPushTransitionInTransaction(
  transaction: IDBTransaction,
  transition: PushTransition,
): Promise<boolean> {
  const events = transaction.objectStore(EVENT_STORE);
  const existingEvent = await requestResult(events.get(transition.eventId));
  const metadata = await readMetadataFromTransaction(transaction);

  await removeExpiredPushMetadata(transaction);
  if (existingEvent) return false;
  events.put({
    ...transition,
    expiresAt: Date.now() + PUSH_FRESHNESS_MILLISECONDS,
  });
  if (metadata.notificationsEnabled !== true) return false;
  const tombstoneValue = await requestResult(
    transaction.objectStore(TOMBSTONE_STORE).get(transition.trackingReference),
  );
  const tombstoneResult = tombstoneSchema.safeParse(tombstoneValue);
  const pendingTerminalNotification =
    tombstoneResult.success &&
    tombstoneResult.data.mode === "terminal" &&
    tombstoneResult.data.status === transition.status &&
    tombstoneResult.data.notificationPending === true;

  if (
    (tombstoneResult.success && tombstoneResult.data.mode === "suppressed") ||
    (!pendingTerminalNotification &&
      !(metadata.enrolledTrackingReferences ?? []).includes(
        transition.trackingReference,
      ))
  ) {
    return false;
  }
  if (
    tombstoneResult.success &&
    tombstoneResult.data.mode === "terminal" &&
    (isActiveOrderStatus(transition.status) ||
      tombstoneResult.data.status !== transition.status)
  ) {
    return false;
  }
  const orderStore = transaction.objectStore(ORDER_STORE);
  const currentResult = storedOrderSchema.safeParse(
    await requestResult(orderStore.get(transition.trackingReference)),
  );

  if (
    currentResult.success &&
    !canApplyStatus(currentResult.data.status, transition.status)
  ) {
    return false;
  }
  if (isActiveOrderStatus(transition.status)) {
    if (currentResult.success) {
      orderStore.put({
        ...currentResult.data,
        status: transition.status,
        updatedAt: transition.transitionedAt,
      });
    }
  } else {
    if (transition.status === "COMPLETED") {
      await rememberReviewInTransaction(
        transaction,
        transition.trackingReference,
      );
    }
    orderStore.delete(transition.trackingReference);
    putTerminalTombstone(transaction, {
      trackingReference: transition.trackingReference,
      status: transition.status,
      transitionedAt: transition.transitionedAt,
    });
    await putMetadataInTransaction(transaction, {
      enrolledTrackingReferences: (
        metadata.enrolledTrackingReferences ?? []
      ).filter((reference) => reference !== transition.trackingReference),
    });
  }

  return true;
}

export async function countActiveTrackedOrders(): Promise<number> {
  try {
    return await withDatabase(countActiveOrders);
  } catch {
    return 0;
  }
}

async function countActiveOrders(database: IDBDatabase): Promise<number> {
  const transaction = database.transaction(
    [ORDER_STORE, METADATA_STORE],
    "readonly",
  );
  const values = await requestResult(
    transaction.objectStore(ORDER_STORE).getAll(),
  );
  const metadata = await readMetadataFromTransaction(transaction);

  if (metadata.notificationsEnabled !== true) {
    return 0;
  }
  const enrolled = new Set(metadata.enrolledTrackingReferences ?? []);

  return parseAll(storedOrderSchema, values).filter(
    (order) =>
      isActiveOrderStatus(order.status) &&
      enrolled.has(order.trackingReference),
  ).length;
}

async function readMetadataFromTransaction(
  transaction: IDBTransaction,
): Promise<NotificationMetadata> {
  const value = await requestResult(
    transaction.objectStore(METADATA_STORE).get("notification"),
  );

  return isNotificationMetadata(value) ? value : {};
}

async function putMetadataInTransaction(
  transaction: IDBTransaction,
  update: Partial<NotificationMetadata>,
): Promise<void> {
  const current = await readMetadataFromTransaction(transaction);

  transaction
    .objectStore(METADATA_STORE)
    .put({ ...current, ...update }, "notification");
}

async function removeExpiredPushMetadata(
  transaction: IDBTransaction,
): Promise<void> {
  const now = Date.now();

  await deleteExpired(
    transaction.objectStore(EVENT_STORE),
    pushEventSchema,
    (event) => event.eventId,
    now,
  );
  await deleteExpired(
    transaction.objectStore(TOMBSTONE_STORE),
    tombstoneSchema,
    (tombstone) => tombstone.trackingReference,
    now,
  );
}

async function deleteExpired<T extends { expiresAt: number }>(
  store: IDBObjectStore,
  schema: z.ZodType<T>,
  keyOf: (record: T) => string,
  now: number,
): Promise<void> {
  for (const record of parseAll(schema, await requestResult(store.getAll()))) {
    if (record.expiresAt <= now) store.delete(keyOf(record));
  }
}

function isNotificationMetadata(value: unknown): value is NotificationMetadata {
  return typeof value === "object" && value !== null;
}

const reviewRecordSchema = z.object({
  trackingReference: z.uuid(),
  consumed: z.boolean(),
});

async function rememberReviewInTransaction(
  transaction: IDBTransaction,
  trackingReference: string,
) {
  const store = transaction.objectStore(REVIEW_STORE);
  const current = reviewRecordSchema.safeParse(
    await requestResult(store.get(trackingReference)),
  );

  if (!current.success) store.put({ trackingReference, consumed: false });
}

function notifyReviewStorageChange() {
  if (typeof window !== "undefined")
    window.dispatchEvent(new Event("kairos-reviews-changed"));
}

export async function rememberReviewReference(
  trackingReference: string,
): Promise<void> {
  if (!z.uuid().safeParse(trackingReference).success) return;
  await withDatabase(async (database) => {
    const transaction = database.transaction(REVIEW_STORE, "readwrite");

    await rememberReviewInTransaction(transaction, trackingReference);
    await transactionComplete(transaction);
  });
  notifyReviewStorageChange();
}

export async function readReviewReferences(): Promise<string[]> {
  return withDatabase(async (database) => {
    const transaction = database.transaction(
      [REVIEW_STORE, ORDER_STORE],
      "readonly",
    );
    const [reviews, orders] = await Promise.all([
      requestResult(transaction.objectStore(REVIEW_STORE).getAll()),
      requestResult(transaction.objectStore(ORDER_STORE).getAll()),
    ]);
    const records = parseAll(reviewRecordSchema, reviews);
    const consumed = new Set(
      records
        .filter((record) => record.consumed)
        .map((record) => record.trackingReference),
    );

    return Array.from(
      new Set([
        ...records
          .filter((record) => !record.consumed)
          .map((record) => record.trackingReference),
        ...parseAll(storedOrderSchema, orders).map(
          (order) => order.trackingReference,
        ),
      ]),
    ).filter((reference) => !consumed.has(reference));
  });
}

export async function consumeReviewInvitation(
  trackingReference: string,
): Promise<void> {
  await withDatabase(async (database) => {
    const transaction = database.transaction(REVIEW_STORE, "readwrite");

    transaction
      .objectStore(REVIEW_STORE)
      .put({ trackingReference, consumed: true });
    await transactionComplete(transaction);
  });
  notifyReviewStorageChange();
}
