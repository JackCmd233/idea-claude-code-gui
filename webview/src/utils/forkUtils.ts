import type {
  ClaudeContentBlock,
  ClaudeMessage,
  ForkReplaySnapshot,
  ForkReplayTurn,
  ForkSessionPayload,
  SentUserContextSnapshot,
} from '../types';

type ForkBuildInput = {
  messages: ClaudeMessage[];
  targetMessageKey: string;
  targetMessageIndex: number;
  currentProvider: string;
  currentSessionId: string | null;
  currentTabTitle: string;
};

type ForkBuildResult =
  | { ok: true; value: ForkSessionPayload }
  | { ok: false; error: 'fork.unsupportedProvider' | 'fork.unsupportedNode' | 'fork.missingSeedUserMessage' | 'fork.incompleteContext' };

function getRawUuid(message: ClaudeMessage): string | null {
  if (!message.raw || typeof message.raw !== 'object') return null;
  const raw = message.raw as Record<string, unknown>;
  return typeof raw.uuid === 'string' ? raw.uuid : null;
}

function extractVisibleText(blocks: ClaudeContentBlock[]): string {
  return blocks
    .filter((block) => block.type === 'text' || block.type === 'thinking')
    .map((block) => ('text' in block && typeof block.text === 'string' ? block.text : ''))
    .filter(Boolean)
    .join('\n')
    .trim();
}

function getBlocks(message: ClaudeMessage): ClaudeContentBlock[] {
  if (!message.raw || typeof message.raw !== 'object') return [];
  const raw = message.raw as any;
  const content = Array.isArray(raw.message?.content)
    ? raw.message.content
    : Array.isArray(raw.content)
      ? raw.content
      : [];
  return content as ClaudeContentBlock[];
}

function assertCompleteUserSnapshot(message: ClaudeMessage, snapshot: SentUserContextSnapshot | undefined): snapshot is SentUserContextSnapshot {
  if (!snapshot) return false;

  const blocks = getBlocks(message);
  const attachmentBlockCount = blocks.filter((block) => block.type === 'attachment' || block.type === 'image').length;
  const snapshotAttachmentCount = snapshot.attachments.length;

  if (attachmentBlockCount > snapshotAttachmentCount) return false;
  if (blocks.some((block) => block.type === 'attachment') && snapshot.attachments.some((att) => !att.data)) return false;
  return true;
}

function buildReplayTurn(message: ClaudeMessage): ForkReplayTurn {
  const blocks = getBlocks(message);
  return {
    role: message.type === 'assistant' ? 'assistant' : 'user',
    text: extractVisibleText(blocks) || (message.content ?? ''),
    attachments: message.type === 'user'
      ? (message.sentUserContextSnapshot?.attachments ?? [])
      : [],
    fileTags: message.type === 'user'
      ? (message.sentUserContextSnapshot?.fileTags ?? [])
      : [],
  };
}

export function createSentUserContextSnapshot(input: SentUserContextSnapshot): SentUserContextSnapshot {
  return {
    messageKey: input.messageKey,
    text: input.text,
    attachments: [...input.attachments],
    fileTags: [...input.fileTags],
    timestamp: input.timestamp,
  };
}

export function buildForkPayloadFromMessage(input: ForkBuildInput): ForkBuildResult {
  if (input.currentProvider !== 'claude') {
    return { ok: false, error: 'fork.unsupportedProvider' };
  }

  const target = input.messages[input.targetMessageIndex];
  if (!target) {
    return { ok: false, error: 'fork.unsupportedNode' };
  }

  const targetUuid = getRawUuid(target);
  if (!targetUuid || targetUuid !== input.targetMessageKey) {
    return { ok: false, error: 'fork.unsupportedNode' };
  }

  const displayMessages = input.messages.slice(0, input.targetMessageIndex + 1);

  const seedMessage = target.type === 'user'
    ? target
    : [...displayMessages].reverse().find((message) => message.type === 'user');

  if (!seedMessage) {
    return { ok: false, error: 'fork.missingSeedUserMessage' };
  }

  if (!assertCompleteUserSnapshot(seedMessage, seedMessage.sentUserContextSnapshot)) {
    return { ok: false, error: 'fork.incompleteContext' };
  }

  const seedSnapshot = seedMessage.sentUserContextSnapshot;
  const seedMessageKey = getRawUuid(seedMessage) ?? seedSnapshot.messageKey;

  // For user target: no replay turns needed (fork starts from this user message)
  // For assistant target: include all messages before the target as replay turns
  const replayTurns: ForkReplayTurn[] = [];
  if (target.type === 'assistant') {
    // Include all messages except the last (which is the assistant target)
    for (let i = 0; i < displayMessages.length - 1; i++) {
      const msg = displayMessages[i];
      if (msg.type === 'user' || msg.type === 'assistant') {
        replayTurns.push(buildReplayTurn(msg));
      }
    }
  }

  const replay: ForkReplaySnapshot = {
    provider: 'claude',
    turns: replayTurns,
    seedInput: {
      text: seedSnapshot.text,
      attachments: [...seedSnapshot.attachments],
      fileTags: [...seedSnapshot.fileTags],
      sourceUserMessageKey: seedMessageKey,
    },
    forkTitle: `Fork of ${input.currentTabTitle}`,
  };

  return {
    ok: true,
    value: {
      display: {
        sourceTabId: null,
        sourceSessionId: input.currentSessionId,
        forkedFromMessageKey: input.targetMessageKey,
        forkedFromMessageType: target.type === 'assistant' ? 'assistant' : 'user',
        titleBase: input.currentTabTitle,
        messages: displayMessages,
      },
      replay,
    },
  };
}
