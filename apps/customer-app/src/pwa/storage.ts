import { z } from "zod";

import {
  isActiveOrderStatus,
  orderStatusSchema,
  type OrderStatus,
} from "@/src/orders/order-status";

const DATABASE_NAME = "kairos-customer";
const DATABASE_VERSION = 1;
const ORDER_STORE = "orders";
const METADATA_STORE = "metadata";
const EVENT_STORE = "push-events";
const TOMBSTONE_STORE = "tombstones";
const PUSH_FRESHNESS_MILLISECONDS = 10 * 60 * 1000;

const storedOrderSchema = z
  .object({
    trackingReference: z.string().min(1),
    label: z.string(),
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

export type StoredTrackedOrder = z.infer<typeof storedOrderSchema>;

export type StoredPushEvent = z.infer<typeof pushEventSchema>;

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

export type PushTransition = {
  eventId: string;
  status: OrderStatus;
  trackingReference: string;
  transitionedAt: string;
};

export type PushTransitionResult = {
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

export async function readTrackedOrders(): Promise<StoredTrackedOrder[]> {
  try {
    return await withDatabase(async (database) => {
      const transaction = database.transaction(ORDER_STORE, "readonly");
      const values = await requestResult(
        transaction.objectStore(ORDER_STORE).getAll(),
      );

      return values
        .map((value) => storedOrderSchema.safeParse(value))
        .filter((result) => result.success)
        .map((result) => result.data)
        .sort((left, right) => right.rememberedAt - left.rememberedAt);
    });
  } catch {
    return [];
  }
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
        [ORDER_STORE, TOMBSTONE_STORE, METADATA_STORE],
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

      if (isActiveOrderStatus(parsed.data.status)) {
        if (!terminalTombstoneIsCurrent) {
          orderStore.put(parsed.data);
          tombstoneStore.delete(parsed.data.trackingReference);
        }
      } else {
        orderStore.delete(parsed.data.trackingReference);
        tombstoneStore.put({
          trackingReference: parsed.data.trackingReference,
          mode: "terminal",
          status: parsed.data.status,
          transitionedAt: parsed.data.updatedAt,
          expiresAt: Date.now() + PUSH_FRESHNESS_MILLISECONDS,
        });
        const metadata = await readMetadataFromTransaction(transaction);

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
}

export async function pruneTerminalTrackedOrders(): Promise<
  StoredTrackedOrder[]
> {
  try {
    await withDatabase(async (database) => {
      const transaction = database.transaction(
        [ORDER_STORE, TOMBSTONE_STORE],
        "readwrite",
      );
      const orders = await requestResult(
        transaction.objectStore(ORDER_STORE).getAll(),
      );
      const now = Date.now();

      for (const candidate of orders) {
        const parsed = storedOrderSchema.safeParse(candidate);

        if (parsed.success && !isActiveOrderStatus(parsed.data.status)) {
          transaction
            .objectStore(ORDER_STORE)
            .delete(parsed.data.trackingReference);
          transaction.objectStore(TOMBSTONE_STORE).put({
            trackingReference: parsed.data.trackingReference,
            mode: "terminal",
            status: parsed.data.status,
            transitionedAt: parsed.data.updatedAt,
            expiresAt: now + PUSH_FRESHNESS_MILLISECONDS,
          });
        }
      }
      await transactionComplete(transaction);
    });
  } catch {
    return [];
  }

  return readTrackedOrders();
}

export async function clearTrackedOrders(): Promise<string[]> {
  try {
    return await withDatabase(async (database) => {
      const transaction = database.transaction(
        [ORDER_STORE, EVENT_STORE, TOMBSTONE_STORE, METADATA_STORE],
        "readwrite",
      );
      const orderStore = transaction.objectStore(ORDER_STORE);
      const values = await requestResult(orderStore.getAll());
      const trackingReferences = values
        .map((value) => storedOrderSchema.safeParse(value))
        .filter((result) => result.success)
        .map((result) => result.data.trackingReference);
      const tombstones = transaction.objectStore(TOMBSTONE_STORE);
      const expiresAt = Date.now() + PUSH_FRESHNESS_MILLISECONDS;

      orderStore.clear();
      transaction.objectStore(EVENT_STORE).clear();
      for (const trackingReference of trackingReferences) {
        tombstones.put({
          trackingReference,
          mode: "suppressed",
          expiresAt,
        });
      }
      await putMetadataInTransaction(transaction, {
        enrolledTrackingReferences: [],
      });
      await transactionComplete(transaction);

      return trackingReferences;
    });
  } catch {
    return [];
  }
}

export async function removeTrackedOrder(
  trackingReference: string,
  mode: "suppressed" | "terminal",
  status?: OrderStatus,
): Promise<void> {
  try {
    await withDatabase(async (database) => {
      const transaction = database.transaction(
        [ORDER_STORE, TOMBSTONE_STORE, METADATA_STORE],
        "readwrite",
      );

      transaction.objectStore(ORDER_STORE).delete(trackingReference);
      transaction.objectStore(TOMBSTONE_STORE).put({
        trackingReference,
        mode,
        status,
        expiresAt: Date.now() + PUSH_FRESHNESS_MILLISECONDS,
      });
      const metadata = await readMetadataFromTransaction(transaction);

      await putMetadataInTransaction(transaction, {
        enrolledTrackingReferences: (
          metadata.enrolledTrackingReferences ?? []
        ).filter((reference) => reference !== trackingReference),
      });
      await transactionComplete(transaction);
    });
  } catch {
    // Best effort local cleanup.
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
    const transaction = database.transaction(METADATA_STORE, "readwrite");
    const current = await readMetadataFromTransaction(transaction);
    const next = { ...current, ...update };

    transaction.objectStore(METADATA_STORE).put(next, "notification");
    await transactionComplete(transaction);

    return next;
  });
}

export async function applyPushTransition(
  transition: PushTransition,
): Promise<PushTransitionResult> {
  return withDatabase(async (database) => {
    const transaction = database.transaction(
      [ORDER_STORE, METADATA_STORE, EVENT_STORE, TOMBSTONE_STORE],
      "readwrite",
    );
    const events = transaction.objectStore(EVENT_STORE);
    const existingEvent = await requestResult(events.get(transition.eventId));
    const metadata = await readMetadataFromTransaction(transaction);

    await removeExpiredPushMetadata(transaction);
    if (existingEvent) {
      await transactionComplete(transaction);

      return {
        activeOrderCount: await countActiveOrders(database),
        shouldDisplay: false,
      };
    }
    events.put({
      ...transition,
      expiresAt: Date.now() + PUSH_FRESHNESS_MILLISECONDS,
    });
    if (metadata.notificationsEnabled !== true) {
      await transactionComplete(transaction);

      return {
        activeOrderCount: await countActiveOrders(database),
        shouldDisplay: false,
      };
    }
    const tombstoneValue = await requestResult(
      transaction
        .objectStore(TOMBSTONE_STORE)
        .get(transition.trackingReference),
    );
    const tombstoneResult = tombstoneSchema.safeParse(tombstoneValue);

    if (
      (tombstoneResult.success && tombstoneResult.data.mode === "suppressed") ||
      !(metadata.enrolledTrackingReferences ?? []).includes(
        transition.trackingReference,
      )
    ) {
      await transactionComplete(transaction);

      return {
        activeOrderCount: await countActiveOrders(database),
        shouldDisplay: false,
      };
    }
    if (
      tombstoneResult.success &&
      tombstoneResult.data.mode === "terminal" &&
      (isActiveOrderStatus(transition.status) ||
        tombstoneResult.data.status !== transition.status)
    ) {
      await transactionComplete(transaction);

      return {
        activeOrderCount: await countActiveOrders(database),
        shouldDisplay: false,
      };
    }
    const orderStore = transaction.objectStore(ORDER_STORE);
    const currentValue = await requestResult(
      orderStore.get(transition.trackingReference),
    );
    const currentResult = storedOrderSchema.safeParse(currentValue);

    if (
      currentResult.success &&
      !canAdvanceStatus(currentResult.data.status, transition.status)
    ) {
      await transactionComplete(transaction);

      return {
        activeOrderCount: await countActiveOrders(database),
        shouldDisplay: false,
      };
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
      orderStore.delete(transition.trackingReference);
      transaction.objectStore(TOMBSTONE_STORE).put({
        trackingReference: transition.trackingReference,
        mode: "terminal",
        status: transition.status,
        transitionedAt: transition.transitionedAt,
        expiresAt: Date.now() + PUSH_FRESHNESS_MILLISECONDS,
      });
      await putMetadataInTransaction(transaction, {
        enrolledTrackingReferences: (
          metadata.enrolledTrackingReferences ?? []
        ).filter((reference) => reference !== transition.trackingReference),
      });
    }
    await transactionComplete(transaction);

    return {
      activeOrderCount: await countActiveOrders(database),
      shouldDisplay: true,
    };
  });
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

  return values
    .map((value) => storedOrderSchema.safeParse(value))
    .filter(
      (result) =>
        result.success &&
        isActiveOrderStatus(result.data.status) &&
        enrolled.has(result.data.trackingReference),
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

  for (const storeName of [EVENT_STORE, TOMBSTONE_STORE]) {
    const store = transaction.objectStore(storeName);
    const values = await requestResult(store.getAll());

    for (const candidate of values) {
      const result =
        storeName === EVENT_STORE
          ? pushEventSchema.safeParse(candidate)
          : tombstoneSchema.safeParse(candidate);

      if (result.success && result.data.expiresAt <= now) {
        store.delete(
          storeName === EVENT_STORE
            ? (result.data as StoredPushEvent).eventId
            : result.data.trackingReference,
        );
      }
    }
  }
}

function canAdvanceStatus(
  current: OrderStatus,
  candidate: OrderStatus,
): boolean {
  if (current === candidate) {
    return false;
  }

  return (
    (current === "IN_PREPARATION" &&
      (candidate === "READY" ||
        candidate === "COMPLETED" ||
        candidate === "CANCELED")) ||
    (current === "READY" &&
      (candidate === "COMPLETED" || candidate === "CANCELED"))
  );
}

function isNotificationMetadata(value: unknown): value is NotificationMetadata {
  return typeof value === "object" && value !== null;
}
