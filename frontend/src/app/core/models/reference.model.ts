export type Market = 'AUSTRALIA' | 'SINGAPORE' | 'HONG_KONG' | 'INDIA' | 'MALAYSIA' | 'THAILAND';
export type CurrencyCode = 'AUD' | 'SGD' | 'HKD' | 'INR' | 'MYR' | 'THB' | 'USD';
export type ProductType = 'MOTOR' | 'HEALTH' | 'TRAVEL' | 'PROPERTY';

export const MARKETS: readonly Market[] = ['AUSTRALIA', 'SINGAPORE', 'HONG_KONG', 'INDIA', 'MALAYSIA', 'THAILAND'];
export const CURRENCIES: readonly CurrencyCode[] = ['AUD', 'SGD', 'HKD', 'INR', 'MYR', 'THB', 'USD'];
export const PRODUCT_TYPES: readonly ProductType[] = ['MOTOR', 'HEALTH', 'TRAVEL', 'PROPERTY'];

/** Turns enum constants such as HONG_KONG or INFO_REQUESTED into "Hong kong" / "Info requested". */
export function humanize(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const text = value.replaceAll('_', ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}
