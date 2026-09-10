export function isScoreWithinRange(score: number | undefined, maximum: number): boolean {
  return typeof score === 'number' && score >= 0 && score <= maximum
}
