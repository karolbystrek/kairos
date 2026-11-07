import { z } from "zod";

export const requiredEmailInputSchema = z
  .string()
  .trim()
  .min(1, "Podaj adres e-mail.")
  .max(200, "Adres e-mail może mieć maksymalnie 200 znaków.")
  .email("Podaj poprawny adres e-mail.")
  .transform((email) => email.toLowerCase());

export const passwordInputSchema = z
  .string()
  .min(1, "Podaj hasło.")
  .max(200, "Hasło może mieć maksymalnie 200 znaków.");
