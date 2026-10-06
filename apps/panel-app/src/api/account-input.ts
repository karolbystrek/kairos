import { z } from "zod";

export const requiredEmailInputSchema = z
  .string()
  .trim()
  .min(1, "Email is required")
  .max(200, "Email must not exceed 200 characters")
  .email("Email must be valid")
  .transform((email) => email.toLowerCase());

export const passwordInputSchema = z
  .string()
  .min(1, "Password is required")
  .max(200, "Password is too long");
