const test = require('node:test')
const assert = require('node:assert/strict')

const { selectStableLocation } = require('../utils/api')

test('selects the most accurate point from the stable cluster and ignores one distant outlier', () => {
  const stableBest = { latitude: 28.22781, longitude: 112.93882, accuracy: 8 }
  const selected = selectStableLocation([
    { latitude: 28.22780, longitude: 112.93880, accuracy: 20 },
    stableBest,
    { latitude: 28.22778, longitude: 112.93885, accuracy: 15 },
    { latitude: 28.33500, longitude: 112.93880, accuracy: 3 },
  ])

  assert.equal(selected, stableBest)
})

test('rejects split samples instead of reporting that the facility is too far away', () => {
  assert.throws(() => selectStableLocation([
    { latitude: 28.22780, longitude: 112.93880, accuracy: 20 },
    { latitude: 28.22782, longitude: 112.93882, accuracy: 18 },
    { latitude: 28.23780, longitude: 112.93880, accuracy: 15 },
    { latitude: 28.23782, longitude: 112.93882, accuracy: 12 },
  ]), error => error.code === 'LOCATION_UNSTABLE' && error.canRetryLocation === true)
})

test('requires at least three successful samples', () => {
  assert.throws(() => selectStableLocation([
    { latitude: 28.22780, longitude: 112.93880, accuracy: 20 },
    { latitude: 28.22782, longitude: 112.93882, accuracy: 18 },
  ]), error => error.code === 'LOCATION_UNAVAILABLE' && error.canRetryLocation === true)
})
