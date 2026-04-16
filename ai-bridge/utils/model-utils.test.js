import test from 'node:test';
import assert from 'node:assert/strict';

import { resolveModelFromSettings } from './model-utils.js';

test('explicit custom model should override legacy ANTHROPIC_MODEL global override', () => {
  const resolved = resolveModelFromSettings('MiniMax-M2.7', {
    ANTHROPIC_MODEL: 'GLM-5.1',
    ANTHROPIC_DEFAULT_SONNET_MODEL: 'MiniMax-M2.1',
  });

  assert.equal(resolved, 'MiniMax-M2.7');
});
