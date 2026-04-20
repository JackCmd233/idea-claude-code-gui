export type ClaudeRole = 'user' | 'assistant' | 'error' | string;

// Fork snapshot types for session forking feature
export interface ForkAttachmentSnapshot {
  id: string;
  fileName: string;
  mediaType: string;
  data: string;
}

export interface ForkFileTagSnapshot {
  displayPath: string;
  absolutePath: string;
}

export interface SentUserContextSnapshot {
  messageKey: string;
  text: string;
  attachments: ForkAttachmentSnapshot[];
  fileTags: ForkFileTagSnapshot[];
  timestamp: string;
}

export interface ForkDisplaySnapshot {
  sourceTabId: string | null;
  sourceSessionId: string | null;
  forkedFromMessageKey: string;
  forkedFromMessageType: 'user' | 'assistant';
  titleBase: string;
  messages: ClaudeMessage[];
}

export interface ForkReplayTurn {
  role: 'user' | 'assistant';
  text: string;
  attachments: ForkAttachmentSnapshot[];
  fileTags: ForkFileTagSnapshot[];
}

export interface ForkSeedInput {
  text: string;
  attachments: ForkAttachmentSnapshot[];
  fileTags: ForkFileTagSnapshot[];
  sourceUserMessageKey: string;
}

export interface ForkReplaySnapshot {
  provider: 'claude';
  turns: ForkReplayTurn[];
  seedInput: ForkSeedInput;
  forkTitle: string;
}

export interface ForkSessionPayload {
  display: ForkDisplaySnapshot;
  replay: ForkReplaySnapshot;
}

export interface ForkInitState {
  display: ForkDisplaySnapshot;
  replay: ForkReplaySnapshot;
  forkPending: boolean;
  initializationError: string | null;
}

export type ToolInput = Record<string, unknown>;

export type ClaudeContentBlock =
  | { type: 'text'; text?: string }
  | { type: 'thinking'; thinking?: string; text?: string }
  | { type: 'tool_use'; id?: string; name?: string; input?: ToolInput }
  | { type: 'image'; src?: string; mediaType?: string; alt?: string }
  | { type: 'attachment'; fileName?: string; mediaType?: string };

export interface ToolResultBlock {
  type: 'tool_result';
  tool_use_id?: string;
  content?: string | Array<{ type?: string; text?: string }>;
  is_error?: boolean;
  [key: string]: unknown;
}

export type ClaudeContentOrResultBlock = ClaudeContentBlock | ToolResultBlock;

export interface ClaudeRawMessage {
  content?: string | ClaudeContentOrResultBlock[];
  message?: { content?: string | ClaudeContentOrResultBlock[] };
  type?: string;
  [key: string]: unknown;
}

/** Represents a single message in the chat conversation. */
export interface ClaudeMessage {
  type: ClaudeRole;
  content?: string;
  raw?: ClaudeRawMessage | string;
  timestamp?: string;
  isStreaming?: boolean;
  isOptimistic?: boolean;
  /**
   * Snapshot of user context captured at send time for fork replay.
   * Only present on user messages. Contains the original text, attachments,
   * and file tags needed to replay the message in a forked session.
   */
  sentUserContextSnapshot?: SentUserContextSnapshot;
  /**
   * Runtime-only: numeric turn identifier for streaming assistant isolation.
   * Set by frontend during streaming to distinguish messages from different
   * conversation turns. Messages with different __turnId values should never
   * be merged. Undefined for history messages loaded from JSONL files.
   */
  __turnId?: number;
  [key: string]: unknown;
}

export interface TodoItem {
  id?: string;
  content: string;
  status: 'pending' | 'in_progress' | 'completed';
}

export interface HistorySessionSummary {
  sessionId: string;
  title: string;
  messageCount: number;
  lastTimestamp?: string;
  isFavorited?: boolean;
  favoritedAt?: number;
  provider?: string; // 'claude' or 'codex'
}

export interface HistoryData {
  success: boolean;
  error?: string;
  sessions?: HistorySessionSummary[];
  total?: number;
  favorites?: Record<string, { favoritedAt: number }>;
}

// File changes types
export type { FileChangeStatus, EditOperation, FileChangeSummary } from './fileChanges';

// Subagent types
export type { SubagentStatus, SubagentInfo } from './subagent';
