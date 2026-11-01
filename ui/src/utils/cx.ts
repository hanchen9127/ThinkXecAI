/** Joins class names, skipping the ones a condition turned off: `cx('a', on && 'b')`. */
export function cx(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(' ')
}
