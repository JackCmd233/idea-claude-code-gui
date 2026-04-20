import { describe, expect, it } from 'vitest';
import type { ClaudeMessage } from '../types';
import {
  buildForkPayloadFromMessage,
  createSentUserContextSnapshot,
} from './forkUtils';

const userSnapshot = createSentUserContextSnapshot({
  messageKey: 'uuid-user-1',
  text: 'Refactor this flow',
  attachments: [
    {
      id: 'att-1',
      fileName: 'trace.log',
      mediaType: 'text/plain',
      data: 'YmFzZTY0',
    },
  ],
  fileTags: [
    {
      displayPath: 'src/main/App.tsx',
      absolutePath: 'D:/Code/idea-claude-code-gui/src/main/App.tsx',
    },
  ],
  timestamp: '2026-04-21T10:00:00.000Z',
});

describe('buildForkPayloadFromMessage', () => {
  it('builds display and replay snapshots for a user target', () => {
    const messages: ClaudeMessage[] = [
      {
        type: 'user',
        content: 'Refactor this flow',
        timestamp: '2026-04-21T10:00:00.000Z',
        raw: {
          uuid: 'uuid-user-1',
          message: {
            content: [
              { type: 'attachment', fileName: 'trace.log', mediaType: 'text/plain' },
              { type: 'text', text: 'Refactor this flow' },
            ],
          },
        } as any,
        sentUserContextSnapshot: userSnapshot,
      },
      {
        type: 'assistant',
        content: 'Start by isolating the send path.',
        timestamp: '2026-04-21T10:01:00.000Z',
        raw: {
          uuid: 'uuid-assistant-1',
          message: {
            content: [{ type: 'text', text: 'Start by isolating the send path.' }],
          },
        } as any,
      },
    ];

    const result = buildForkPayloadFromMessage({
      messages,
      targetMessageKey: 'uuid-user-1',
      targetMessageIndex: 0,
      currentProvider: 'claude',
      currentSessionId: 'source-session-1',
      currentTabTitle: 'Original Session',
    });

    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error('expected ok result');

    expect(result.value.display.titleBase).toBe('Original Session');
    expect(result.value.display.forkedFromMessageKey).toBe('uuid-user-1');
    expect(result.value.display.messages).toHaveLength(1);
    expect(result.value.replay.provider).toBe('claude');
    expect(result.value.replay.turns).toHaveLength(0);
    expect(result.value.replay.seedInput.text).toBe('Refactor this flow');
    expect(result.value.replay.seedInput.attachments).toHaveLength(1);
    expect(result.value.replay.seedInput.fileTags[0].displayPath).toBe('src/main/App.tsx');
    expect(result.value.replay.forkTitle).toBe('Fork of Original Session');
  });

  it('uses the nearest preceding user message as seed input for an assistant target', () => {
    const messages: ClaudeMessage[] = [
      {
        type: 'user',
        content: 'Investigate the timeout',
        timestamp: '2026-04-21T10:00:00.000Z',
        raw: {
          uuid: 'uuid-user-2',
          message: { content: [{ type: 'text', text: 'Investigate the timeout' }] },
        } as any,
        sentUserContextSnapshot: createSentUserContextSnapshot({
          messageKey: 'uuid-user-2',
          text: 'Investigate the timeout',
          attachments: [],
          fileTags: [],
          timestamp: '2026-04-21T10:00:00.000Z',
        }),
      },
      {
        type: 'assistant',
        content: 'The timeout comes from the daemon wait loop.',
        timestamp: '2026-04-21T10:01:00.000Z',
        raw: {
          uuid: 'uuid-assistant-2',
          message: { content: [{ type: 'text', text: 'The timeout comes from the daemon wait loop.' }] },
        } as any,
      },
    ];

    const result = buildForkPayloadFromMessage({
      messages,
      targetMessageKey: 'uuid-assistant-2',
      targetMessageIndex: 1,
      currentProvider: 'claude',
      currentSessionId: 'source-session-2',
      currentTabTitle: 'Timeout Debugging',
    });

    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error('expected ok result');

    expect(result.value.display.messages).toHaveLength(2);
    expect(result.value.replay.turns).toHaveLength(1);
    expect(result.value.replay.turns[0]).toEqual({
      role: 'user',
      text: 'Investigate the timeout',
      attachments: [],
      fileTags: [],
    });
    expect(result.value.replay.seedInput.sourceUserMessageKey).toBe('uuid-user-2');
    expect(result.value.replay.seedInput.text).toBe('Investigate the timeout');
  });

  it('blocks assistant targets that have no preceding user seed', () => {
    const messages: ClaudeMessage[] = [
      {
        type: 'assistant',
        content: 'No seed exists here',
        timestamp: '2026-04-21T10:01:00.000Z',
        raw: {
          uuid: 'uuid-assistant-3',
          message: { content: [{ type: 'text', text: 'No seed exists here' }] },
        } as any,
      },
    ];

    const result = buildForkPayloadFromMessage({
      messages,
      targetMessageKey: 'uuid-assistant-3',
      targetMessageIndex: 0,
      currentProvider: 'claude',
      currentSessionId: 'source-session-3',
      currentTabTitle: 'Bad Target',
    });

    expect(result).toEqual({
      ok: false,
      error: 'fork.missingSeedUserMessage',
    });
  });

  it('blocks replay when the user snapshot is incomplete', () => {
    const messages: ClaudeMessage[] = [
      {
        type: 'user',
        content: 'Use the missing attachment',
        timestamp: '2026-04-21T10:00:00.000Z',
        raw: {
          uuid: 'uuid-user-4',
          message: {
            content: [
              { type: 'attachment', fileName: 'broken.bin', mediaType: 'application/octet-stream' },
              { type: 'text', text: 'Use the missing attachment' },
            ],
          },
        } as any,
        sentUserContextSnapshot: {
          messageKey: 'uuid-user-4',
          text: 'Use the missing attachment',
          attachments: [],
          fileTags: [],
          timestamp: '2026-04-21T10:00:00.000Z',
        },
      },
    ];

    const result = buildForkPayloadFromMessage({
      messages,
      targetMessageKey: 'uuid-user-4',
      targetMessageIndex: 0,
      currentProvider: 'claude',
      currentSessionId: 'source-session-4',
      currentTabTitle: 'Broken Context',
    });

    expect(result).toEqual({
      ok: false,
      error: 'fork.incompleteContext',
    });
  });
});
