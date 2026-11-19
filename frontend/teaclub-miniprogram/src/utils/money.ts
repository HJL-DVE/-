/**
 * 金额工具：后端一律「分」(整数)，展示时 /100。
 * 全程整数运算，禁止浮点累加。
 */

export function fen2yuan(fen: number | string | null | undefined): string {
  const n = typeof fen === 'string' ? Number(fen) : fen
  if (n === null || n === undefined || !isFinite(Number(n))) return '0.00'
  const cents = Math.trunc(Number(n))
  const sign = cents < 0 ? '-' : ''
  const abs = Math.abs(cents)
  const integer = Math.floor(abs / 100)
  const decimal = abs % 100
  return sign + String(integer) + '.' + String(decimal).padStart(2, '0')
}

export function yuanText(fen: number | string | null | undefined): string {
  return '¥' + fen2yuan(fen)
}

/** 元字符串 -> 分（整数）。仅用于「自定义充值金额」等输入，避免浮点乘法。 */
export function yuan2fen(yuan: string | number | null | undefined): number {
  if (yuan === null || yuan === undefined || yuan === '') return 0
  const s = String(yuan).trim()
  if (!/^-?\d+(\.\d{1,2})?$/.test(s)) return NaN
  const negative = s.startsWith('-')
  const body = negative ? s.slice(1) : s
  const parts = body.split('.')
  const integer = Number(parts[0] || '0')
  const decimalRaw = (parts[1] || '').padEnd(2, '0').slice(0, 2)
  const decimal = Number(decimalRaw || '0')
  const fen = integer * 100 + decimal
  return negative ? -fen : fen
}

/** 分求和（整数安全） */
export function sumFen(list: Array<number | null | undefined>): number {
  return list.reduce<number>((acc, cur) => acc + (Math.trunc(Number(cur ?? 0)) || 0), 0)
}
