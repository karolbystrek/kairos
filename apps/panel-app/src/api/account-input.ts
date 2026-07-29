import { z } from "zod";

export const usernameInputSchema = z
  .string()
  .trim()
  .min(1, "Username is required")
  .max(120, "Username must not exceed 120 characters")
  .transform((username) => username.toLowerCase());

export const requiredEmailInputSchema = z
  .string()
  .trim()
  .min(1, "Email is required")
  .max(254, "Email must not exceed 254 characters")
  .email("Email must be valid")
  .transform((email) => email.toLowerCase());

export const optionalEmailInputSchema = z
  .union([z.literal(""), requiredEmailInputSchema])
  .transform((email) => email || null);

export const passwordInputSchema = z
  .string()
  .min(12, "Password must contain at least 12 characters")
  .refine(
    (password) => new TextEncoder().encode(password).length <= 72,
    "Password is too long",
  );
